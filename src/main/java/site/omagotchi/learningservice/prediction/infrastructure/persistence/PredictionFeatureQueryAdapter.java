package site.omagotchi.learningservice.prediction.infrastructure.persistence;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.QAttendanceRecord;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.QCohort;
import site.omagotchi.learningservice.cohort.domain.QCohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.QCohortMembership;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.QUserCharacter;
import site.omagotchi.learningservice.gamification.domain.QUserDailyQuest;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.prediction.application.PredictionErrorCode;
import site.omagotchi.learningservice.prediction.application.port.PredictionFeatureSnapshotReader;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.*;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionFeatureQueryAdapter implements PredictionFeatureSnapshotReader {

    private static final int RECENT_DAYS = 30;
    private static final Set<AttendanceStatus> ATTENDED_STATUSES = Set.of(
            AttendanceStatus.PRESENT,
            AttendanceStatus.LATE,
            AttendanceStatus.LEFT_EARLY,
            AttendanceStatus.LATE_LEFT_EARLY,
            AttendanceStatus.MISSING_CHECK_OUT
    );
    private static final Set<AttendanceStatus> LATE_STATUSES = Set.of(
            AttendanceStatus.LATE,
            AttendanceStatus.LATE_LEFT_EARLY
    );

    private static final QCohort cohort = QCohort.cohort;
    private static final QCohortMembership cohortMembership = QCohortMembership.cohortMembership;
    private static final QCohortAttendancePolicy attendancePolicy =
            QCohortAttendancePolicy.cohortAttendancePolicy;
    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;
    private static final QAttendanceRecord attendanceRecord = QAttendanceRecord.attendanceRecord;
    private static final QUserCharacter userCharacter = QUserCharacter.userCharacter;
    private static final QUserDailyQuest userDailyQuest = QUserDailyQuest.userDailyQuest;

    private final JPAQueryFactory queryFactory;


    @Override
    public PredictionFeatureSnapshot read(
            UUID userId,
            Long cohortId,
            Long cohortMembershipId,
            LocalDate baseDate,
            Instant observedAt
    ) {
        MembershipContext membershipContext = findMembershipContext(cohortId, cohortMembershipId);
        LocalDate membershipStartDate = membershipStartDate(membershipContext);
        LocalDate recentStartDate = baseDate.minusDays(RECENT_DAYS - 1L);

        // 소속, 학습, 출결, 게임 조회를 하나의 스냅샷으로 묶어서 반환
        return new PredictionFeatureSnapshot(
                baseDate,
                membershipStartDate,
                membershipContext.attendanceTimezone(),
                findStudyHistory(cohortMembershipId, recentStartDate, baseDate),
                findAttendanceHistory(
                        cohortMembershipId,
                        membershipStartDate,
                        recentStartDate,
                        baseDate,
                        observedAt,
                        membershipContext
                ),
                findGamificationHistory(userId, membershipStartDate, baseDate)
        );
    }

    // 기수·소속·출결 정책을 조인하여 소속 시작일 계산과 시간대 변환에 필요한 컨텍스트를 조회
    private MembershipContext findMembershipContext(Long cohortId, Long cohortMembershipId) {
        Tuple context = queryFactory
                .select(
                        cohort.startDate,             // 기수 공식 시작일 (집계 시작일 하한선)
                        cohortMembership.requestedAt, // 소속 가입 요청 일시 (processedAt 누락 시 fallback)
                        cohortMembership.processedAt, // 소속 승인/처리 일시 (소속 활성화 일자 계산 기준)
                        attendancePolicy.timezone,    // 기수 출결 정책 시간대 (UTC -> 로컬 날짜 변환용 ZoneId)
                        attendancePolicy.absenceCutoffTime // 오늘의 미등원 여부를 확정할 수 있는 마감 시각
                )
                .from(cohortMembership)
                .join(cohort).on(cohort.id.eq(cohortMembership.cohortId))
                .leftJoin(attendancePolicy).on(attendancePolicy.cohortId.eq(cohort.id))
                .where(
                        cohortMembership.id.eq(cohortMembershipId),
                        cohortMembership.cohortId.eq(cohortId)
                )
                .fetchOne();

        if (context == null) {
            throw new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND);
        }

        String timezone = context.get(attendancePolicy.timezone);
        if (timezone == null || timezone.isBlank()) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
        }
        LocalTime absenceCutoffTime = context.get(attendancePolicy.absenceCutoffTime);
        if (absenceCutoffTime == null) {
            // 출결 정책은 존재하더라도 결석 마감 시각이 없으면 오늘 노쇼를 계산할 수 없다.
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
        }

        return new MembershipContext(
                context.get(cohort.startDate),
                context.get(cohortMembership.requestedAt),
                context.get(cohortMembership.processedAt),
                timezone,
                absenceCutoffTime
        );
    }

    // 기수 시작일과 소속 활성일 중 늦은 날짜를 출결 전체 기간의 시작일
    private LocalDate membershipStartDate(MembershipContext context) {
        OffsetDateTime membershipStartedAt = context.processedAt();
        if (membershipStartedAt == null) {
            // TODO: ACTIVE 소속의 processedAt 누락을 데이터 오류로 처리할지 확정한다.
            membershipStartedAt = context.requestedAt();
        }

        LocalDate activatedDate = membershipStartedAt
                .atZoneSameInstant(ZoneId.of(context.attendanceTimezone()))
                .toLocalDate();

        return activatedDate.isAfter(context.cohortStartDate())
                ? activatedDate
                : context.cohortStartDate();
    }

    // 삭제되지 않은 확정 기록에서 최근 일별 공부시간과 전체 공부시간을 조회한다.
    private StudyHistory findStudyHistory(
            Long cohortMembershipId,
            LocalDate recentStartDate,
            LocalDate baseDate
    ) {
        NumberExpression<Long> dailyStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        List<DailyStudySeconds> recentDailyStudySeconds = queryFactory
                .select(
                        studyRecord.aggregationDate,
                        dailyStudySeconds
                )
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(recentStartDate, baseDate)
                )
                .groupBy(studyRecord.aggregationDate)
                .orderBy(studyRecord.aggregationDate.asc())
                .fetch()
                .stream()
                .map(row -> new DailyStudySeconds(
                        row.get(studyRecord.aggregationDate),
                        valueOrZero(row.get(dailyStudySeconds))
                ))
                .toList();

        DateExpression<LocalDate> firstStudyDate = studyRecord.aggregationDate.min();
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        Tuple allStudy = queryFactory
                .select(firstStudyDate, totalStudySeconds)
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.loe(baseDate)
                )
                .fetchOne();

        return new StudyHistory(
                recentDailyStudySeconds,
                allStudy == null ? null : allStudy.get(firstStudyDate),
                allStudy == null ? 0L : valueOrZero(allStudy.get(totalStudySeconds))
        );
    }

    // finalStatus를 기준으로 최근 출결 내역과 전체 평일 등원·지각 일수를 조회
    private AttendanceHistory findAttendanceHistory(
            Long cohortMembershipId,
            LocalDate membershipStartDate,
            LocalDate recentStartDate,
            LocalDate baseDate,
            Instant observedAt,
            MembershipContext membershipContext
    ) {
        LocalDate effectiveRecentStartDate = recentStartDate.isAfter(membershipStartDate)
                ? recentStartDate
                : membershipStartDate;
        List<DailyAttendance> recentAttendance = queryFactory
                .select(
                        attendanceRecord.attendanceDate,
                        attendanceRecord.finalStatus,
                        attendanceRecord.checkedInAt
                )
                .from(attendanceRecord)
                .where(
                        attendanceRecord.cohortMembershipId.eq(cohortMembershipId),
                        attendanceRecord.attendanceDate.between(effectiveRecentStartDate, baseDate)
                )
                .orderBy(attendanceRecord.attendanceDate.asc())
                .fetch()
                .stream()
                .map(row -> new DailyAttendance(
                        row.get(attendanceRecord.attendanceDate),
                        row.get(attendanceRecord.finalStatus),
                        row.get(attendanceRecord.checkedInAt)
                ))
                .toList();

        NumberExpression<Long> attendedDays = attendedDaysExpression();
        NumberExpression<Long> lateDays = lateDaysExpression();
        NumberExpression<Long> pendingWeekdays = pendingWeekdaysExpression();
        Tuple allAttendance = queryFactory
                .select(attendedDays, lateDays, pendingWeekdays)
                .from(attendanceRecord)
                .where(
                        attendanceRecord.cohortMembershipId.eq(cohortMembershipId),
                        attendanceRecord.attendanceDate.between(membershipStartDate, baseDate)
                )
                .fetchOne();

        return new AttendanceHistory(
                recentAttendance,
                allAttendance == null ? 0L : valueOrZero(allAttendance.get(attendedDays)),
                allAttendance == null ? 0L : valueOrZero(allAttendance.get(lateDays)),
                allAttendance == null ? 0L : valueOrZero(allAttendance.get(pendingWeekdays)),
                resolveNoShowOnBaseDate(
                        recentAttendance,
                        membershipStartDate,
                        baseDate,
                        observedAt,
                        membershipContext
                )
        );
    }

    /**
     * 계약의 {@code noshowYesterday}는 이름과 달리 baseDate(오늘)의 미등원 여부다.
     * 기록 없는 평일은 결석 마감 이후에만 노쇼로 확정하고, 마감 전 또는 PENDING은
     * DTO의 0/1 어느 쪽으로도 왜곡하지 않고 피처 미확정 오류로 처리한다.
     */
    private boolean resolveNoShowOnBaseDate(
            List<DailyAttendance> recentAttendance,
            LocalDate membershipStartDate,
            LocalDate baseDate,
            Instant observedAt,
            MembershipContext membershipContext
    ) {
        if (baseDate.isBefore(membershipStartDate) || !isWeekday(baseDate)) {
            return false;
        }

        Map<LocalDate, DailyAttendance> attendanceByDate = recentAttendance.stream()
                .collect(Collectors.toMap(DailyAttendance::attendanceDate, Function.identity()));
        DailyAttendance todayAttendance = attendanceByDate.get(baseDate);
        if (todayAttendance == null) {
            ZoneId zoneId = ZoneId.of(membershipContext.attendanceTimezone());
            Instant absenceCutoff = baseDate
                    .atTime(membershipContext.absenceCutoffTime())
                    .atZone(zoneId)
                    .toInstant();
            if (observedAt.isBefore(absenceCutoff)) {
                throw new BusinessException(PredictionErrorCode.PREDICTION_FEATURE_NOT_READY);
            }
            // 결석 행을 만드는 배치가 아직 없으므로 마감 후의 행 부재를 노쇼로 해석한다.
            // TODO: 출결 확정 배치가 도입되면 배치 지연 시 허용할 유예 시간과 폴백 규칙을 확정한다.
            return true;
        }

        if (todayAttendance.finalStatus() == AttendanceStatus.PENDING) {
            // PENDING은 미확정 상태이므로 모든 출결 계산과 동일하게 0/1 변환에서 제외한다.
            // TODO: 출결 확정 배치에서 장기 PENDING을 MISSING_CHECK_OUT으로 전환한다.
            throw new BusinessException(PredictionErrorCode.PREDICTION_FEATURE_NOT_READY);
        }
        return todayAttendance.finalStatus() == AttendanceStatus.ABSENT;
    }

    // 최종 출결 상태 중 평일에 실제 등원으로 인정할 상태의 조건식
    private NumberExpression<Long> attendedDaysExpression() {
        return new com.querydsl.core.types.dsl.CaseBuilder()
                .when(
                        attendanceRecord.finalStatus.in(ATTENDED_STATUSES)
                                .and(attendanceDateIsWeekday())
                )
                .then(1L)
                .otherwise(0L)
                .sumLong()
                .coalesce(0L);
    }

    // 최종 출결 상태 중 평일에 지각으로 인정할 상태의 조건식
    private NumberExpression<Long> lateDaysExpression() {
        return new com.querydsl.core.types.dsl.CaseBuilder()
                .when(
                        attendanceRecord.finalStatus.in(LATE_STATUSES)
                                .and(attendanceDateIsWeekday())
                )
                .then(1L)
                .otherwise(0L)
                .sumLong()
                .coalesce(0L);
    }

    // 전체 등원율의 분모에서 제외할 미확정 평일 수 조건식
    private NumberExpression<Long> pendingWeekdaysExpression() {
        return new com.querydsl.core.types.dsl.CaseBuilder()
                .when(
                        attendanceRecord.finalStatus.eq(AttendanceStatus.PENDING)
                                .and(attendanceDateIsWeekday())
                )
                .then(1L)
                .otherwise(0L)
                .sumLong()
                .coalesce(0L);
    }

    // PostgreSQL ISO 요일(월=1, 일=7) 기준 평일 조건식
    private BooleanExpression attendanceDateIsWeekday() {
        NumberExpression<Double> isoDayOfWeek = Expressions.numberTemplate(
                Double.class,
                "function('date_part', 'isodow', {0})",
                attendanceRecord.attendanceDate
        );

        return isoDayOfWeek.between(1.0, 5.0);
    }

    // 대표 캐릭터 레벨, 전체 완료 퀘스트 수와 소속 기간의 날짜별 퀘스트 집계를 조회
    private GamificationHistory findGamificationHistory(
            UUID userId,
            LocalDate membershipStartDate,
            LocalDate baseDate
    ) {
        Integer representativeLevel = queryFactory
                .select(userCharacter.level)
                .from(userCharacter)
                .where(
                        userCharacter.userId.eq(userId),
                        userCharacter.representative.isTrue()
                )
                .fetchFirst();
        if (representativeLevel == null) {
            // 대표 캐릭터는 예측의 필수 피처이며 임의 레벨로 콜드스타트 처리하지 않는다.
            throw new BusinessException(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND);
        }

        Long completedQuestsTotal = queryFactory
                .select(userDailyQuest.id.count())
                .from(userDailyQuest)
                .where(
                        userDailyQuest.userId.eq(userId),
                        userDailyQuest.questDate.loe(baseDate),
                        userDailyQuest.completedAt.isNotNull()
                )
                .fetchOne();

        NumberExpression<Long> generatedCount = userDailyQuest.id.count();
        NumberExpression<Long> completedCount = new com.querydsl.core.types.dsl.CaseBuilder()
                .when(userDailyQuest.completedAt.isNotNull())
                .then(1L)
                .otherwise(0L)
                .sumLong()
                .coalesce(0L);
        List<DailyQuestSummary> dailyQuestSummaries = queryFactory
                .select(
                        userDailyQuest.questDate,
                        generatedCount,
                        completedCount
                )
                .from(userDailyQuest)
                .where(
                        userDailyQuest.userId.eq(userId),
                        userDailyQuest.questDate.between(membershipStartDate, baseDate)
                )
                .groupBy(userDailyQuest.questDate)
                .orderBy(userDailyQuest.questDate.asc())
                .fetch()
                .stream()
                .map(row -> new DailyQuestSummary(
                        row.get(userDailyQuest.questDate),
                        valueOrZero(row.get(generatedCount)),
                        valueOrZero(row.get(completedCount))
                ))
                .toList();

        return new GamificationHistory(
                representativeLevel,
                valueOrZero(completedQuestsTotal),
                dailyQuestSummaries
        );
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue() <= 5;
    }

    // 집계 결과가 없을 때 QueryDSL의 null 값을 0으로 정규화
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    // 예측 피처 산출에 필요한 소속 컨텍스트 (기수 시작일, 가입 요청/승인 일시, 출결 시간대)
    private record MembershipContext(
            LocalDate cohortStartDate,
            OffsetDateTime requestedAt,
            OffsetDateTime processedAt,
            String attendanceTimezone,
            LocalTime absenceCutoffTime
    ) {
    }
}
