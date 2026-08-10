package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code occupancy_participants} Persistence 경계.
 *
 * <p>구간 모델이므로 이탈은 삭제가 아니라 {@code left_at} 기록이다 (MR-32).
 * 삭제 메서드를 추가하면 참여 이력이 사라지고, 재합류 시 행 재사용 규약(결정 #30)도
 * 깨진다.</p>
 *
 * <p>참여자 추가·이탈·제외(#6)에 필요한 조회는 아직 여기 없다. 쓰지 않는 메서드를
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
     * 이 점유에서 이 사람의 행을 찾는다. 이탈 여부와 무관하게 조회한다.
     *
     * <p>활성 필터를 붙이지 않는 것이 요점이다. 재합류는 새 행이 아니라 기존 행의
     * {@code left_at}을 되돌리는 것이므로(결정 #30), 이미 이탈한 행도 찾아야 한다.
     * 활성만 조회하면 재합류가 INSERT로 흘러
     * {@code uq_occupancy_participants_pair} 위반이 된다.</p>
     */
    Optional<OccupancyParticipant> findByOccupancyIdAndUserId(Long occupancyId, UUID userId);


    /**
     * 열린 참여자 전원의 {@code left_at}을 종료 시각으로 일괄 마감한다 (MR-32).
     *
     * <p>반납·강제 종료·만료가 공유하는 마무리다. 점유가 끝나면 그 안의 참여도 끝나야
     * 하는데, 남겨두면 {@code uq_occupancy_participants_one_active}가 계정 기준이라
     * 그 사람들이 영구히 다른 회의에 참여할 수 없게 된다.</p>
     *
     * <p>점유자 본인의 참여 행도 함께 닫힌다 — 점유 시작이 점유자를 참여자로 등록했으므로
     * (MR-27) 별도 처리가 필요 없다.</p>
     *
     * <p>행을 삭제하지 않고 시각만 찍는 것이 구간 모델의 요점이다. 지우면 참여 이력이
     * 사라진다.</p>
     *
     * @return 마감된 행 수
     */
    int closeAllActiveByOccupancyId(Long occupancyId, OffsetDateTime endedAt);
}
