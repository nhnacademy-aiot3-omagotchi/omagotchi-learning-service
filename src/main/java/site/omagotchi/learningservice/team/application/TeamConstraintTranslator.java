package site.omagotchi.learningservice.team.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;

/**
 * 유니크 위반을 도메인 에러로 변환한다.
 *
 * 서비스의 select 선검사는 동시 요청을 막지 못한다 — 두 트랜잭션이 같은 시점에
 * "없음"을 확인하고 둘 다 INSERT할 수 있다. 부분 유니크 인덱스가 최종 방어선이다.
 *
 * V6 주석대로 이 유니크들은 테이블 제약이 아니라 인덱스이므로,
 * PostgreSQL이 23505와 함께 넘기는 이름은 "인덱스명"이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TeamConstraintTranslator {

    private static final String UQ_TEAMS_ACTIVE_NAME = "uq_teams_active_name";
    private static final String UQ_TEAM_MEMBERS_MEMBERSHIP = "uq_team_members_membership";
    private static final String UQ_TEAM_MEMBERS_TEAM_MEMBERSHIP = "uq_team_members_team_membership";
    private static final String UQ_TEAM_MEMBERS_ONE_MASTER = "uq_team_members_one_master";

    /**
     * 유니크 위반 예외를 그에 대응하는 도메인 에러로 바꾼다.
     *
     * <p>던지지 않고 <b>반환</b>한다. 호출부에서 {@code throw translate(e)}로 쓰면
     * 컴파일러가 그 지점에서 흐름이 끝난다는 것을 알기 때문에, catch 블록 뒤에
     * 도달 불가능한 return을 넣지 않아도 된다.</p>
     *
     * <p>인덱스명을 못 읽으면 ALREADY_IN_TEAM으로 떨어진다. 팀 도메인에서 가장 흔한
     * 위반이라 고른 기본값이며, 정확한 원인이 아닐 수 있다 — 새 유니크를 추가하면
     * 여기 분기도 같이 늘려야 한다.</p>
     *
     * @param exception 원인이 Hibernate {@code ConstraintViolationException}이어야 인덱스명을 읽을 수 있다
     * @return 던질 준비가 된 예외. 이 메서드는 예외를 던지지 않는다
     */
    public static BusinessException translate(DataIntegrityViolationException exception) {
        String name = extractConstraintName(exception);
        if (name == null) {
            return new BusinessException(TeamErrorCode.ALREADY_IN_TEAM);
        }

        String normalized = name.toLowerCase();
        if (normalized.contains(UQ_TEAMS_ACTIVE_NAME)) {
            return new BusinessException(TeamErrorCode.DUPLICATE_NAME);
        }
        if (normalized.contains(UQ_TEAM_MEMBERS_MEMBERSHIP)
                || normalized.contains(UQ_TEAM_MEMBERS_TEAM_MEMBERSHIP)) {
            return new BusinessException(TeamErrorCode.ALREADY_IN_TEAM);
        }
        if (normalized.contains(UQ_TEAM_MEMBERS_ONE_MASTER)) {
            return new BusinessException(TeamErrorCode.MASTER_STATE_CONFLICT);
        }
        return new BusinessException(TeamErrorCode.ALREADY_IN_TEAM);
    }

    private static String extractConstraintName(DataIntegrityViolationException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ConstraintViolationException violation) {
            return violation.getConstraintName();
        }
        return null;
    }
}
