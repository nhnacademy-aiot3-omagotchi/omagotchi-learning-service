package site.omagotchi.learningservice.team.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.team.application.TeamErrorCode;

import java.sql.SQLException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인덱스명 → 오류 코드 변환.
 *
 * <p>이름의 정본은 통합 DDL이 아니라 {@code V7__create_space_team_index.sql}이다.
 * 마이그레이션에서 인덱스명이 바뀌면 이 테스트가 먼저 깨져야 한다 — 안 깨지면 운영에서
 * 409여야 할 응답이 조용히 500으로 바뀐다.</p>
 *
 * <p>{@code OccupancyConstraintTranslatorTest}와 같은 구조다. 두 파트가 같은 규약을
 * 쓰는지 한눈에 비교할 수 있어야 해서 의도적으로 형태를 맞췄다.</p>
 */
class TeamConstraintTranslatorTest {

    @Test
    @DisplayName("기수 내 활성 팀 이름 유니크 위반은 이름 중복 오류가 된다.")
    void test1() {
        assertTranslatedTo(TeamErrorCode.DUPLICATE_NAME, "uq_teams_active_name");
    }

    @Test
    @DisplayName("멤버십 유니크 위반은 이미 팀 소속 오류가 된다.")
    void test2() {
        assertTranslatedTo(TeamErrorCode.ALREADY_IN_TEAM, "uq_team_members_membership");
        assertTranslatedTo(TeamErrorCode.ALREADY_IN_TEAM, "uq_team_members_team_membership");
    }

    @Test
    @DisplayName("팀당 MASTER 1명 유니크 위반은 마스터 상태 충돌이 된다.")
    void test3() {
        assertTranslatedTo(TeamErrorCode.MASTER_STATE_CONFLICT, "uq_team_members_one_master");
    }

    @Test
    @DisplayName("PostgreSQL이 스키마를 붙여 넘겨도 알아본다.")
    void test4() {
        assertTranslatedTo(TeamErrorCode.DUPLICATE_NAME, "learning_service.UQ_TEAMS_ACTIVE_NAME");
    }

    /**
     * 예전에는 모르는 제약을 ALREADY_IN_TEAM으로 떨어뜨렸다. 그러면 NOT NULL·FK·CHECK
     * 위반까지 409 "이미 팀에 소속"으로 나가 원인을 찾을 단서가 사라진다 — 의미를 좁게
     * 확정할 수 있을 때만 변환하라는 규칙(04-error-handling §2)을 여기서 고정한다.
     */
    @Test
    @DisplayName("모르는 제약이면 원본 예외를 그대로 돌려준다.")
    void test5() {
        DataIntegrityViolationException original = violation("fk_team_members_team");

        assertThat(TeamConstraintTranslator.translate(original)).isSameAs(original);
    }

    @Test
    @DisplayName("인덱스명을 읽을 수 없으면 원본 예외를 그대로 돌려준다.")
    void test6() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("원인을 알 수 없는 무결성 위반");

        assertThat(TeamConstraintTranslator.translate(original)).isSameAs(original);
    }

    /** 원본을 cause로 달아야 예상 밖 실패의 스택 트레이스가 최종 경계까지 살아남는다. */
    @Test
    @DisplayName("변환한 예외는 원본을 cause로 보존한다.")
    void test7() {
        DataIntegrityViolationException original = violation("uq_teams_active_name");

        assertThat(TeamConstraintTranslator.translate(original)).hasCause(original);
    }

    /**
     * 터키어 로케일에서는 {@code String#toLowerCase()}가 "I"를 "i"가 아니라 "ı"로 바꾼다.
     * {@code Locale.ROOT}를 쓰지 않으면 {@code UQ_TEAMS_ACTIVE_NAME}이
     * {@code uq_teams_actıve_name}이 되어 매칭이 깨지고, 알 수 없는 제약으로 빠져 원본
     * 예외가 500으로 나간다. JVM 기본 로케일을 바꾸는 테스트라 다른 테스트에 새지 않게
     * 반드시 원래 값으로 되돌린다.
     */
    @Test
    @DisplayName("JVM 기본 로케일이 터키어여도 인덱스명을 정확히 인식한다.")
    void test8() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.of("tr", "TR"));
        try {
            assertTranslatedTo(TeamErrorCode.DUPLICATE_NAME, "UQ_TEAMS_ACTIVE_NAME");
        } finally {
            Locale.setDefault(original);
        }
    }

    private void assertTranslatedTo(ErrorCode expectedErrorCode, String constraintName) {
        RuntimeException translated =
                TeamConstraintTranslator.translate(violation(constraintName));

        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorCode()).isEqualTo(expectedErrorCode);
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "중복 키 위반",
                new ConstraintViolationException(
                        "duplicate key value violates unique constraint",
                        new SQLException("duplicate key", "23505"),
                        constraintName
                )
        );
    }
}
