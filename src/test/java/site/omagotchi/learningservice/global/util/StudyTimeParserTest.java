package site.omagotchi.learningservice.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("학습 시간 파싱")
class StudyTimeParserTest {

    @Nested
    @DisplayName("날짜 및 시간 파싱")
    class ParseToInstant {

        @Test
        @DisplayName("콤팩트 형식 정상 처리")
        void parsesCompactDateAndTime() {
            String date = "20000101";
            String time = "1030";

            Instant instant = StudyTimeParser.parseToInstant(date, time);

            assertEquals(Instant.parse("2000-01-01T01:30:00Z"), instant);
        }

        @Test
        @DisplayName("구분자 포함 형식 정상 처리")
        void parsesDelimitedDateAndTime() {
            String date = "2000-01-01";
            String time = "10:30";

            Instant instant = StudyTimeParser.parseToInstant(date, time);

            assertEquals(Instant.parse("2000-01-01T01:30:00Z"), instant);
        }

        @ParameterizedTest
        @CsvSource({
                "invalidDate, 1030",
                "20000101, invalidTime",
                "20001301, 1030",
                "20000101, 2500",
                "20000101, 1060"
        })
        @DisplayName("잘못된 형식 예외")
        void rejectsInvalidFormat(String date, String time) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> StudyTimeParser.parseToInstant(date, time)
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }

        @Test
        @DisplayName("날짜 누락 예외")
        void rejectsMissingDate() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> StudyTimeParser.parseToInstant(null, "1030")
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }

        @Test
        @DisplayName("시간 누락 예외")
        void rejectsMissingTime() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> StudyTimeParser.parseToInstant("20000101", null)
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("분 단위 정렬 검증")
    class IsMinuteAligned {

        @Test
        @DisplayName("참 반환")
        void returnsTrueForMinuteAlignedTime() {
            Instant instant = Instant.parse("2000-01-01T01:30:00Z");

            boolean minuteAligned = StudyTimeParser.isMinuteAligned(instant);

            assertTrue(minuteAligned);
        }

        @Test
        @DisplayName("분 단위 기록이 아닌 값 거짓 반환")
        void returnsFalseForTimeWithSeconds() {
            Instant instant = Instant.parse("2000-01-01T01:30:15Z");

            boolean minuteAligned = StudyTimeParser.isMinuteAligned(instant);

            assertFalse(minuteAligned);
        }

        @Test
        @DisplayName("나노초가 남은 시각 거짓 반환")
        void returnsFalseForTimeWithNanoseconds() {
            Instant instant = Instant.ofEpochSecond(1000L, 123456L);

            boolean minuteAligned = StudyTimeParser.isMinuteAligned(instant);

            assertFalse(minuteAligned);
        }

        @Test
        @DisplayName("null 입력 시 거짓 반환")
        void returnsFalseForMissingTime() {
            boolean minuteAligned = StudyTimeParser.isMinuteAligned(null);

            assertFalse(minuteAligned);
        }
    }
}
