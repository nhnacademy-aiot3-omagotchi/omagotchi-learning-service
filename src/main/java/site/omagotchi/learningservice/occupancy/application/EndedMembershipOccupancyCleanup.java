package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 소속이 끝난 사람의 점유·참여를 정리한다 (MR-26, CE-07 점유 부분).
 *
 * <p>계정 삭제(GR-16)와 수동 제명·중도 이탈(CE-07)이 모두 여기로 수렴한다. 세 사건 모두
 * "이 멤버십은 더 이상 유효하지 않다"는 같은 사실이라 처리도 같다
 * ({@code CohortMembershipEndedEvent} 참고).</p>
 *
 * <p><b>정리하지 않으면 방과 사람이 함께 묶인다.</b> 점유는 ACTIVE로 남아 목록에
 * "사용 중"으로 뜨고 아무도 쓸 수 없으며, 열린 참여 행은
 * {@code uq_occupancy_participants_one_active}가 계정 기준이라 그 사람이 다시는 어떤
 * 회의에도 들어가지 못하게 만든다. 강제 종료(MR-21)로도 회수되지 않는다 — 점유자 기수를
 * 되찾을 수 없어 권한 판정이 성립하지 않기 때문이다.</p>
 *
 * <p><b>반납과 같은 {@code RELEASED}이고 공실 알림을 발송한다</b> (명세 02 §3). 강제
 * 종료가 공간 회수라 알리지 않는 것과 반대다 — 이쪽은 사람이 빠져서 방이 비는 것이므로
 * 대기자에게 알리는 것이 맞다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndedMembershipOccupancyCleanup {

    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final OccupancyEventPublisher eventPublisher;

    /**
     * 끝난 소속의 점유와 참여를 정리한다.
     *
     * <p><b>멤버십의 활성 여부를 묻지 않는다</b> (명세 06 §2 2항). 이 Method가 도는 시점에는
     * 이미 {@code ENDED}이므로, 활성 소속으로 좁혀 조회하면 대상을 하나도 찾지 못하고
     * <b>활성 점유가 영구히 잔존한다.</b></p>
     *
     * <p>같은 이벤트가 두 번 도착해도 안전하다. 점유는 활성 조회가 빈 결과가 되고, 참여는
     * {@code left_at IS NULL} 조건부 UPDATE라 이미 닫힌 행을 덮어쓰지 않는다.</p>
     *
     * @param membershipId 끝난 소속. 점유자 판정의 키다
     * @param userId       그 계정. 참여는 계정 기준으로 배타되므로 이 값이 필요하다
     * @param endedAt      종료 시각. 수신이 늦어도 "언제 끝났는지"는 이 값이 정본이다
     * @return 점유를 종료시켰으면 {@code true}
     */
    @Transactional
    public boolean cleanUp(Long membershipId, UUID userId, OffsetDateTime endedAt) {

        boolean released = releaseOccupancy(membershipId, endedAt);

        // 남의 점유에 참여자로 들어가 있던 경우다 (명세 06 §2 7항). 점유자 본인의 행은
        // 위에서 이미 닫혔고, 계정당 열린 참여가 하나뿐이라 둘이 겹치지 않는다.
        int closed = participantRepository.closeActiveByUserId(userId, endedAt);
        if (closed > 0) {
            log.info("소속 종료로 참여를 마감했습니다. membershipId={}, 마감={}건", membershipId, closed);
        }

        return released;
    }

    /**
     * 이 멤버십이 점유자인 활성 점유를 반납 처리한다.
     *
     * <p>값으로 읽고 잠근 뒤 다시 확인하는 순서다. 엔티티로 먼저 읽으면 1차 캐시가
     * {@code lockById}의 재확인을 가려, 그 사이 만료·반납된 점유를 다시 종료시킨다.</p>
     */
    private boolean releaseOccupancy(Long membershipId, OffsetDateTime endedAt) {

        RoomOccupancyRepository.ActiveOccupancy summary = occupancyRepository
                .findActiveSummaryByOccupierMembershipId(membershipId)
                .orElse(null);
        if (summary == null) {
            return false;
        }

        RoomOccupancy occupancy = occupancyRepository.lockById(summary.id()).orElse(null);
        if (occupancy == null || !occupancy.release(endedAt)) {
            // 잠금을 얻고 보니 스케줄러나 반납이 먼저 끝냈다. 종료 사유를 덮어쓰지 않는다.
            return false;
        }

        // 점유가 끝나면 그 안의 참여도 끝난다 (MR-32). 열어 두면 참여자 전원이 계정 기준
        // 유니크에 묶여 다른 회의에 들어가지 못한다.
        participantRepository.closeAllActiveByOccupancyId(occupancy.getId(), endedAt);

        // 반납과 같은 규약으로 알린다 — 사람이 빠져 방이 비었으므로 대기자에게 알린다.
        // 커밋 후 비동기로 발송된다 (ADR space-team/0006).
        eventPublisher.publishRoomVacated(
                new RoomVacatedEvent(occupancy.getSpaceId(), occupancy.getId(), endedAt));

        log.info("소속 종료로 점유를 반납 처리했습니다. membershipId={}, occupancyId={}",
                membershipId, occupancy.getId());
        return true;
    }
}
