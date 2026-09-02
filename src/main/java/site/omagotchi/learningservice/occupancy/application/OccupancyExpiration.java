package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;

import java.time.OffsetDateTime;

/**
 * 점유 한 건의 만료 종료를 자기 트랜잭션에서 처리한다 (스케줄러 #9).
 *
 * <p><b>별도 Component인 것이 이 Class의 존재 이유다.</b> 명세서 03이 "한 건의 실패가
 * 나머지를 막지 않도록 건별로 처리한다"고 정했는데, 같은 Class 안에서 호출하면 Spring
 * Proxy를 거치지 않아 {@code @Transactional}이 적용되지 않는다 — 결국 호출부의 트랜잭션에
 * 그대로 합류해 한 건의 실패가 전부를 되돌린다. 자기호출로는 건별 경계를 만들 수 없다
 * (10-backend-code-structure §4 "특수 Transaction: 별도 Component").</p>
 *
 * <p>{@code REQUIRES_NEW}인 것도 같은 이유다. 호출부가 트랜잭션 없이 부르는 것이 정상이지만,
 * 나중에 누군가 트랜잭션 안에서 부르면 {@code REQUIRED}는 조용히 합류해 격리가 사라진다 —
 * 이 Class의 계약은 "무슨 일이 있어도 이 한 건만 커밋·롤백된다"이므로 명시한다.</p>
 *
 * <p>순서가 상태 전이 → 참여자 마감 → 이벤트 발행이다. 전이가 실패(0행)하면 나머지를
 * 하지 않는 것이 핵심이다 — 연장됐거나 이미 종료됐거나 다른 인스턴스가 처리한 건인데,
 * 그때 참여자를 닫으면 사용 중인 회의의 참여자가 사라지고 알림까지 잘못 나간다.</p>
 */
@Component
@RequiredArgsConstructor
public class OccupancyExpiration {

    private final RoomOccupancyRepository occupancyRepository;
    private final MeetingPresenceCoordinator meetingPresenceCoordinator;
    private final OccupancyEventPublisher eventPublisher;

    /**
     * 만료된 점유 한 건을 종료한다.
     *
     * @param occupancy {@link RoomOccupancyRepository#findStale}가 돌려준 후보. 조회 시점의
     *                  스냅샷이므로 지금도 만료 상태라는 보장은 없다 — 그 판정은
     *                  {@link RoomOccupancyRepository#expire}의 조건이 한다
     * @param now       만료 판정 기준 시각. 조회에 쓴 값을 그대로 넘겨야 조회와 전이의
     *                  판정 기준이 같아진다
     * @return 이번 호출로 종료됐으면 {@code true}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(RoomOccupancyRepository.ExpiredOccupancy occupancy, OffsetDateTime now) {
        if (!occupancyRepository.expire(occupancy.occupancyId(), now)) {
            return false;
        }

        // 점유가 끝나면 그 안의 참여도 끝난다 (MR-32). 열어 두면
        // uq_occupancy_participants_one_active가 계정 기준이라 그 사람들이 영구히
        // 다른 회의에 참여할 수 없다.
        meetingPresenceCoordinator.leaveAll(
                occupancy.occupancyId(), occupancy.spaceId(), occupancy.endedAt());

        // 발행은 상태 변경 뒤다. 리스너가 AFTER_COMMIT으로 받으므로 실제 발송은 커밋 후이며,
        // vacatedAt이 now가 아닌 것은 스케줄러 주기만큼 늦게 발견했을 뿐 실제로 비워진 것은
        // expires_at 시점이기 때문이다.
        eventPublisher.publishRoomVacated(new RoomVacatedEvent(
                occupancy.spaceId(), occupancy.occupancyId(), occupancy.endedAt()));
        return true;
    }
}
