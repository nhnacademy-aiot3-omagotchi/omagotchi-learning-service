package site.omagotchi.learningservice.prediction.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("공부시간 예측 요청 조립")
class StudyTimePredictionRequestAssemblerTest {

    private static final double DELTA = 0.000_000_1;
    private static final LocalDate FEATURE_DATE = LocalDate.parse("2000-01-10");

    private final StudyTimePredictionRequestAssembler assembler =
            new StudyTimePredictionRequestAssembler();

    @Test
    @DisplayName("확정 정책에 따라 학습 출결 게임 달력 피처 조립 정상 처리")
    void assemblesAllFeaturesByConfirmedPolicies() {
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                LocalDate.parse("2000-01-03"),
                "Asia/Seoul",
                new StudyHistory(
                        List.of(
                                new DailyStudySeconds(LocalDate.parse("2000-01-08"), 3_600L),
                                new DailyStudySeconds(LocalDate.parse("2000-01-09"), 7_200L),
                                new DailyStudySeconds(FEATURE_DATE, 10_800L)
                        ),
                        LocalDate.parse("2000-01-08"),
                        21_600L,
                        1L
                ),
                new AttendanceHistory(
                        List.of(
                                attendance("2000-01-04", AttendanceStatus.LEFT_EARLY,
                                        "2000-01-04T00:00:00Z"),
                                attendance("2000-01-05", AttendanceStatus.LATE, "2000-01-05T00:30:00Z"),
                                attendance("2000-01-06", AttendanceStatus.ABSENT, null),
                                attendance("2000-01-07", AttendanceStatus.MISSING_CHECK_OUT,
                                        "2000-01-07T00:15:00Z"),
                                attendance("2000-01-08", AttendanceStatus.PRESENT,
                                        "2000-01-08T00:00:00Z"),
                                attendance("2000-01-10", AttendanceStatus.ABSENT, null)
                        ),
                        0L
                ),
                new GamificationHistory(
                        8,
                        142L,
                        List.of(
                                quest("2000-01-04", 5),
                                quest("2000-01-05", 4, 4),
                                quest("2000-01-06", 5),
                                quest("2000-01-07", 0),
                                quest("2000-01-08", 5),
                                quest("2000-01-09", 5),
                                quest("2000-01-10", 5)
                        )
                )
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        double expectedSevenDayMean = 6.0 / 7.0;
        double expectedPopulationStd = Math.sqrt(434.0 / 343.0);
        assertAll(
                () -> assertEquals(3.0, request.studyLag1(), DELTA),
                () -> assertEquals(2.0, request.studyLag2(), DELTA),
                () -> assertEquals(1.0, request.studyLag3(), DELTA),
                () -> assertEquals(expectedSevenDayMean, request.study7dMean(), DELTA),
                () -> assertEquals(0.2, request.study30dMean(), DELTA),
                () -> assertEquals(2.0, request.studyAllMean(), DELTA),
                () -> assertEquals(expectedPopulationStd, request.study7dStd(), DELTA),
                () -> assertEquals(expectedSevenDayMean - 0.2, request.trend7To30(), DELTA),
                () -> assertEquals(1.0, request.studyDiff1d(), DELTA),
                () -> assertEquals(0.2, request.att7d(), DELTA),
                () -> assertEquals(1.0 / 6.0, request.att30d(), DELTA),
                () -> assertEquals(1.0 / 6.0, request.attAll(), DELTA),
                () -> assertEquals(3.0, request.attendDays7d(), DELTA),
                () -> assertEquals(0, request.noshowYesterday()),
                () -> assertEquals(0.0, request.late7d(), DELTA),
                () -> assertEquals(0.0, request.late30d(), DELTA),
                () -> assertEquals(0.0, request.lateAll(), DELTA),
                () -> assertEquals(0.0, request.forgot7d(), DELTA),
                () -> assertNull(request.entryLag1Min()),
                () -> assertNull(request.entry7dMeanMin()),
                () -> assertEquals(8, request.level()),
                () -> assertEquals(142L, request.questsTotal()),
                () -> assertEquals(3L, request.questStreak()),
                () -> assertEquals(29.0 / 34.0, request.questRate7d(), DELTA),
                () -> assertEquals(1, request.tomorrowIsWeekday()),
                () -> assertEquals(1, request.tomorrowDow1()),
                () -> assertEquals(0, request.tomorrowDow2()),
                () -> assertEquals(0, request.tomorrowDow3()),
                () -> assertEquals(0, request.tomorrowDow4()),
                () -> assertEquals(0, request.tomorrowDow5()),
                () -> assertEquals(0, request.tomorrowDow6()),
                () -> assertEquals(2L, request.daysSinceStart())
        );
    }

