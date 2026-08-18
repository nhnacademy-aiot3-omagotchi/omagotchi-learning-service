package site.omagotchi.learningservice.ranking.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("학습 랭킹 최대 순위")
class StudyRankingQueryTest {

    @ParameterizedTest
    @CsvSource({
            ", 100",
            "1, 1",
            "1000, 1000"
    })
    @DisplayName("기본값과 허용 범위 정상 처리")
    void resolvesMaxRank(Integer requested, int expected) {
        StudyRankingQuery query = new StudyRankingQuery(
                StudyRankingPeriod.DAILY,
                requested
        );

        assertEquals(expected, query.resolveMaxRank());
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "1001"})
    @DisplayName("허용 범위 초과 예외")
    void rejectsOutOfRangeMaxRank(int requested) {
        StudyRankingQuery query = new StudyRankingQuery(
                StudyRankingPeriod.DAILY,
                requested
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                query::resolveMaxRank
        );

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }
}
