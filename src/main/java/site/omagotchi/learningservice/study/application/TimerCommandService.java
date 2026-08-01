package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TimerCommandService {

    private final CohortAccessService cohortAccessService;
    private final TimerRunRepository timerRunRepository;
    private final TimerRunQueryRepository timerRunQueryRepository;
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
            // TODO: KST 04:00 경계 분할 및 StudyRecord 저장 로직 추가 필요
            //  동일 트랜젝션에서 처리해야 함
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

    public void expire(UUID timerRunId, Long cohortMembershipId) {
        studyWriteLock.acquire(cohortMembershipId);

        TimerRun timerRun = timerRunQueryRepository
                .findOwnedById(timerRunId, cohortMembershipId)
                .orElse(null);

        if (timerRun == null) {
            return;
        }

        Instant currentAt = clock.instant();
        if (timerRun.expireIfDue(currentAt, timerTimePolicy)) {
            timerRunRepository.end(timerRun);
        }
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
}
