package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopLearnerPatternQueryService {

    // 익명성 보호: 표본이 이보다 작으면 집계하지 않는다
    private static final int MIN_STUDENT_COUNT = 10;
    // 익명성 보호: 상위 그룹이 이보다 작으면 개인이 특정될 수 있다
    private static final int MIN_TOP_GROUP_SIZE = 3;

    private final StudyPatternQueryService studyPatternQueryService;
    private final CohortAccessService cohortAccessService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;

    // 상위권 비교 Tool이 직접 부르는 진입점
    public TopLearnerPatternResult getTopLearnerPattern(UUID userId, Integer periodDaysOrNull) {
        return this.getTopLearnerPattern(this.cohortAccessService.requireCurrentActiveMembership(userId), periodDaysOrNull);
    }

    // 이미 활성 소속을 구한 호출자(리포트)를 위한 진입점
    public TopLearnerPatternResult getTopLearnerPattern(CohortMembership membership, Integer periodDaysOrNull) {
        // 1. 내 패턴을 먼저 계산한다.
        StudyPatternResult myPattern = studyPatternQueryService.getPattern(membership, periodDaysOrNull);
        int periodDays = myPattern.periodDays();

        // 2. 내 기수의 학생 명단
        Long cohortId = membership.getCohortId();
        List<CohortMembershipView> students =
                cohortMembershipQueryService.findActiveStudentMemberships(cohortId);
        if (students.size() < MIN_STUDENT_COUNT) {
            return TopLearnerPatternResult.insufficientSample(periodDays, students.size());
        }

        // 3. 기간 내 총 공부 시간으로 줄 세운다
        List<Long> membershipIds = new ArrayList<>();
        for (CohortMembershipView student : students) {
            membershipIds.add(student.membershipId());
        }
        LocalDate today = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate startDate = today.minusDays(periodDays - 1L);

        List<MemberStudyDurationResult> durations = new ArrayList<>(
                studyRecordAggregationDurations(membershipIds, startDate, today));
        if (durations.size() < MIN_TOP_GROUP_SIZE) {
            return TopLearnerPatternResult.noData(periodDays, students.size());
        }
        // 4. 공부 시간이 많은 순 정렬
        durations.sort(new Comparator<MemberStudyDurationResult>() {
            @Override
            public int compare(MemberStudyDurationResult a, MemberStudyDurationResult b) {
                return Long.compare(b.studySeconds(), a.studySeconds());
            }
        });

        // 5. 상위 그룹 선정: 전체의 10%, 단 최소 3명
        int topGroupSize = students.size() / 10;
        if (topGroupSize < MIN_TOP_GROUP_SIZE) {
            topGroupSize = MIN_TOP_GROUP_SIZE;
        }
        if (topGroupSize > durations.size()) {
            topGroupSize = durations.size();
        }
        List<Long> topMembershipIds = new ArrayList<>();
        for (int i = 0; i < topGroupSize; i++) {
            topMembershipIds.add(durations.get(i).cohortMembershipId());
        }

        // 6. 상위 그룹의 기록을 한 번에 가져와 익명 집계한다
        List<StudyRecord> topRecords = studyRecordQueryRepository
                .findActiveRecordsBetweenForMemberships(topMembershipIds, startDate, today);
        if (topRecords.isEmpty()) {
            return TopLearnerPatternResult.noData(periodDays, students.size());
        }
        return aggregate(myPattern, periodDays, students.size(), topGroupSize, topRecords);
    }

    private List<MemberStudyDurationResult> studyRecordAggregationDurations(
            List<Long> membershipIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return studyRecordQueryRepository.findConfirmedDurations(membershipIds, startDate, endDate);
    }

    /**
     * 상위 그룹 전체의 기록을 하나의 익명 통계로 요약한다.
     */
    private TopLearnerPatternResult aggregate(
            StudyPatternResult myPattern,
            int periodDays,
            int cohortStudentCount,
            int topGroupSize,
            List<StudyRecord> topRecords
    ) {
        // 1. 섞여 온 기록으로 "사람 × 날짜" 표를 채운다.
        //    첫 세션과 마지막 세션을 함께 들고 있어야 그날 자리에 있던 시간을 잴 수 있다
        Map<Long, Map<LocalDate, StudyRecord>> firstByMemberAndDate = new HashMap<>();
        Map<Long, Map<LocalDate, StudyRecord>> lastByMemberAndDate = new HashMap<>();
        long totalStudySeconds = 0;
        for (StudyRecord record : topRecords) {
            totalStudySeconds = totalStudySeconds + record.getStudySeconds();

            Map<LocalDate, StudyRecord> byDate = firstByMemberAndDate.get(record.getCohortMembershipId());
            if (byDate == null) {
                byDate = new HashMap<>();
                firstByMemberAndDate.put(record.getCohortMembershipId(), byDate);
            }
            StudyRecord current = byDate.get(record.getAggregationDate());
            if (current == null || record.getStartTime().isBefore(current.getStartTime())) {
                byDate.put(record.getAggregationDate(), record);
            }

            Map<LocalDate, StudyRecord> lastByDate =
                    lastByMemberAndDate.get(record.getCohortMembershipId());
            if (lastByDate == null) {
                lastByDate = new HashMap<>();
                lastByMemberAndDate.put(record.getCohortMembershipId(), lastByDate);
            }
            StudyRecord latest = lastByDate.get(record.getAggregationDate());
            if (latest == null || record.getEndTime().isAfter(latest.getEndTime())) {
                lastByDate.put(record.getAggregationDate(), record);
            }
        }

        // 2. 완성된 표를 돌며 계산 재료를 꺼낸다
        int totalStudyDays = 0;
        long totalSpanSeconds = 0;
        List<Integer> shiftedMinutes = new ArrayList<>();
        for (Map.Entry<Long, Map<LocalDate, StudyRecord>> member : firstByMemberAndDate.entrySet()) {
            Map<LocalDate, StudyRecord> byDate = member.getValue();
            Map<LocalDate, StudyRecord> lastByDate = lastByMemberAndDate.get(member.getKey());
            totalStudyDays = totalStudyDays + byDate.size();

            for (Map.Entry<LocalDate, StudyRecord> day : byDate.entrySet()) {
                StudyRecord first = day.getValue();
                shiftedMinutes.add(StudyPatternMath.toShiftedMinutes(first.getStartTime()));

                // 그 사람이 그날 자리에 있던 시간: 첫 세션 시작~마지막 세션 종료
                StudyRecord last = lastByDate.get(day.getKey());
                totalSpanSeconds = totalSpanSeconds
                        + StudyPatternMath.spanSeconds(first.getStartTime(), last.getEndTime());
            }
        }

        // 3. 산수 세 개와 대표 시작 시각을 결과 상자에 담는다
        return new TopLearnerPatternResult(
                TopLearnerPatternResult.Status.OK,
                periodDays,
                cohortStudentCount,
                topGroupSize,
                totalStudySeconds / 60 / totalStudyDays,        // 공부한 날 하루 평균 분
                (int) Math.round((double) totalStudyDays / topGroupSize),           // 1인당 평균 학습일 (반올림)
                totalStudySeconds / topRecords.size() / 60,                         // 평균 세션 길이(분)
                StudyPatternMath.focusDensityPercent(totalStudySeconds, totalSpanSeconds), // 몰입 밀도(%)
                StudyPatternMath.medianStartTime(shiftedMinutes),                   // 대표 시작 시각 "HH:mm"
                myPattern                                                           // 내 패턴은 계산 없이 동봉
        );
    }
}