    @Test
    @DisplayName("최초 학습과 출결 및 퀘스트 기록이 없으면 콜드스타트 값 조립")
    void assemblesColdStartValues() {
        LocalDate saturday = LocalDate.parse("2000-01-01");
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                saturday,
                saturday,
                "Asia/Seoul",
                new StudyHistory(List.of(), null, 0L, 0L),
                new AttendanceHistory(List.of(), 0L),
                new GamificationHistory(1, 0L, List.of())
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(0.0, request.studyLag1(), DELTA),
                () -> assertEquals(0.0, request.studyAllMean(), DELTA),
                () -> assertEquals(0.0, request.attAll(), DELTA),
                () -> assertEquals(1, request.noshowYesterday()),
                () -> assertNull(request.late7d()),
                () -> assertNull(request.entryLag1Min()),
                () -> assertNull(request.entry7dMeanMin()),
                () -> assertEquals(0L, request.questStreak()),
                () -> assertEquals(0.0, request.questRate7d(), DELTA),
                () -> assertEquals(0, request.tomorrowIsWeekday()),
                () -> assertEquals(1, request.tomorrowDow6()),
                () -> assertEquals(0L, request.daysSinceStart())
        );
    }

    @Test
    @DisplayName("출결 상태와 무관하게 확정 StudyRecord로 등원 판정")
    void usesConfirmedStudyRecordsAsAttendanceSource() {
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                LocalDate.parse("2000-01-03"),
                "Asia/Seoul",
                new StudyHistory(
                        List.of(
                                new DailyStudySeconds(LocalDate.parse("2000-01-04"), 3_600L),
                                new DailyStudySeconds(LocalDate.parse("2000-01-06"), 3_600L),
                                new DailyStudySeconds(FEATURE_DATE, 3_600L)
                        ),
                        LocalDate.parse("2000-01-04"),
                        10_800L,
                        3L
                ),
                new AttendanceHistory(
                        List.of(
                                attendance("2000-01-04", AttendanceStatus.ABSENT, null),
                                attendance("2000-01-05", AttendanceStatus.PRESENT,
                                        "2000-01-05T00:00:00Z"),
                                attendance("2000-01-06", AttendanceStatus.LATE,
                                        "2000-01-06T00:30:00Z"),
                                attendance("2000-01-10", AttendanceStatus.MISSING_CHECK_OUT,
                                        "2000-01-10T00:15:00Z")
                        ),
                        1L
                ),
                new GamificationHistory(1, 0L, List.of())
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(0.6, request.att7d(), DELTA),
                () -> assertEquals(0.5, request.att30d(), DELTA),
                () -> assertEquals(0.5, request.attAll(), DELTA),
                () -> assertEquals(3.0, request.attendDays7d(), DELTA),
                () -> assertEquals(0, request.noshowYesterday()),
                () -> assertEquals(1.0 / 3.0, request.late7d(), DELTA),
                () -> assertEquals(1.0 / 3.0, request.late30d(), DELTA),
                () -> assertEquals(1.0 / 3.0, request.lateAll(), DELTA),
                () -> assertEquals(1.0, request.forgot7d(), DELTA),
                () -> assertEquals(555.0, request.entryLag1Min(), DELTA),
                () -> assertEquals(562.5, request.entry7dMeanMin(), DELTA)
        );
    }

    @Test
    @DisplayName("PENDING 출결은 예측을 중단하지 않고 미퇴실로 집계")
    void countsPendingAttendanceAsForgottenCheckOut() {
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                FEATURE_DATE,
                "Asia/Seoul",
                new StudyHistory(
                        List.of(new DailyStudySeconds(FEATURE_DATE, 3_600L)),
                        FEATURE_DATE,
                        3_600L,
                        1L
                ),
                new AttendanceHistory(
                        List.of(attendance(FEATURE_DATE.toString(), AttendanceStatus.PENDING, null)),
                        0L
                ),
                new GamificationHistory(1, 0L, List.of())
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(1.0, request.attendDays7d(), DELTA),
                () -> assertEquals(0, request.noshowYesterday()),
                () -> assertEquals(1.0, request.forgot7d(), DELTA)
        );
    }

    @Test
    @DisplayName("출결 기록만 있고 확정 StudyRecord가 없으면 비등원과 노쇼로 판정")
    void ignoresAttendanceWithoutConfirmedStudyRecord() {
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                LocalDate.parse("2000-01-03"),
                "Asia/Seoul",
                new StudyHistory(List.of(), null, 0L, 0L),
                new AttendanceHistory(
                        List.of(attendance(
                                "2000-01-10",
                                AttendanceStatus.PRESENT,
                                "2000-01-10T00:00:00Z"
                        )),
                        0L
                ),
                new GamificationHistory(1, 0L, List.of())
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(0.0, request.att7d(), DELTA),
                () -> assertEquals(0.0, request.attAll(), DELTA),
                () -> assertEquals(0.0, request.attendDays7d(), DELTA),
                () -> assertEquals(1, request.noshowYesterday()),
                () -> assertNull(request.late7d()),
                () -> assertNull(request.entryLag1Min()),
                () -> assertNull(request.entry7dMeanMin())
        );
    }

    @Test
    @DisplayName("실제 생성된 퀘스트 수를 완료율 분모와 스트릭 기준으로 사용")
    void usesGeneratedQuestCountForRateAndStreak() {
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                FEATURE_DATE,
                "Asia/Seoul",
                new StudyHistory(List.of(), null, 0L, 0L),
                new AttendanceHistory(List.of(), 0L),
                new GamificationHistory(
                        1,
                        0L,
                        List.of(new DailyQuestSummary(FEATURE_DATE, 4L, 4L))
                )
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(1.0, request.questRate7d(), DELTA),
                () -> assertEquals(1L, request.questStreak())
        );
    }

    @Test
    @DisplayName("계산한 공부시간 입력 피처는 예측 모델 상한 11.5시간으로 보정")
    void clampsCalculatedStudyFeaturesToPredictionModelUpperBound() {
        long fullDaySeconds = 24L * 3_600L;
        PredictionFeatureSnapshot snapshot = new PredictionFeatureSnapshot(
                FEATURE_DATE,
                FEATURE_DATE.minusDays(3),
                "Asia/Seoul",
                new StudyHistory(
                        List.of(
                                new DailyStudySeconds(FEATURE_DATE.minusDays(3), fullDaySeconds),
                                new DailyStudySeconds(FEATURE_DATE.minusDays(2), fullDaySeconds),
                                new DailyStudySeconds(FEATURE_DATE.minusDays(1), fullDaySeconds),
                                new DailyStudySeconds(FEATURE_DATE, fullDaySeconds)
                        ),
                        FEATURE_DATE.minusDays(3),
                        fullDaySeconds * 4L,
                        2L
                ),
                new AttendanceHistory(List.of(), 0L),
                new GamificationHistory(1, 0L, List.of())
        );

        StudyTimePredictionRequest request = assembler.assemble(snapshot);

        assertAll(
                () -> assertEquals(11.5, request.studyLag1(), DELTA),
                () -> assertEquals(11.5, request.studyLag2(), DELTA),
                () -> assertEquals(11.5, request.studyLag3(), DELTA),
                () -> assertEquals(11.5, request.study7dMean(), DELTA),
                () -> assertEquals(3.2, request.study30dMean(), DELTA),
                () -> assertEquals(11.5, request.studyAllMean(), DELTA),
                () -> assertEquals(11.5, request.study7dStd(), DELTA),
                () -> assertEquals(8.3, request.trend7To30(), DELTA),
                () -> assertEquals(0.0, request.studyDiff1d(), DELTA)
        );
    }

    private DailyAttendance attendance(
            String date,
            AttendanceStatus status,
            String checkedInAt
    ) {
        return new DailyAttendance(
                LocalDate.parse(date),
                status,
                checkedInAt == null ? null : Instant.parse(checkedInAt)
        );
    }

    private DailyQuestSummary quest(String date, long completedCount) {
        return new DailyQuestSummary(LocalDate.parse(date), 5L, completedCount);
    }

    private DailyQuestSummary quest(String date, long generatedCount, long completedCount) {
        return new DailyQuestSummary(LocalDate.parse(date), generatedCount, completedCount);
    }
}
