package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;

import java.util.Locale;

/**
 * 유니크 위반을 기능 오류 코드로 변환한다.
 *
 * <p>{@code infrastructure}에 있는 이유는 여기서 다루는 것이 전부 기술 정보이기 때문이다 —
 * Hibernate 예외 계층과 DB 인덱스명. Application이 이걸 알면 서비스 코드가 Spring Data와
 * 인덱스 이름에 묶인다.</p>
 *
 * <p>V6 주석대로 이 유니크들은 테이블 제약이 아니라 부분 인덱스이므로, PostgreSQL이
 * 23505와 함께 넘기는 이름은 "인덱스명"이다. 이름은 통합 DDL이 아니라
 * {@code V7__create_space_team_index.sql}을 정본으로 삼는다 — ERD 문서에는
 * {@code uq_occupancies_*}로 적혀 있지만 실제 인덱스는 {@code uq_room_occupancies_*}다.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OccupancyConstraintTranslator {
    private static final String UQ_ONE_ACTIVE_PER_SPACE = "uq_room_occupancies_one_active_per_space";
    private static final String UQ_ONE_ACTIVE_PER_USER = "uq_room_occupancies_one_active_per_user";
    private static final String UQ_PARTICIPANTS_ONE_ACTIVE = "uq_occupancy_participants_one_active";
    private static final String UQ_PARTICIPANTS_PAIR = "uq_occupancy_participants_pair";

    /**
     * 유니크 위반 예외를 대응하는 도메인 에러로 바꾼다.
     *
     * <p>던지지 않고 <b>반환</b>한다. 호출부에서 {@code throw translate(e)}로 쓰면
     * 컴파일러가 그 지점에서 흐름이 끝난다는 것을 안다.</p>
     *
     * <p><b>인덱스명을 못 읽으면 원본을 그대로 돌려준다.</b> {@code TeamConstraintTranslator}는
     * 기본값으로 가장 흔한 코드를 쓰지만, 점유는 세 유니크의 의미가 완전히 달라
     * ("방이 찼다" / "내가 이미 잡았다" / "내가 다른 회의에 있다") 오진 비용이 크다.
     * 500이 나가면 stack trace가 남아 원인을 찾을 수 있지만, 엉뚱한 409는 조용히 묻힌다.
     * 새 유니크를 추가하면 여기 분기도 반드시 함께 늘린다.</p>
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
        if (normalized.contains(UQ_ONE_ACTIVE_PER_SPACE)) {
            return new BusinessException(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED, exception);
        }
        if (normalized.contains(UQ_ONE_ACTIVE_PER_USER)) {
            return new BusinessException(OccupancyErrorCode.ALREADY_OCCUPYING, exception);
        }
        if (normalized.contains(UQ_PARTICIPANTS_ONE_ACTIVE)
                || normalized.contains(UQ_PARTICIPANTS_PAIR)) {
            return new BusinessException(OccupancyErrorCode.ALREADY_PARTICIPATING, exception);
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
