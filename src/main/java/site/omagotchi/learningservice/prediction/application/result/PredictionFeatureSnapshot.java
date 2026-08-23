package site.omagotchi.learningservice.prediction.application.result;

import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PredictionFeatureSnapshot(
        LocalDate baseDate,
        LocalDate membershipStartDate,
        String attendanceTimezone,
        StudyHistory study,
        AttendanceHistory attendance,
        GamificationHistory gamification
) {

    public record StudyHistory(
            List<DailyStudySeconds> recentDailyStudySeconds,
            LocalDate firstStudyDate,
            long totalStudySeconds
    ) {
        public StudyHistory {
            recentDailyStudySeconds = List.copyOf(recentDailyStudySeconds);
        }
    }

    public record DailyStudySeconds(
            LocalDate aggregationDate,
            long studySeconds
    ) {
    }

    public record AttendanceHistory(
            List<DailyAttendance> recentAttendance,
            long attendedDaysAll,
            long lateDaysAll,
            long pendingWeekdaysAll,
            boolean noShowOnBaseDate
    ) {
        public AttendanceHistory {
            recentAttendance = List.copyOf(recentAttendance);
        }
    }

    public record DailyAttendance(
            LocalDate attendanceDate,
            AttendanceStatus finalStatus,
            Instant checkedInAt
    ) {
    }

    /**
     * 게임 피처를 계산할 수 있는 사용자 단위 원천값이다.
     *
     * <p>기존 퀘스트 생성 정책을 변경하지 않고 날짜별 실제 생성 행 수를 전달한다.
     * 생성되지 않은 날짜는 목록에 없으며 최근 완료율의 분모에도 포함하지 않는다.
     * 상태는 완료 후 CLAIMED 또는 EXPIRED로 바뀔 수 있으므로 완료 사실은 status가 아니라
     * {@code completedAt IS NOT NULL}로 집계한다.</p>
     */
    public record GamificationHistory(
            int representativeLevel,
            long completedQuestsTotal,
            List<DailyQuestSummary> dailyQuestSummaries
    ) {
        public GamificationHistory {
            dailyQuestSummaries = List.copyOf(dailyQuestSummaries);
        }
    }

    public record DailyQuestSummary(
            LocalDate questDate,
            long generatedCount,
            long completedCount
    ) {
    }
}
