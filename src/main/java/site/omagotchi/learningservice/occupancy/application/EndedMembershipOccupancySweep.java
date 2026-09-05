package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 종료된 소속의 점유·참여 정리가 유실됐을 때 되찾는 정합성 스윕 (MR-26, ADR space-team/0013).
 *
 * <p><b>불변식은 하나다</b> — 열린 참여({@code left_at IS NULL})는 모두 ACTIVE 소속을
 * 가리킨다. 어긋난 행을 찾아 {@link EndedMembershipOccupancyCleanup#cleanUp}을 다시
 * 호출한다.</p>
 *
 * <p><b>열린 참여에서 출발하는 것이 요점이다.</b> 점유 시작이 점유자를 참여자로 자동
 * 등록하므로(MR-27), 이 한 번의 순회가 <b>점유자와 참여자를 모두</b> 포함한다 — 점유
 * 테이블을 따로 훑을 필요가 없고, 두 경로의 판정 기준이 어긋날 여지도 없다.</p>
 *
 * <p><b>이벤트를 대체하지 않고 뒤를 받친다.</b> 정상 경로는
 * {@code OccupancyMembershipEndedListener}가 커밋 직후 처리하고, 여기는 그것이 실패했을
 * 때만 늦게 개입한다. 인메모리 이벤트는 전달 상태를 보존하지 않아
 * {@code CohortMembershipService#end}가 재발행하지 못하는데(두 번째 호출은 이미 ENDED라
 * 0행), 그 틈을 메우는 것이 이 Class의 존재 이유다.</p>
 *
 * <p><b>{@link EndedMembershipOccupancyCleanup} 밖에 있는 것이 핵심이다.</b> 같은 Class
 * 안에서 {@code cleanUp}을 부르면 Spring Proxy를 거치지 않아 {@code @Transactional}이
 * 적용되지 않는다 — 건별 경계가 사라져 한 건의 실패가 배치 전체를 되돌린다.</p>
 *
 * <p><b>기수 파트 테이블을 직접 읽지 않는다.</b> 소속이 살아 있는지와 ENDED 소속의 실제
 * 종료 시각은 {@link CohortMembershipQueryService}에 묻는다. 점유가
 * {@code cohort_memberships}를 조인하면 남의 테이블을 알게 된다 (ADR 0013 §6).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipOccupancySweep {

    /**
     * 한 실행에서 처리할 최대 배치 수.
     *
     * <p>커서가 전진하지 못하는 상황에서 스케줄러 Thread를 영원히 붙들지 않기 위한
     * 상한이다. 정상 상태에서는 도달하지 않는다 — 열린 참여는 계정당 최대 1건이라
     * (부분 유니크) 현재 이용자 수로 유계이기 때문이다.</p>
     */
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final OccupancyParticipantRepository participantRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final EndedMembershipOccupancyCleanup occupancyCleanup;
    private final Clock clock;

    /**
     * 소속이 끝났는데 열려 있는 참여를 찾아 정리한다.
     *
     * <p><b>이 Method에 Transaction이 없는 것이 의도다.</b> 정리는 건별로 {@code cleanUp}이
     * 자기 Transaction에서 수행한다. 여기에 Transaction을 두면 조회 한 번을 위해 커넥션을
     * 순회가 끝날 때까지 붙들고, 건별 격리도 무의미해진다.</p>
     *
     * <p>커서로 전진하는 이유는 조회 대상이 "고아"가 아니라 <b>열린 참여 전체</b>이기
     * 때문이다. 소속 유효성은 기수 파트에 물어야 알 수 있어 SQL로 걸러낼 수 없고,
     * {@code LIMIT}만 두면 앞쪽 배치만 반복해 뒤쪽에 닿지 못한다.</p>
     *
     * <p>ENDED 소속은 {@code cohort_memberships.ended_at}에 보존된 실제 종료 시각을 쓴다.
     * 스윕 발견 시각을 쓰면 늦게 복구될수록 점유·MEETING 이력이 실제보다 길어진다.
     * PENDING·REJECTED처럼 종료 시각이 없는 비활성 상태의 비정상 참여만 발견 시각으로
     * 닫아 기존의 "열린 참여는 모두 ACTIVE" 복구 계약을 유지한다.</p>
     *
     * @param batchSize 한 배치에서 훑을 참여 행 수
     * @return 이번 실행으로 실제 점유를 종료시킨 건수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sweep(int batchSize) {
        long cursor = 0L;
        int released = 0;
        int detected = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<OccupancyParticipantRepository.OpenParticipation> openParticipations =
                    participantRepository.findOpenParticipationsAfter(cursor, batchSize);
            if (openParticipations.isEmpty()) {
                break;
            }

            // 소속 유효성은 기수 파트가 판정한다. 배치당 1회 호출이며, 조회가 실패하거나
            // 비면 정리 대상이 0건이 되어 안전 측으로 기운다.
            Set<Long> inactiveMembershipIds = cohortMembershipQueryService
                    .findInactiveMembershipIds(openParticipations.stream()
                            .map(OccupancyParticipantRepository.OpenParticipation::cohortMembershipId)
                            .toList());
            Map<Long, OffsetDateTime> endedMemberships = cohortMembershipQueryService
                    .findEndedMemberships(inactiveMembershipIds);

            for (OccupancyParticipantRepository.OpenParticipation open : openParticipations) {
                if (!inactiveMembershipIds.contains(open.cohortMembershipId())) {
                    continue;
                }
                detected++;
                OffsetDateTime cleanupAt = endedMemberships.get(open.cohortMembershipId());
                if (cleanupAt == null) {
                    cleanupAt = OffsetDateTime.now(clock);
                }
                released += cleanUp(open, cleanupAt) ? 1 : 0;
            }

            cursor = openParticipations.getLast().participantId();
            if (openParticipations.size() < batchSize) {
                break;
            }
        }

        report(detected, released);
        return released;
    }

    /**
     * 한 건을 정리한다. 실패해도 나머지를 막지 않는다.
     *
     * <p>{@code false}가 정상적으로 나올 수 있다 — 조회와 정리 사이에 이벤트 리스너가 먼저
     * 처리했거나, 대상이 점유자가 아니라 참여자여서 종료시킬 점유가 없는 경우다. 후자도
     * 참여 행은 마감되므로 정리 자체는 일어난다.</p>
     */
    private boolean cleanUp(
            OccupancyParticipantRepository.OpenParticipation open,
            OffsetDateTime cleanupAt
    ) {
        try {
            return occupancyCleanup.cleanUp(
                    open.cohortMembershipId(), open.userId(), cleanupAt);
        } catch (Exception exception) {
            // 여기서 다시 던지면 남은 대상이 이번 주기에 처리되지 않는다.
            // 행은 그대로 남아 다음 주기가 다시 집어 간다.
            log.error("고아 참여 정리에 실패했습니다. participantId={}, membershipId={}",
                    open.participantId(), open.cohortMembershipId(), exception);
            return false;
        }
    }

    /**
     * 검출 결과를 남긴다.
     *
     * <p><b>정상 기대값은 0건이다.</b> 0이 아닌 값이 지속되면 "스윕이 잘 고치고 있다"가
     * 아니라 <b>이벤트 경로가 고장 났다는 신호</b>로 읽어야 한다 — 스윕이 증상을 가려
     * 원인을 묻히게 두지 않으려고 검출 건수를 정리 건수와 따로 남긴다.</p>
     */
    private void report(int detected, int released) {
        if (detected == 0) {
            log.debug("정합성 스윕: 소속이 끝난 열린 참여가 없습니다.");
            return;
        }

        log.warn("정합성 스윕이 고아 참여를 정리했습니다. 검출={}건, 점유 종료={}건 "
                        + "— 검출이 계속 0이 아니면 소속 종료 이벤트 경로를 확인해야 합니다.",
                detected, released);
    }
}
