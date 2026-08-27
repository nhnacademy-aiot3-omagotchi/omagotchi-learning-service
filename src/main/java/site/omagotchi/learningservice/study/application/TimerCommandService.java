package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;
import site.omagotchi.learningservice.study.application.port.StudyEventPublisher;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TimerCommandService {

    private final CohortAccessService cohortAccessService;
    private final TimerRunRepository timerRunRepository;
    private final TimerRunQueryRepository timerRunQueryRepository;
    private final StudyRecordRepository studyRecordRepository;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final StudyWriteLock studyWriteLock;
    private final Clock clock;
    private final TimerTimePolicy timerTimePolicy;
    private final StudyEventPublisher studyEventPublisher;

    public TimerStateResult start(
            UUID userId,
            Long cohortId
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        studyWriteLock.acquire(cohortMembershipId);

        Optional<TimerRun> openTimerRun = timerRunQueryRepository
                .findActiveByCohortMembershipId(cohortMembershipId);
        Instant currentAt = clock.instant();

        if (openTimerRun.isPresent()) {
            TimerRun previousTimerRun = openTimerRun.get();
            if (!previousTimerRun.expireIfDue(currentAt, timerTimePolicy)) {
                throw new BusinessException(TimerErrorCode.ALREADY_RUNNING);
            }

            // 만료 종료 UPDATE를 먼저 반영해 활성 실행 유일 인덱스 충돌을 방지한다.
            timerRunRepository.end(previousTimerRun);
        }

        TimerRun saved = timerRunRepository.create(
                TimerRun.start(cohortMembershipId, currentAt)
        );

        return TimerStateResult.running(
                saved.getId(),
                saved.getStartedAt(),
                0L
        );
    }

    public void stop(
            UUID userId,
            Long cohortId,
            UUID timerRunId
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        studyWriteLock.acquire(cohortMembershipId);

        TimerRun timerRun = requireOwnedOpenTimer(timerRunId, cohortMembershipId);
        Instant currentAt = clock.instant();

        TimerEndReason endReason = timerRun.stopOrExpire(currentAt, timerTimePolicy);

        if (endReason == TimerEndReason.STOP && timerRun.getMeasuredSeconds() > 0L) {
            List<StudyRecord> studyRecords = createStudyRecords(timerRun);
            if (!studyRecords.isEmpty()) {
                studyRecords.forEach(this::validateNoExistingRecordOverlap);
                studyRecords.forEach(studyRecordRepository::save);
                studyEventPublisher.publishCompleted(new StudyCompletedEvent(
                        userId,
                        timerRunId,
                        timerRun.getEndedAt()
                ));
            }
        }
        timerRunRepository.end(timerRun);
    }

    public void discard(
            UUID userId,
            Long cohortId,
            UUID timerRunId
    ) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        studyWriteLock.acquire(cohortMembershipId);

        TimerRun timerRun = requireOwnedOpenTimer(timerRunId, cohortMembershipId);
        Instant currentAt = clock.instant();

        timerRun.discardOrExpire(currentAt, timerTimePolicy);
        timerRunRepository.end(timerRun);
    }

    private TimerRun requireOwnedOpenTimer(
            UUID timerRunId,
            Long cohortMembershipId
    ) {
        TimerRun timerRun = timerRunQueryRepository
                .findOwnedById(timerRunId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(TimerErrorCode.RUN_NOT_FOUND));

        if (!timerRun.isRunning()) {
            throw new BusinessException(TimerErrorCode.ALREADY_ENDED);
        }

        return timerRun;
    }

    private List<StudyRecord> createStudyRecords(TimerRun timerRun) {
        Instant startedAt = timerRun.getStartedAt();
        Instant endedAt = timerRun.getEndedAt();
        Instant recordStartedAt = StudyTimePolicy.floorToMinute(startedAt);
        Instant recordEndedAt = StudyTimePolicy.floorToMinute(endedAt);
        long measuredSeconds = timerRun.getMeasuredSeconds();

        return StudyTimePolicy.findCrossedAggregationBoundary(startedAt, endedAt)
                .map(boundary -> createSplitStudyRecords(
                        timerRun,
                        recordStartedAt,
                        boundary,
                        recordEndedAt
                ))
                .orElseGet(() -> createStudyRecord(
                        timerRun.getCohortMembershipId(),
                        recordStartedAt,
                        recordEndedAt,
                        measuredSeconds
                ).stream().toList());
    }

    private List<StudyRecord> createSplitStudyRecords(
            TimerRun timerRun,
            Instant recordStartedAt,
            Instant boundary,
            Instant recordEndedAt
    ) {
        long measuredFirstChunkSeconds = Duration.between(
                timerRun.getStartedAt(),
                boundary
        ).getSeconds();
        long measuredSecondChunkSeconds = timerRun.getMeasuredSeconds()
                - measuredFirstChunkSeconds;
        List<StudyRecord> studyRecords = new ArrayList<>(2);

        createStudyRecord(
                timerRun.getCohortMembershipId(),
                recordStartedAt,
                boundary,
                measuredFirstChunkSeconds
        ).ifPresent(studyRecords::add);
        createStudyRecord(
                timerRun.getCohortMembershipId(),
                boundary,
                recordEndedAt,
                measuredSecondChunkSeconds
        ).ifPresent(studyRecords::add);

        return List.copyOf(studyRecords);
    }

    private Optional<StudyRecord> createStudyRecord(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            long measuredSeconds
    ) {
        if (!startTime.isBefore(endTime) || measuredSeconds <= 0L) {
            return Optional.empty();
        }

        long occupiedSeconds = Duration.between(startTime, endTime).getSeconds();
        // 종료 시각 내림으로 구간이 줄어든 경우 DB·도메인의 점유 구간 상한을 지킨다.
        long studySeconds = Math.min(measuredSeconds, occupiedSeconds);
        return Optional.of(StudyRecord.create(
                cohortMembershipId,
                startTime,
                endTime,
                studySeconds
        ));
    }

    private void validateNoExistingRecordOverlap(StudyRecord studyRecord) {
        boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
                studyRecord.getCohortMembershipId(),
                studyRecord.getStartTime(),
                studyRecord.getEndTime(),
                null
        );

        if (overlaps) {
            throw new BusinessException(StudyRecordErrorCode.OVERLAP);
        }
    }
}
