package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberCurrentStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 다른 Feature가 멤버십별 공부시간을 조회하는 공개 계약.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRecordAggregationQueryService {

    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final TimerRunQueryRepository timerRunQueryRepository;
    private final TimerTimePolicy timerTimePolicy;

    /**
     * 멤버십별로 삭제되지 않은 {@code study_records}의 기간 합계를 일괄 조회한다.
     *
     * <p>각 멤버십은 최대 한 번 반환하며 합계가 0인 멤버십과 {@code timer_runs}는 제외한다.</p>
     */
    public List<MemberStudyDurationResult> getConfirmedDurations(
            Collection<Long> cohortMembershipIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return List.of();
        }

        return studyRecordQueryRepository.findConfirmedDurations(
                cohortMembershipIds,
                startDate,
                endDate
        );
    }

    /**
     * 멤버십별 현재 집계일 공부시간을 확정 기록과 정상 실행 중 타이머로 계산한다.
     *
     * <p>실행 시간은 현재 집계일의 KST 04:00보다 이전에 시작한 타이머라도
     * 집계일 시작 시각부터 {@code calculatedAt}까지만 포함한다.</p>
     */
    public List<MemberCurrentStudyDurationResult> getCurrentDurations(
            Collection<Long> cohortMembershipIds,
            Instant calculatedAt
    ) {
        if (calculatedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return List.of();
        }

        Set<Long> requestedMembershipIds = new LinkedHashSet<>(cohortMembershipIds);
        if (requestedMembershipIds.contains(null)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        LocalDate aggregationDate = AggregationDateTime.aggregationDate(calculatedAt);
        Map<Long, MutableCurrentDuration> durationsByMembershipId = new HashMap<>();
        getConfirmedDurations(
                requestedMembershipIds,
                aggregationDate,
                aggregationDate
        ).forEach(duration -> durationsByMembershipId.put(
                duration.cohortMembershipId(),
                new MutableCurrentDuration(duration.studySeconds(), false)
        ));

        getCurrentTimers(requestedMembershipIds, calculatedAt)
                .forEach(timer -> durationsByMembershipId.merge(
                        timer.cohortMembershipId(),
                        new MutableCurrentDuration(
                                timer.currentAggregationSeconds(),
                                true
                        ),
                        MutableCurrentDuration::add
                ));

        List<MemberCurrentStudyDurationResult> results = new ArrayList<>();
        requestedMembershipIds.forEach(membershipId -> {
            MutableCurrentDuration duration = durationsByMembershipId.get(membershipId);
            if (duration != null && duration.studySeconds() > 0L) {
                results.add(new MemberCurrentStudyDurationResult(
                        membershipId,
                        duration.studySeconds(),
                        duration.timerRunning()
                ));
            }
        });
        return List.copyOf(results);
    }

    /**
     * 멤버십별 정상 실행 중 타이머와 현재 집계일 반영 시간을 일괄 조회한다.
     *
     * <p>반환 목록에 멤버십이 있으면 {@code calculatedAt} 기준 실행 중이며,
     * 집계일 경계에서 막 시작한 0초 타이머도 포함한다.</p>
     */
    public List<MemberCurrentTimerResult> getCurrentTimers(
            Collection<Long> cohortMembershipIds,
            Instant calculatedAt
    ) {
        if (calculatedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return List.of();
        }

        Set<Long> requestedMembershipIds = new LinkedHashSet<>(cohortMembershipIds);
        if (requestedMembershipIds.contains(null)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        Instant aggregationStartedAt = AggregationDateTime.startOfAggregationDate(
                AggregationDateTime.aggregationDate(calculatedAt)
        );
        Map<Long, MemberCurrentTimerResult> timersByMembershipId = new HashMap<>();
        timerRunQueryRepository.findActiveByCohortMembershipIds(requestedMembershipIds)
                .stream()
                .filter(timerRun -> isNormallyRunning(timerRun, calculatedAt))
                .forEach(timerRun -> timersByMembershipId.put(
                        timerRun.getCohortMembershipId(),
                        currentTimerResult(
                                timerRun,
                                aggregationStartedAt,
                                calculatedAt
                        )
                ));

        return requestedMembershipIds.stream()
                .map(timersByMembershipId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isNormallyRunning(TimerRun timerRun, Instant calculatedAt) {
        return !timerRun.getStartedAt().isAfter(calculatedAt)
                && timerRun.isRunningAt(calculatedAt, timerTimePolicy);
    }

    private MemberCurrentTimerResult currentTimerResult(
            TimerRun timerRun,
            Instant aggregationStartedAt,
            Instant calculatedAt
    ) {
        Instant effectiveStartedAt = timerRun.getStartedAt().isAfter(aggregationStartedAt)
                ? timerRun.getStartedAt()
                : aggregationStartedAt;
        return new MemberCurrentTimerResult(
                timerRun.getCohortMembershipId(),
                timerRun.getStartedAt(),
                timerTimePolicy.elapsedSeconds(effectiveStartedAt, calculatedAt)
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private record MutableCurrentDuration(
            long studySeconds,
            boolean timerRunning
    ) {

        private MutableCurrentDuration add(MutableCurrentDuration other) {
            return new MutableCurrentDuration(
                    Math.addExact(studySeconds, other.studySeconds),
                    timerRunning || other.timerRunning
            );
        }
    }
}
