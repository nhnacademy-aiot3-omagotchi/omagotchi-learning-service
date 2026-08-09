package site.omagotchi.learningservice.occupancy.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인덱스명 → 오류 코드 변환.
 *
 * <p>이름을 정본으로 삼는 것은 통합 DDL이 아니라 {@code V7__create_space_team_index.sql}이다.
 * 마이그레이션에서 인덱스명이 바뀌면 이 테스트가 먼저 깨져야 한다 — 안 깨지면 운영에서
 * 409여야 할 응답이 조용히 500으로 바뀐다.</p>
 */
class OccupancyConstraintTranslatorTest {

    @Test
    @DisplayName("회의실당 활성 점유 유니크 위반은 사용 중 오류가 된다.")
    void test1() {
        assertTranslatedTo(
                OccupancyErrorCode.ROOM_ALREADY_OCCUPIED,
                "uq_room_occupancies_one_active_per_space"
        );
    }

    @Test
    @DisplayName("계정당 활성 점유 유니크 위반은 이미 점유 중 오류가 된다.")
    void test2() {
        assertTranslatedTo(
                OccupancyErrorCode.ALREADY_OCCUPYING,
                "uq_room_occupancies_one_active_per_user"
        );
    }

    /** 점유 시작 경로에서 이 위반이 곧 "다른 회의에 참여 중인 사람의 점유 요청"이다. */
    @Test
    @DisplayName("참여자 유니크 위반은 이미 참여 중 오류가 된다.")
    void test3() {
        assertTranslatedTo(
                OccupancyErrorCode.ALREADY_PARTICIPATING,
                "uq_occupancy_participants_one_active"
        );
        assertTranslatedTo(
                OccupancyErrorCode.ALREADY_PARTICIPATING,
                "uq_occupancy_participants_pair"
        );
    }

    @Test
    @DisplayName("PostgreSQL이 스키마를 붙여 넘겨도 알아본다.")
    void test4() {
        assertTranslatedTo(
                OccupancyErrorCode.ROOM_ALREADY_OCCUPIED,
                "learning_service.UQ_ROOM_OCCUPANCIES_ONE_ACTIVE_PER_SPACE"
        );
    }

    /**
     * 기본값으로 가장 흔한 코드를 쓰지 않는 이유를 고정한다. 세 유니크의 의미가 완전히
     * 달라 오진 비용이 크다 — 500은 스택 트레이스가 남지만 엉뚱한 409는 조용히 묻힌다.
     */
    @Test
    @DisplayName("모르는 제약이면 원본 예외를 그대로 돌려준다.")
    void test5() {
        DataIntegrityViolationException original = violation("fk_room_occupancies_space");

        assertThat(OccupancyConstraintTranslator.translate(original)).isSameAs(original);
    }

    @Test
    @DisplayName("인덱스명을 읽을 수 없으면 원본 예외를 그대로 돌려준다.")
    void test6() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("원인을 알 수 없는 무결성 위반");

        assertThat(OccupancyConstraintTranslator.translate(original)).isSameAs(original);
    }

    /** 원본을 cause로 달아야 예상 밖 실패의 스택 트레이스가 최종 경계까지 살아남는다. */
    @Test
    @DisplayName("변환한 예외는 원본을 cause로 보존한다.")
    void test7() {
        DataIntegrityViolationException original =
                violation("uq_room_occupancies_one_active_per_space");

        assertThat(OccupancyConstraintTranslator.translate(original)).hasCause(original);
    }

    private void assertTranslatedTo(ErrorCode expectedErrorCode, String constraintName) {
        RuntimeException translated =
                OccupancyConstraintTranslator.translate(violation(constraintName));

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
