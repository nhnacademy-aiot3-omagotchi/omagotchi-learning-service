package site.omagotchi.learningservice.team.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamErrorCode;

import java.util.Locale;

/**
 * 유니크 위반을 기능 오류 코드로 변환한다.
 *
 * <p>{@code infrastructure}에 있는 이유는 여기서 다루는 것이 전부 기술 정보이기 때문이다 —
 * Hibernate 예외 계층과 DB 인덱스명. Application이 이걸 알면 서비스 코드가 Spring Data와
 * 인덱스 이름에 묶인다. 기술 실패가 하나의 오류 코드와 명확히 대응하고 호출자가
 * 재시도·복구를 판단하지 않으므로, 중간 예외 타입을 두지 않고 곧바로 변환한다.</p>
 *
 * 서비스의 select 선검사는 동시 요청을 막지 못한다 — 두 트랜잭션이 같은 시점에
 * "없음"을 확인하고 둘 다 INSERT할 수 있다. 부분 유니크 인덱스가 최종 방어선이다.
 *
 * V7 주석대로 이 유니크들은 테이블 제약이 아니라 인덱스이므로,
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
     * <p><b>아는 인덱스가 아니면 원본을 그대로 돌려준다.</b> 예전에는 ALREADY_IN_TEAM을
     * 기본값으로 썼지만, 그러면 NOT NULL·FK·CHECK 위반까지 전부 409 "이미 팀에 소속"으로
     * 나간다 — 의미를 좁게 확정할 수 있을 때만 변환하라는 규칙(04-error-handling §2)에
     * 어긋나고, 원인을 찾을 단서인 stack trace도 사라진다. 500이 나가면 로그가 남지만
     * 엉뚱한 409는 조용히 묻힌다. 새 유니크를 추가하면 여기 분기도 반드시 함께 늘린다.</p>
     *
     * @param exception 원인이 Hibernate {@code ConstraintViolationException}이어야 인덱스명을 읽을 수 있다
     * @return 던질 준비가 된 예외. 이 메서드는 예외를 던지지 않는다
     */
    public static RuntimeException translate(DataIntegrityViolationException exception) {
        String name = extractConstraintName(exception);
        if (name == null) {
            return exception;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains(UQ_TEAMS_ACTIVE_NAME)) {
            return new BusinessException(TeamErrorCode.DUPLICATE_NAME, exception);
        }
        if (normalized.contains(UQ_TEAM_MEMBERS_MEMBERSHIP)
                || normalized.contains(UQ_TEAM_MEMBERS_TEAM_MEMBERSHIP)) {
            return new BusinessException(TeamErrorCode.ALREADY_IN_TEAM, exception);
        }
        if (normalized.contains(UQ_TEAM_MEMBERS_ONE_MASTER)) {
            return new BusinessException(TeamErrorCode.MASTER_STATE_CONFLICT, exception);
        }
        return exception;
    }

    private static String extractConstraintName(DataIntegrityViolationException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ConstraintViolationException violation) {
            return violation.getConstraintName();
        }
        return null;
    }
}
