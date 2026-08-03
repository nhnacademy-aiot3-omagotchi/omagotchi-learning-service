package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
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
    private final StudyWriteLock studyWriteLock;
    private final Clock clock;
    private final TimerTimePolicy timerTimePolicy;

    public TimerStateResult start(
            UUID commandId,
            UUID userId,
            Long cohortId
    ) {
        // TODO: command_receipts 구현 후 commandId 기준 영수증 처리 연결

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
            UUID commandId,
            UUID userId,
            Long cohortId,
            UUID timerRunId
    ) {
        // TODO: command_receipts 구현 후 commandId 기준 영수증 처리 연결

        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        studyWriteLock.acquire(cohortMembershipId);

        TimerRun timerRun = requireOwnedOpenTimer(timerRunId, cohortMembershipId);
        Instant currentAt = clock.instant();

        TimerEndReason endReason = timerRun.stopOrExpire(currentAt, timerTimePolicy);

        if (endReason == TimerEndReason.STOP && timerRun.getMeasuredSeconds() > 0L) {
            createStudyRecords(timerRun).forEach(studyRecordRepository::save);
        }
        timerRunRepository.end(timerRun);
    }

    public void discard(
            UUID commandId,
            UUID userId,
            Long cohortId,
            UUID timerRunId
    ) {
        // TODO: command_receipts 구현 후 commandId 기준 영수증 처리 연결

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
        long measuredSeconds = timerRun.getMeasuredSeconds();

        return StudyTimePolicy.findCrossedAggregationBoundary(startedAt, endedAt)
                .map(boundary -> createSplitStudyRecords(
                        timerRun.getCohortMembershipId(),
                        startedAt,
                        boundary,
                        endedAt,
                        measuredSeconds
                ))
                .orElseGet(() -> List.of(StudyRecord.create(
                        timerRun.getCohortMembershipId(),
                        startedAt,
                        endedAt,
                        measuredSeconds
                )));
    }

    private List<StudyRecord> createSplitStudyRecords(
            Long cohortMembershipId,
            Instant startedAt,
            Instant boundary,
            Instant endedAt,
            long measuredSeconds
    ) {
        long firstChunkSeconds = Duration.between(startedAt, boundary).getSeconds();
        long secondChunkSeconds = measuredSeconds - firstChunkSeconds;

        return List.of(
                StudyRecord.create(
                        cohortMembershipId,
                        startedAt,
                        boundary,
                        firstChunkSeconds
                ),
                StudyRecord.create(
                        cohortMembershipId,
                        boundary,
                        endedAt,
                        secondChunkSeconds
                )
        );
    }
}
