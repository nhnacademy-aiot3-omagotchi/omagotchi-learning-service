package site.omagotchi.learningservice.team.application.port;

import site.omagotchi.learningservice.team.domain.Team;

import java.util.List;
import java.util.Optional;

/**
 * {@code teams} Persistence 경계.
 *
 * <p>Application이 요구하는 Method만 둔다. Spring Data의 {@code JpaRepository}를 그대로
 * 노출하면 Application이 {@code saveAndFlush}·{@code getReferenceById} 같은 기술 의미와
 * 기술 예외를 함께 떠안게 되고, 조회에 활성 필터가 붙었는지도 눈으로 확인할 수 없다.</p>
 *
 * <p>팀은 소프트 삭제이므로 "행이 있다"와 "살아 있다"가 다르다. 조회 Method에
 * {@code Active}·{@code DeletedAtIsNull}이 붙어 있는지 항상 확인해야 하며,
 * 해체된 팀이 목록이나 상세로 새어 나가면 안 된다.
 * 예외는 {@link #findByIdForUpdate(Long)} 하나뿐이고 그건 의도다.</p>
 */
public interface TeamRepository {

    /**
     * 팀을 저장하고 즉시 flush한다.
     *
     * <p>flush를 지연하지 않는 것이 계약의 일부다. 커밋 시점까지 밀면 유니크 위반이
     * 트랜잭션 경계 밖에서 터져 {@code ErrorCode}로 변환되지 못하고 500이 된다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         {@code uq_teams_active_name} 위반 시 {@code TEAM_DUPLICATE_NAME}(409).
     *         구현이 기술 예외를 이 계약으로 변환하므로 호출부는 기술 예외를 잡지 않는다
     */
    Team save(Team team);

    /**
     * 단건 조회. 해체된 팀은 없는 것으로 취급한다.
     *
     * <p>락이 필요 없는 조회 경로에서만 쓴다. 뒤에서 {@link #findByIdForUpdate(Long)}를
     * 부를 계획이라면 대신 {@link #findActiveCohortId(Long)}를 써야 한다.</p>
     */
    Optional<Team> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 살아 있는 팀의 기수만 스칼라로 읽는다.
     *
     * <p>엔티티가 아니라 값 하나만 뽑는 것이 이 Method의 존재 이유다. Team을 엔티티로 먼저 읽으면
     * 1차 캐시에 올라가고, 뒤이은 {@link #findByIdForUpdate(Long)}가 {@code SELECT ... FOR UPDATE}를
     * 실제로 실행해도 Hibernate는 캐시의 인스턴스를 그대로 돌려준다(재조회 결과로 필드를 덮어쓰지
     * 않는다). 그러면 락 획득 후 {@code deleted_at} 재확인이 락 이전 스냅샷을 보게 되어
     * 해체 레이스 방어가 무력화된다.</p>
     */
    Optional<Long> findActiveCohortId(Long id);

    /**
     * 팀 행 배타 락. 정원 카운트(GR-17)와 해체 레이스 방어의 유일한 수단이다.
     *
     * <p>"최대 N행"은 유니크 인덱스로 표현할 수 없으므로 카운트는 반드시 이 락 안에서 한다.</p>
     *
     * <p>해체 여부를 조건에 넣지 않고 해체된 팀도 돌려주는 것이 의도다 — 락을 잡은 뒤
     * 호출부가 {@code isDisbanded()}를 재확인해야 "해체 커밋 직후 도착한 요청"을
     * 404로 잡아낼 수 있다.</p>
     */
    Optional<Team> findByIdForUpdate(Long id);

    /**
     * 기수 내 활성 팀 이름 중복 확인 (GR-21).
     *
     * <p>{@code uq_teams_active_name}이 {@code LOWER(BTRIM(name))} 기준이므로 구현도 같은 기준을 써야
     * 인덱스를 탄다. 넘기는 이름은 {@code Team.normalizeName()}으로 정규화한 값이어야 한다.</p>
     *
     * <p>이 검사는 동시 요청을 막지 못한다. 두 트랜잭션이 같은 시점에 "없음"을 보고 둘 다
     * INSERT할 수 있으므로 최종 방어선은 {@link #save(Team)}가 변환하는 인덱스 위반이다.</p>
     */
    boolean existsActiveByCohortIdAndName(Long cohortId, String name);

    /**
     * 내 팀 목록 조회의 마지막 단계 (GR-06). 해체된 팀을 걸러낸다.
     *
     * <p>해체 시 팀원 행을 물리 삭제하므로 보통은 남지 않지만, 기수 종료 정리(CE-01)나
     * 회원 삭제 훅처럼 팀만 소프트 삭제되는 경로가 있어 이 필터가 필요하다.</p>
     *
     * <p>빈 리스트를 넘기면 {@code IN ()}이 되어 문법 오류가 날 수 있으므로
     * 호출부가 비어 있는지 먼저 확인한다.</p>
     */
    List<Team> findByIdInAndDeletedAtIsNull(List<Long> ids);

    /**
     * 이 기수의 활성 팀 식별자 전부 (CE-01).
     *
     * <p>식별자만 돌려주는 이유는 1차 캐시다. 여기서 엔티티로 읽으면 뒤이은
     * {@link #findByIdForUpdate}가 {@code FOR UPDATE}를 걸어도 잠금 이전 스냅샷을 본다 —
     * 해체 커밋 직후 도착한 정리가 이미 해체된 팀을 다시 처리하게 된다.</p>
     */
    List<Long> findActiveIdsByCohortId(Long cohortId);
}
