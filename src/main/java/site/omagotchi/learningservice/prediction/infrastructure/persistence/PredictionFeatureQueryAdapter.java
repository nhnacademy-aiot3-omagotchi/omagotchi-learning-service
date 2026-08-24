package site.omagotchi.learningservice.prediction.infrastructure.persistence;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
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
import site.omagotchi.learningservice.prediction.application.port.PredictionFeatureSnapshotReader;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.*;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionFeatureQueryAdapter implements PredictionFeatureSnapshotReader {

    private static final int RECENT_DAYS = 30;
    // 지각 피처는 지각과 조퇴가 각각 또는 함께 발생한 최종 상태를 모두 포함한다.
    private static final Set<AttendanceStatus> LATE_STATUSES = Set.of(
            AttendanceStatus.LATE,
            AttendanceStatus.LEFT_EARLY,
            AttendanceStatus.LATE_LEFT_EARLY
    );

    private static final QCohort cohort = QCohort.cohort;
    private static final QCohortMembership cohortMembership = QCohortMembership.cohortMembership;
    private static final QCohortAttendancePolicy attendancePolicy = QCohortAttendancePolicy.cohortAttendancePolicy;
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
            LocalDate featureDate
    ) {
        MembershipContext membershipContext = findMembershipContext(cohortId, cohortMembershipId);
        LocalDate membershipStartDate = membershipStartDate(membershipContext);
        LocalDate recentStartDate = featureDate.minusDays(RECENT_DAYS - 1L);

        // 모든 원천값은 targetDate의 전날인 featureDate까지만 조회한다.
        return new PredictionFeatureSnapshot(
                featureDate,
                membershipStartDate,
                membershipContext.attendanceTimezone(),
                findStudyHistory(
                        cohortMembershipId,
                        membershipStartDate,
                        recentStartDate,
                        featureDate
                ),
                findAttendanceHistory(
                        cohortMembershipId,
                        membershipStartDate,
                        recentStartDate,
                        featureDate
                ),
                findGamificationHistory(userId, membershipStartDate, featureDate)
        );
    }

    // 기수·소속·출결 정책을 조인하여 소속 시작일 계산과 시간대 변환에 필요한 컨텍스트를 조회
    private MembershipContext findMembershipContext(Long cohortId, Long cohortMembershipId) {
        Tuple context = queryFactory
                .select(
                        cohort.startDate,             // 기수 공식 시작일 (집계 시작일 하한선)
                        cohortMembership.processedAt, // 소속 승인/처리 일시 (소속 활성화 일자 계산 기준)
                        attendancePolicy.timezone    // 기수 출결 정책 시간대 (UTC -> 로컬 날짜 변환용 ZoneId)
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
        return new MembershipContext(
                context.get(cohort.startDate),
                context.get(cohortMembership.processedAt),
                timezone
        );
    }

    // 기수 시작일과 소속 활성일 중 늦은 날짜를 출결 전체 기간의 시작일
    private LocalDate membershipStartDate(MembershipContext context) {
        OffsetDateTime membershipStartedAt = context.processedAt();
        if (membershipStartedAt == null) {
            // ACTIVE 소속은 processedAt이 필수이므로 신청 시각으로 대체하지 않는다.
            throw new IllegalStateException("ACTIVE 소속의 processedAt이 누락되었습니다.");
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
            LocalDate membershipStartDate,
            LocalDate recentStartDate,
            LocalDate featureDate
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
                        studyRecord.aggregationDate.between(recentStartDate, featureDate)
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
                        studyRecord.aggregationDate.loe(featureDate)
                )
                .fetchOne();

        NumberExpression<Long> studiedWeekdaysAll = studyRecord.aggregationDate.countDistinct();
        Long studiedWeekdaysAllValue = queryFactory
                .select(studiedWeekdaysAll)
                .from(studyRecord)
                .where(
                        studyRecord.cohortMembershipId.eq(cohortMembershipId),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(membershipStartDate, featureDate),
                        studyDateIsWeekday()
                )
                .fetchOne();

        return new StudyHistory(
                recentDailyStudySeconds,
                allStudy == null ? null : allStudy.get(firstStudyDate),
                allStudy == null ? 0L : valueOrZero(allStudy.get(totalStudySeconds)),
                valueOrZero(studiedWeekdaysAllValue)
        );
    }

    // 최근 출결 메타데이터와 확정 학습일에 해당하는 전체 지각·조퇴 일수를 조회
    private AttendanceHistory findAttendanceHistory(
            Long cohortMembershipId,
            LocalDate membershipStartDate,
            LocalDate recentStartDate,
            LocalDate featureDate
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
                        attendanceRecord.attendanceDate.between(effectiveRecentStartDate, featureDate)
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

        NumberExpression<Long> lateStudiedDaysAll =
                attendanceRecord.attendanceDate.countDistinct();
        Long lateStudiedDaysAllValue = queryFactory
                .select(lateStudiedDaysAll)
                .from(attendanceRecord)
                .where(
                        attendanceRecord.cohortMembershipId.eq(cohortMembershipId),
                        attendanceRecord.attendanceDate.between(membershipStartDate, featureDate),
                        attendanceRecord.finalStatus.in(LATE_STATUSES),
                        attendanceDateIsWeekday(),
                        JPAExpressions
                                .selectOne()
                                .from(studyRecord)
                                .where(
                                        studyRecord.cohortMembershipId.eq(
                                                attendanceRecord.cohortMembershipId
                                        ),
                                        studyRecord.aggregationDate.eq(
                                                attendanceRecord.attendanceDate
                                        ),
                                        studyRecord.deletedAt.isNull()
                                )
                                .exists()
                )
                .fetchOne();

        return new AttendanceHistory(
                recentAttendance,
                valueOrZero(lateStudiedDaysAllValue)
        );
    }

    // PostgreSQL ISO 요일(월=1, 일=7) 기준 평일 조건식
    private BooleanExpression attendanceDateIsWeekday() {
        // 출결 피처의 날짜 분모는 PostgreSQL ISO 요일 기준 월요일~금요일만 사용한다.
        NumberExpression<Double> isoDayOfWeek = Expressions.numberTemplate(
                Double.class,
                "function('date_part', 'isodow', {0})",
                attendanceRecord.attendanceDate
        );

        return isoDayOfWeek.between(1.0, 5.0);
    }

    // PostgreSQL ISO 요일(월=1, 일=7) 기준 확정 학습 평일 조건식
    private BooleanExpression studyDateIsWeekday() {
        NumberExpression<Double> isoDayOfWeek = Expressions.numberTemplate(
                Double.class,
                "function('date_part', 'isodow', {0})",
                studyRecord.aggregationDate
        );

        return isoDayOfWeek.between(1.0, 5.0);
    }

    // 대표 캐릭터 레벨, 전체 완료 퀘스트 수와 소속 기간의 날짜별 퀘스트 집계를 조회
    private GamificationHistory findGamificationHistory(
            UUID userId,
            LocalDate membershipStartDate,
            LocalDate featureDate
    ) {
        // 레벨은 날짜 스냅샷이 아니라 요청 시점의 최신 대표 캐릭터 레벨을 사용한다.
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
                        userDailyQuest.questDate.loe(featureDate),
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
                        userDailyQuest.questDate.between(membershipStartDate, featureDate)
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

    // 집계 결과가 없을 때 QueryDSL의 null 값을 0으로 정규화
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    // 예측 피처 산출에 필요한 소속 컨텍스트 (기수 시작일, 승인 일시, 출결 시간대)
    private record MembershipContext(
            LocalDate cohortStartDate,
            OffsetDateTime processedAt,
            String attendanceTimezone
    ) {
    }
}
