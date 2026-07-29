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
