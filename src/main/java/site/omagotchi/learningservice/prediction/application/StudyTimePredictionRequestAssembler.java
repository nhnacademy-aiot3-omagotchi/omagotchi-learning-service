package site.omagotchi.learningservice.prediction.application;

import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.DailyAttendance;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.DailyQuestSummary;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.DailyStudySeconds;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class StudyTimePredictionRequestAssembler {

    private static final int STUDY_RECENT_DAYS = 30;
    private static final int RATE_RECENT_DAYS = 7;
    private static final double SECONDS_PER_HOUR = 3_600.0;

    /**
     * 예측 모델을 만들 때 정한 공부시간 입력 피처의 상한이다.
     * 타이머나 StudyRecord의 도메인 상한이 아니라 prediction-service로 보낼 값의 경계다.
     */
    private static final double MAX_PREDICTION_STUDY_HOURS = 11.5;

    // 지각 피처는 지각과 조퇴가 각각 또는 함께 발생한 최종 상태를 모두 포함한다.
    private static final Set<AttendanceStatus> LATE_STATUSES = Set.of(
            AttendanceStatus.LATE,
            AttendanceStatus.LEFT_EARLY,
            AttendanceStatus.LATE_LEFT_EARLY
    );

    /**
     * DB나 현재 시각을 다시 조회하지 않고 전달받은 스냅샷만으로 32개 요청 피처를 조립한다.
     * 스냅샷의 featureDate는 targetDate의 전날이다.
     * 같은 스냅샷은 항상 같은 요청을 만들어야 하므로 이 Module은 순수 계산만 담당한다.
     */
    public StudyTimePredictionRequest assemble(PredictionFeatureSnapshot snapshot) {
        Map<LocalDate, Long> studySecondsByDate = studySecondsByDate(snapshot);
        StudyFeatures study = assembleStudyFeatures(snapshot, studySecondsByDate);
        AttendanceFeatures attendance = assembleAttendanceFeatures(
                snapshot,
                studySecondsByDate.keySet()
        );
        GamificationFeatures gamification = assembleGamificationFeatures(snapshot);
        CalendarFeatures calendar = assembleCalendarFeatures(snapshot.featureDate());

        return new StudyTimePredictionRequest(
                study.studyLag1(),
                study.studyLag2(),
                study.studyLag3(),
                study.study7dMean(),
                study.study30dMean(),
                study.studyAllMean(),
                study.study7dStd(),
                study.trend7To30(),
                study.studyDiff1d(),
                attendance.att7d(),
                attendance.att30d(),
                attendance.attAll(),
                attendance.attendDays7d(),
                attendance.noShowYesterday() ? 1 : 0,
                attendance.late7d(),
                attendance.late30d(),
                attendance.lateAll(),
                attendance.forgot7d(),
                attendance.entryLag1Min(),
                attendance.entry7dMeanMin(),
                snapshot.gamification().representativeLevel(),
                snapshot.gamification().completedQuestsTotal(),
                gamification.questStreak(),
                gamification.questRate7d(),
                calendar.tomorrowIsWeekday(),
                calendar.tomorrowDow1(),
                calendar.tomorrowDow2(),
                calendar.tomorrowDow3(),
                calendar.tomorrowDow4(),
                calendar.tomorrowDow5(),
                calendar.tomorrowDow6(),
                study.daysSinceStart()
        );
    }

    private Map<LocalDate, Long> studySecondsByDate(PredictionFeatureSnapshot snapshot) {
        Map<LocalDate, Long> studySecondsByDate = new HashMap<>();
        for (DailyStudySeconds daily : snapshot.study().recentDailyStudySeconds()) {
            // 같은 날짜에 확정 StudyRecord가 여러 개면 일 단위 공부시간으로 합산한다.
            studySecondsByDate.merge(daily.aggregationDate(), daily.studySeconds(), Long::sum);
        }
        return studySecondsByDate;
    }

    private StudyFeatures assembleStudyFeatures(
            PredictionFeatureSnapshot snapshot,
            Map<LocalDate, Long> studySecondsByDate
    ) {
        double[] recent30Hours = new double[STUDY_RECENT_DAYS];
        LocalDate recentStartDate = snapshot.featureDate().minusDays(STUDY_RECENT_DAYS - 1L);
        for (int index = 0; index < STUDY_RECENT_DAYS; index++) {
            LocalDate date = recentStartDate.plusDays(index);
            // 확정 기록이 없는 달력 날짜는 결정한 정책대로 0시간으로 채운다.
            recent30Hours[index] = studySecondsByDate.getOrDefault(date, 0L) / SECONDS_PER_HOUR;
        }

        double studyLag1 = upperClampPredictionStudyHours(
                recent30Hours[STUDY_RECENT_DAYS - 1]
        );
        double studyLag2 = upperClampPredictionStudyHours(
                recent30Hours[STUDY_RECENT_DAYS - 2]
        );
        double studyLag3 = upperClampPredictionStudyHours(
                recent30Hours[STUDY_RECENT_DAYS - 3]
        );
        double rawStudy7dMean = mean(
                recent30Hours,
                STUDY_RECENT_DAYS - RATE_RECENT_DAYS,
                STUDY_RECENT_DAYS
        );
        double rawStudy30dMean = mean(recent30Hours, 0, STUDY_RECENT_DAYS);
        // 표본이 아니라 최근 7개 달력값 전체를 모집단으로 보고 분모 7을 사용한다.
        double rawStudy7dStd = populationStandardDeviation(
                recent30Hours,
                STUDY_RECENT_DAYS - RATE_RECENT_DAYS,
                STUDY_RECENT_DAYS,
                rawStudy7dMean
        );
        // 원천 일별 값을 바꾸지 않고 계산이 끝난 공부시간 입력 피처만 모델 경계로 보정한다.
        double study7dMean = upperClampPredictionStudyHours(rawStudy7dMean);
        double study30dMean = upperClampPredictionStudyHours(rawStudy30dMean);
        double study7dStd = upperClampPredictionStudyHours(rawStudy7dStd);

        LocalDate firstStudyDate = snapshot.study().firstStudyDate();
        if (firstStudyDate == null) {
            // 최초 확정 StudyRecord가 없으면 공부·추세·경과일 피처를 모두 0으로 조립한다.
            return new StudyFeatures(
                    studyLag1,
                    studyLag2,
                    studyLag3,
                    study7dMean,
                    study30dMean,
                    0.0,
                    study7dStd,
                    study7dMean - study30dMean,
                    studyLag1 - studyLag2,
                    0L
            );
        }

        long allCalendarDays = ChronoUnit.DAYS.between(firstStudyDate, snapshot.featureDate()) + 1L;
        if (allCalendarDays <= 0L) {
            throw new IllegalArgumentException(
                    "예측 피처 스냅샷의 날짜 범위가 일관되지 않습니다. "
                            + "firstStudyDate=%s, featureDate=%s"
                            .formatted(firstStudyDate, snapshot.featureDate())
            );
        }
        // 전체 평균도 첫 기록일부터 featureDate까지 기록 없는 날을 0으로 포함한다.
        double studyAllMean = upperClampPredictionStudyHours(
                snapshot.study().totalStudySeconds()
                        / SECONDS_PER_HOUR
                        / allCalendarDays
        );
        long daysSinceStart = ChronoUnit.DAYS.between(firstStudyDate, snapshot.featureDate());

        return new StudyFeatures(
                studyLag1,
                studyLag2,
                studyLag3,
                study7dMean,
                study30dMean,
                studyAllMean,
                study7dStd,
                study7dMean - study30dMean,
                studyLag1 - studyLag2,
                daysSinceStart
        );
    }

    private AttendanceFeatures assembleAttendanceFeatures(
            PredictionFeatureSnapshot snapshot,
            Set<LocalDate> studiedDates
    ) {
        Map<LocalDate, DailyAttendance> attendanceByDate = new HashMap<>();
        for (DailyAttendance attendance : snapshot.attendance().recentAttendance()) {
            DailyAttendance previous = attendanceByDate.put(attendance.attendanceDate(), attendance);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "예측 피처 스냅샷에 같은 날짜의 출결 기록이 중복되었습니다. "
                                + "attendanceDate=%s".formatted(attendance.attendanceDate())
                );
            }
        }

        LocalDate sevenDayStart = laterOf(
                snapshot.membershipStartDate(),
                snapshot.featureDate().minusDays(RATE_RECENT_DAYS - 1L)
        );
        LocalDate thirtyDayStart = laterOf(
                snapshot.membershipStartDate(),
                snapshot.featureDate().minusDays(STUDY_RECENT_DAYS - 1L)
        );
        AttendanceWindow sevenDays = attendanceWindow(
                studiedDates,
                attendanceByDate,
                sevenDayStart,
                snapshot.featureDate()
        );
        AttendanceWindow thirtyDays = attendanceWindow(
                studiedDates,
                attendanceByDate,
                thirtyDayStart,
                snapshot.featureDate()
        );

        long allWeekdays = countWeekdays(snapshot.membershipStartDate(), snapshot.featureDate());
        double attAll = ratio(snapshot.study().studiedWeekdaysAll(), allWeekdays);
        double lateAll = ratio(
                snapshot.attendance().lateStudiedDaysAll(),
                snapshot.study().studiedWeekdaysAll()
        );

        ZoneId attendanceZone = ZoneId.of(snapshot.attendanceTimezone());
        Double entryLag1Min = studiedDates.contains(snapshot.featureDate())
                ? entryMinutes(attendanceByDate.get(snapshot.featureDate()), attendanceZone)
                : null;
        Double entry7dMeanMin = averageEntryMinutes(
                studiedDates,
                attendanceByDate,
                sevenDayStart,
                snapshot.featureDate(),
                attendanceZone
        );
        boolean noShowYesterday = !snapshot.featureDate().isBefore(snapshot.membershipStartDate())
                && !studiedDates.contains(snapshot.featureDate());
        long attendedCalendarDays7d = countStudiedDays(
                studiedDates,
                sevenDayStart,
                snapshot.featureDate()
        );

        return new AttendanceFeatures(
                ratio(sevenDays.studiedDays(), sevenDays.weekdays()),
                ratio(thirtyDays.studiedDays(), thirtyDays.weekdays()),
                attAll,
                // attendDays7d는 등원율과 달리 주말을 제외하지 않고 최근 7개 달력일을 센다.
                attendedCalendarDays7d,
                // Prediction 필드명은 유지하되 요일과 무관하게 featureDate의 StudyRecord 유무로 판정한다.
                noShowYesterday,
                sevenDays.studiedDays() == 0L
                        ? null
                        : ratio(sevenDays.lateDays(), sevenDays.studiedDays()),
                ratio(thirtyDays.lateDays(), thirtyDays.studiedDays()),
                lateAll,
                sevenDays.missingCheckOutDays(),
                entryLag1Min,
                entry7dMeanMin
        );
    }

    private GamificationFeatures assembleGamificationFeatures(PredictionFeatureSnapshot snapshot) {
        Map<LocalDate, DailyQuestSummary> questByDate = new HashMap<>();
        for (DailyQuestSummary summary : snapshot.gamification().dailyQuestSummaries()) {
            validateDailyQuestSummary(summary);
            DailyQuestSummary previous = questByDate.put(summary.questDate(), summary);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "예측 피처 스냅샷에 같은 날짜의 퀘스트 집계가 중복되었습니다. "
                                + "questDate=%s".formatted(summary.questDate())
                );
            }
        }

        LocalDate rateStartDate = laterOf(
                snapshot.membershipStartDate(),
                snapshot.featureDate().minusDays(RATE_RECENT_DAYS - 1L)
        );
        long generatedQuests = 0L;
        long completedQuests = 0L;
        for (LocalDate date = rateStartDate;
             !date.isAfter(snapshot.featureDate());
            date = date.plusDays(1)) {
            DailyQuestSummary summary = questByDate.get(date);
            if (summary == null) {
                // 기존 퀘스트 생성 정책을 유지하므로 미생성 날짜는 분자와 분모에서 모두 제외한다.
                continue;
            }
            generatedQuests += summary.generatedCount();
            completedQuests += summary.completedCount();
        }
        // 계약: 최근 7일 완료율은 실제 완료 수 합계 / 실제 생성 수 합계이며 생성 수가 0이면 0이다.
        double questRate7d = ratio(completedQuests, generatedQuests);

        long questStreak = 0L;
        for (LocalDate date = snapshot.featureDate();
             !date.isBefore(snapshot.membershipStartDate());
            date = date.minusDays(1)) {
            DailyQuestSummary summary = questByDate.get(date);
            // 실제 생성된 퀘스트가 하나 이상이고 모두 완료돼야 스트릭이 이어진다.
            if (summary == null
                    || summary.generatedCount() == 0L
                    || summary.completedCount() != summary.generatedCount()) {
                break;
            }
            questStreak++;
        }

        return new GamificationFeatures(questStreak, questRate7d);
    }

    private CalendarFeatures assembleCalendarFeatures(LocalDate featureDate) {
        // Prediction 계약의 tomorrow는 featureDate 다음 날, 즉 targetDate다.
        int dayOfWeek = featureDate.plusDays(1L).getDayOfWeek().getValue();
        return new CalendarFeatures(
                dayOfWeek <= DayOfWeek.FRIDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.TUESDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.WEDNESDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.THURSDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.FRIDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.SATURDAY.getValue() ? 1 : 0,
                dayOfWeek == DayOfWeek.SUNDAY.getValue() ? 1 : 0
        );
    }

    private AttendanceWindow attendanceWindow(
            Set<LocalDate> studiedDates,
            Map<LocalDate, DailyAttendance> attendanceByDate,
            LocalDate startDate,
            LocalDate endDate
    ) {
        long weekdays = 0L;
        long studiedDays = 0L;
        long lateDays = 0L;
        long missingCheckOutDays = 0L;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!isWeekday(date)) {
                continue;
            }
            weekdays++;
            if (!studiedDates.contains(date)) {
                continue;
            }

            // 확정 StudyRecord가 있는 평일만 등원이며 출결 행은 부가 피처에만 사용한다.
            studiedDays++;
            DailyAttendance attendance = attendanceByDate.get(date);
            if (attendance == null) {
                continue;
            }
            if (LATE_STATUSES.contains(attendance.finalStatus())) {
                lateDays++;
            }
            if (attendance.finalStatus() == AttendanceStatus.MISSING_CHECK_OUT
                    || attendance.finalStatus() == AttendanceStatus.PENDING) {
                // PENDING은 예측을 막지 않고 아직 퇴실이 확정되지 않은 미퇴실 신호로 사용한다.
                missingCheckOutDays++;
            }
        }
        return new AttendanceWindow(
                weekdays,
                studiedDays,
                lateDays,
                missingCheckOutDays
        );
    }

    private Double averageEntryMinutes(
            Set<LocalDate> studiedDates,
            Map<LocalDate, DailyAttendance> attendanceByDate,
            LocalDate startDate,
            LocalDate endDate,
            ZoneId zoneId
    ) {
        double sum = 0.0;
        long count = 0L;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!isWeekday(date) || !studiedDates.contains(date)) {
                continue;
            }
            Double minutes = entryMinutes(attendanceByDate.get(date), zoneId);
            if (minutes != null) {
                sum += minutes;
                count++;
            }
        }
        return count == 0L ? null : sum / count;
    }

    private Double entryMinutes(DailyAttendance attendance, ZoneId zoneId) {
        if (attendance == null || attendance.checkedInAt() == null) {
            // 등원 여부는 StudyRecord가 결정하며 입실 메타데이터가 없으면 null로 보존한다.
            return null;
        }
        return attendance.checkedInAt()
                .atZone(zoneId)
                .toLocalTime()
                .toSecondOfDay() / 60.0;
    }

    private void validateDailyQuestSummary(DailyQuestSummary summary) {
        if (summary.generatedCount() <= 0L
                || summary.completedCount() < 0L
                || summary.completedCount() > summary.generatedCount()) {
            throw new IllegalArgumentException(
                    "예측 피처 스냅샷의 일일 퀘스트 집계가 일관되지 않습니다. "
                            + "questDate=%s, generatedCount=%d, completedCount=%d"
                            .formatted(
                                    summary.questDate(),
                                    summary.generatedCount(),
                                    summary.completedCount()
                            )
            );
        }
    }

    private long countWeekdays(LocalDate startDate, LocalDate endDate) {
        long weekdays = 0L;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWeekday(date)) {
                weekdays++;
            }
        }
        return weekdays;
    }

    private long countStudiedDays(
            Set<LocalDate> studiedDates,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return studiedDates.stream()
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                .count();
    }

    private boolean isWeekday(LocalDate date) {
        // 출결 피처의 날짜 분모는 토·일을 제외한 월요일~금요일만 사용한다.
        return date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
    }

    private LocalDate laterOf(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private double mean(double[] values, int fromInclusive, int toExclusive) {
        double sum = 0.0;
        for (int index = fromInclusive; index < toExclusive; index++) {
            sum += values[index];
        }
        return sum / (toExclusive - fromInclusive);
    }

    private double populationStandardDeviation(
            double[] values,
            int fromInclusive,
            int toExclusive,
            double mean
    ) {
        double squaredDifferenceSum = 0.0;
        for (int index = fromInclusive; index < toExclusive; index++) {
            double difference = values[index] - mean;
            squaredDifferenceSum += difference * difference;
        }
        return Math.sqrt(squaredDifferenceSum / (toExclusive - fromInclusive));
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0L ? 0.0 : (double) numerator / denominator;
    }

    private double upperClampPredictionStudyHours(double hours) {
        // 음수는 내부 데이터 오류로 검증되게 보존하고, 모델 입력 상한 초과만 잘라낸다.
        return Math.min(hours, MAX_PREDICTION_STUDY_HOURS);
    }

    private record StudyFeatures(
            double studyLag1,
            double studyLag2,
            double studyLag3,
            double study7dMean,
            double study30dMean,
            double studyAllMean,
            double study7dStd,
            double trend7To30,
            double studyDiff1d,
            long daysSinceStart
    ) {
    }

    private record AttendanceWindow(
            long weekdays,
            long studiedDays,
            long lateDays,
            long missingCheckOutDays
    ) {
    }

    private record AttendanceFeatures(
            double att7d,
            double att30d,
            double attAll,
            double attendDays7d,
            boolean noShowYesterday,
            Double late7d,
            double late30d,
            double lateAll,
            double forgot7d,
            Double entryLag1Min,
            Double entry7dMeanMin
    ) {
    }

    private record GamificationFeatures(
            long questStreak,
            double questRate7d
    ) {
    }

    private record CalendarFeatures(
            int tomorrowIsWeekday,
            int tomorrowDow1,
            int tomorrowDow2,
            int tomorrowDow3,
            int tomorrowDow4,
            int tomorrowDow5,
            int tomorrowDow6
    ) {
    }
}
