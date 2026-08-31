package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code occupancy_participants} Persistence 경계.
 *
 * <p>구간 모델이므로 이탈은 삭제가 아니라 {@code left_at} 기록이다 (MR-32).
 * 삭제 메서드를 추가하면 참여 이력이 사라지고, 재합류 시 행 재사용 규약(결정 #30)도
 * 깨진다.</p>
 *
 * <p>강제 종료 정리(MR-21)에 필요한 조회는 아직 여기 없다. 쓰지 않는 메서드를
 * 미리 Port에 두지 않는다 ({@code TeamMemberRepository}와 같은 규약).</p>
 */
public interface OccupancyParticipantRepository {

    /**
     * 참여자 행을 저장하고 즉시 flush한다.
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         {@code uq_occupancy_participants_one_active} 위반 시
     *         {@code OCCUPANCY_ALREADY_PARTICIPATING}(409).
     *         점유 시작 경로에서는 이것이 "다른 회의에 참여 중인 사람의 점유 요청"을
     *         막는 유일한 검사다 (MR-30)
     */
    OccupancyParticipant save(OccupancyParticipant participant);


    /** 이 점유의 현재 참여자 수 ({@code left_at IS NULL}). 정원 검증(MR-28)에 쓴다. */
    long countActiveByOccupancyId(Long occupancyId);


    /**
     * 여러 점유의 현재 참여자를 한 번에 읽는다 ({@code left_at IS NULL}).
     * 공간 목록이 회의 참여자를 표시하는 데 쓴다.
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 사용 중인 방이 N개여도 쿼리는 1회여야 한다 —
     * 점유마다 따로 조회하면 그대로 N+1이 된다.</p>
     *
     * <p>점유자 본인도 결과에 포함된다. 점유 시작이 점유자를 참여자로 등록하므로(MR-27)
     * 별도 항목이 아니며, 받는 쪽이 점유자를 다시 더하면 중복된다.</p>
     *
     * <p>값 목록만 돌려주고 {@code cohort_membership_id}는 담지 않는다. 참여자의 기수는
     * 점유자의 기수와 같음이 추가 시점에 이미 검증됐고(MR-33), 소비처의 노출 판정은
     * 점유자 기수 하나로 끝나므로 참여자별 기수를 알 이유가 없다.</p>
     *
     * @param occupancyIds 조회할 점유. 비어 있으면 빈 결과
     * @return {@code occupancyId → 참여자 계정 목록}. 참여자가 없는 점유는 키가 없다.
     *         목록은 참여 순서({@code occupancy_participants.id})를 유지한다
     */
    Map<Long, List<UUID>> findActiveUserIdsByOccupancyIds(Collection<Long> occupancyIds);

    /** 계정별 현재 참여 중인 점유 식별자. 열린 참여 행은 계정당 최대 하나다. */
    Map<UUID, Long> findActiveOccupancyIdsByUserIds(Collection<UUID> userIds);


    /**
     * 이 점유에서 이 사람의 행을 찾는다. 이탈 여부와 무관하게 조회한다.
     *
     * <p>활성 필터를 붙이지 않는 것이 요점이다. 재합류는 새 행이 아니라 기존 행의
     * {@code left_at}을 되돌리는 것이므로(결정 #30), 이미 이탈한 행도 찾아야 한다.
     * 활성만 조회하면 재합류가 INSERT로 흘러
     * {@code uq_occupancy_participants_pair} 위반이 된다.</p>
     */
    Optional<OccupancyParticipant> findByOccupancyIdAndUserId(Long occupancyId, UUID userId);


    /**
     * 이 계정에게 열린 참여가 있는가.
     */
    boolean existsActiveParticipationByUserId(UUID userId);


    /**
     * 열린 참여자 전원의 {@code left_at}을 종료 시각으로 일괄 마감한다 (MR-32).
     *
     * @return 마감된 행 수
     */
    int closeAllActiveByOccupancyId(Long occupancyId, OffsetDateTime endedAt);

    /**
     * 이 계정의 열린 참여를 마감한다 (MR-26 참여 처리, 명세 06 §2 7항).
     *
     * @return 마감한 행 수
     */
    int closeActiveByUserId(UUID userId, OffsetDateTime endedAt);

    /**
     * 열린 참여 행을 커서로 훑는다 (MR-26 정합성 스윕).
     *
     * <p>커서로 전진하는 이유는 조회 대상이 "고아"가 아니라 <b>열린 참여 전체</b>이기
     * 때문이다. 소속이 살아 있는지는 기수 파트에 물어야 알 수 있어 SQL로 걸러낼 수 없고,
     * {@code LIMIT}만 두면 앞쪽 배치만 반복해 뒤쪽에 닿지 못한다
     * ({@code TeamMemberRepository.findMembershipRefsAfter}와 같은 규약).</p>
     *
     * @param afterId 이 값보다 큰 {@code occupancy_participants.id}부터. 첫 배치는 0
     * @param limit   한 배치 크기
     * @return {@code id} 오름차순. 비면 순회 종료다
     */
    List<OpenParticipation> findOpenParticipationsAfter(Long afterId, int limit);

    /** 스윕 후보 한 건. 소속 판정에 멤버십이, 정리에 계정이 필요하다. */
    record OpenParticipation(Long participantId, Long cohortMembershipId, UUID userId) {
    }
}
