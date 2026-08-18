package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 공간 목록과 현재 상태를 조회하는 Application Service (RM-07, RM-03, MR-36).
 *
 * <p><b>사용 상태를 저장하지 않고 매 조회 시 파생 계산한다</b> (ADR space-team/0003). 공간은
 * "사용 가능/사용 중"을 컬럼으로 갖지 않으며, 활성 점유의 존재로 판단한다.</p>
 *
 * <p><b>점유 테이블을 직접 읽지 않는다.</b> "사용 중"의 정의는 {@code occupancy}가 소유하고
 * ({@code status = ACTIVE} + {@code expires_at > now}), 여기는 그 판정 결과를 받아 화면 상태로
 * 옮기기만 한다. 조인으로 가져오면 같은 정의가 두 곳에 생겨, 만료 스케줄러나 강제 종료로
 * 조건이 바뀔 때 <b>같은 방이 목록에서는 공실인데 점유하면 409</b>인 상태가 된다.</p>
 *
 * <p>조합을 Infrastructure가 아니라 여기서 하는 이유는 이것이 <b>Use Case의 판단</b>이기
 * 때문이다 — 어떤 유형에 사용 상태를 매길지, 타 기수 점유의 개인정보를 가릴지는 화면 정책이지
 * 저장 기술이 아니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceRepository spaceRepository;
    private final OccupancyQueryService occupancyQueryService;
    private final CohortAccessService cohortAccessService;
    private final Clock clock;

    /**
     * 삭제되지 않은 전체 공간과 현재 사용 상태를 조회한다.
     *
     * <p>공간이 N개여도 점유 조회는 1회다 — 목록을 돌며 단건으로 물으면 그대로 N+1이 된다.</p>
     *
     * @param requesterUserId 요청자. {@code null}이면 기수를 특정할 수 없어 모든 점유가
     *                        "타 기수"로 취급되고 개인정보가 가려진다 (MR-36)
     */
    public List<SpaceListResult> getSpaceList(UUID requesterUserId) {
        ZonedDateTime now = ZonedDateTime.now(clock);

        List<Space> spaces = spaceRepository.findAllNotDeleted();
        if (spaces.isEmpty()) {
            return List.of();
        }

        Map<Long, SpaceOccupancyView> occupancies = occupancyQueryService.findActiveBySpaceIds(
                spaces.stream().map(Space::getId).toList(),
                now.toOffsetDateTime()
        );

        Set<Long> requesterCohortIds = requesterUserId == null
                ? Set.of()
                : Set.copyOf(cohortAccessService.findActiveCohortIds(requesterUserId));

        return spaces.stream()
                .map(space -> toListResult(
                        space,
                        occupancies.get(space.getId()),
                        requesterCohortIds,
                        now
                ))
                .toList();
    }

    /**
     * 공간 하나를 화면 상태로 옮긴다.
     *
     * <p>점유자·참여자 식별자는 <b>요청자와 같은 기수의 점유일 때만</b> 담는다 (MR-36).
     * 기수를 판정할 수 없으면(비로그인, 멤버십이 이미 정리됨) 가리는 쪽이 안전한 기본값이다.</p>
     */
    private SpaceListResult toListResult(
            Space space,
            SpaceOccupancyView occupancy,
            Set<Long> requesterCohortIds,
            ZonedDateTime now
    ) {
        SpaceUsageStatus status = determineUsageStatus(space, occupancy);
        boolean occupied = status == SpaceUsageStatus.OCCUPIED;

        ZonedDateTime expiresAt = occupied
                ? occupancy.expiresAt().atZoneSameInstant(now.getZone())
                : null;

        // 점유자의 기수를 알 수 없으면(멤버십이 이미 정리됨) 어느 기수와도 일치시키지 않는다 —
        // 판정할 수 없으면 감추는 것이 SpaceOccupancyView.of의 계약이다.
        // null 검사가 앞에 와야 하는 것도 계약의 일부다: 불변 Set의 contains(null)은
        // 빈 Set이어도 NPE라, 순서를 뒤집으면 점유 하나 때문에 목록 전체가 500이 된다.
        boolean sameCohort = occupied
                && occupancy.occupierCohortId() != null
                && requesterCohortIds.contains(occupancy.occupierCohortId());

        return new SpaceListResult(
                space.getId(),
                space.getName(),
                space.getSpaceType(),
                space.getCapacity(),
                space.getOperationalStatus(),
                space.getInactiveReason(),
                space.getCohortId(),
                status,
                expiresAt,
                remainingSeconds(expiresAt, now),
                sameCohort,
                sameCohort ? occupancy.occupierCohortId() : null,
                sameCohort ? occupancy.occupierMembershipId() : null,
                sameCohort ? occupancy.occupierUserId() : null,
                sameCohort ? occupancy.participantUserIds() : null
        );
    }

    /**
     * 파생 상태 판정 (명세 01 §3).
     *
     * <p>회의실만 선착순 점유의 대상이므로 나머지 유형은 상태 자체가 성립하지 않는다.
     * 비활성 판정이 점유 판정보다 앞서는 것이 중요하다 — 비활성 공간에는 새 점유가 들어올 수
     * 없지만 비활성화 직전에 시작된 점유는 남아 있을 수 있고, 그때 화면에는 "이용 불가"가
     * 맞다.</p>
     */
    private SpaceUsageStatus determineUsageStatus(Space space, SpaceOccupancyView occupancy) {
        if (!space.isMeetingRoom()) {
            return SpaceUsageStatus.NOT_APPLICABLE;
        }
        if (!space.isActive()) {
            return SpaceUsageStatus.UNAVAILABLE;
        }
        return occupancy == null
                ? SpaceUsageStatus.AVAILABLE
                : SpaceUsageStatus.OCCUPIED;
    }

    /** 남은 시간(초). 사용 중이 아니면 값이 없고, 만료 직후 음수가 되지 않도록 0에서 자른다. */
    private Long remainingSeconds(ZonedDateTime expiresAt, ZonedDateTime now) {
        if (expiresAt == null) {
            return null;
        }
        return Math.max(Duration.between(now.toInstant(), expiresAt.toInstant()).getSeconds(), 0L);
    }
}
