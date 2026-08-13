package site.omagotchi.learningservice.statistics.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("수강생 통계 페이지 조건")
class MemberPageQueryTest {

    @Nested
    @DisplayName("기본값과 계산")
    class DefaultsAndCalculation {

        @Test
        @DisplayName("선택 요청값 생략 시 기본값 정상 처리")
        void appliesDefaults() {
            MemberPageQuery query =
                    MemberPageQuery.of(
                            "30d",
                            null,
                            null,
                            null
                    );

            assertAll(
                    () -> assertEquals("30d", query.window().value()),
                    () -> assertEquals(0, query.page()),
                    () -> assertEquals(20, query.size()),
                    () -> assertEquals(
                            MemberPageQuery.SortField.PERIOD_STUDY_SECONDS,
                            query.sortField()
                    ),
                    () -> assertEquals(
                            MemberPageQuery.SortDirection.DESC,
                            query.sortDirection()
                    ),
                    () -> assertEquals(0L, query.offset())
            );
        }

        @Test
        @DisplayName("페이지와 정렬 정상 처리")
        void acceptsCustomPaginationAndSort() {
            MemberPageQuery query =
                    MemberPageQuery.of(
                            "60d",
                            2,
                            50,
                            "todayStudySeconds,asc"
                    );

            assertAll(
                    () -> assertEquals(2, query.page()),
                    () -> assertEquals(50, query.size()),
                    () -> assertEquals(100L, query.offset()),
                    () -> assertEquals(
                            MemberPageQuery.SortField.TODAY_STUDY_SECONDS,
                            query.sortField()
                    ),
                    () -> assertEquals(
                            MemberPageQuery.SortDirection.ASC,
                            query.sortDirection()
                    )
            );
        }
    }

    @Nested
    @DisplayName("요청값 검증")
    class ValidateRequest {

        @Test
        @DisplayName("음수 페이지 예외")
        void rejectsNegativePage() {
            assertInvalidRequest(() -> MemberPageQuery.of(
                    "30d",
                    -1,
                    20,
                    "periodStudySeconds,desc"
            ));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 101})
        @DisplayName("지원하지 않는 페이지 크기 예외")
        void rejectsUnsupportedSize(int size) {
            assertInvalidRequest(() -> MemberPageQuery.of(
                    "30d",
                    0,
                    size,
                    "periodStudySeconds,desc"
            ));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "unknown,asc",
                "periodStudySeconds",
                "periodStudySeconds,ASC",
                "periodStudySeconds,desc,extra",
                " periodStudySeconds,desc",
                "periodStudySeconds,desc "
        })
        @DisplayName("지원하지 않는 정렬 예외")
        void rejectsUnsupportedSort(String sort) {
            assertInvalidRequest(() -> MemberPageQuery.of(
                    "30d",
                    0,
                    20,
                    sort
            ));
        }

        @Test
        @DisplayName("필수값 누락 예외")
        void rejectsMissingRequiredValue() {
            WindowQuery window = new WindowQuery(30);

            assertAll(
                    () -> assertInvalidRequest(() -> new MemberPageQuery(
                            null,
                            0,
                            20,
                            MemberPageQuery.SortField.PERIOD_STUDY_SECONDS,
                            MemberPageQuery.SortDirection.DESC
                    )),
                    () -> assertInvalidRequest(() -> new MemberPageQuery(
                            window,
                            0,
                            20,
                            null,
                            MemberPageQuery.SortDirection.DESC
                    )),
                    () -> assertInvalidRequest(() -> new MemberPageQuery(
                            window,
                            0,
                            20,
                            MemberPageQuery.SortField.PERIOD_STUDY_SECONDS,
                            null
                    ))
            );
        }
    }

    private static void assertInvalidRequest(Runnable request) {
        BusinessException exception = assertThrows(BusinessException.class, request::run);

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}
