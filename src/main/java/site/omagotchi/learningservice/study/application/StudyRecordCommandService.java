package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;
import site.omagotchi.learningservice.study.application.port.StudyEventPublisher;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StudyRecordCommandService {

    private final CohortAccessService cohortAccessService;
    private final StudyRecordRepository studyRecordRepository;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final TimerRunQueryRepository timerRunQueryRepository;
    private final Clock clock;
    private final StudyWriteLock studyWriteLock;
    private final StudyEventPublisher studyEventPublisher;
    private final ManualStudyRecordPolicy manualStudyRecordPolicy;
    private final StudyRecordOverlapGuard studyRecordOverlapGuard;

    public StudyRecordResult create(
            UUID userId,
            Long cohortId,
            CreateStudyRecordCommand command
    ) {
        // membershipId 검증 및 변환
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        Instant startInstant = command.startTime();
        Instant endInstant = command.endTime();

        manualStudyRecordPolicy.validate(startInstant, endInstant);

        // cohortMembershipId 단위 transaction-scoped advisory lock 획득
        studyWriteLock.acquire(cohortMembershipId);

        // 실행 중인 타이머가 있는지 검증
        validateNoActiveTimer(cohortMembershipId);

        // 오버랩 검증
        studyRecordOverlapGuard.requireNoOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                null
        );

        // 현재 단계에서는 전달받은 구간 전체를 하나의 기록으로 저장한다.
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();
        StudyRecord entity = StudyRecord.create(
                cohortMembershipId,
                startInstant,
                endInstant,
                studySeconds
        );

        StudyRecord saved = studyRecordRepository.save(entity);
        studyEventPublisher.publishCompleted(new StudyCompletedEvent(
                userId,
                saved.getId(),
                saved.getEndTime()
        ));

        return StudyRecordResult.from(saved);
    }

    public StudyRecordResult update(
            UUID userId,
            Long cohortId,
            UUID studyRecordId,
            UpdateStudyRecordCommand command
    ) {
        // membershipId 검증 및 변환
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        // cohortMembershipId 단위 transaction-scoped advisory lock 획득
        studyWriteLock.acquire(cohortMembershipId);

        // 실행 중인 타이머가 있는지 검증
        validateNoActiveTimer(cohortMembershipId);

        // 인증된 소속이 소유한 활성 기록만 수정 대상으로 조회
        StudyRecord entity = studyRecordQueryRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        validateExpectedVersion(entity, command.expectedVersion());

        Instant startInstant = command.startTime();
        Instant endInstant = command.endTime();

        manualStudyRecordPolicy.validate(startInstant, endInstant);

        // 오버랩 검증 (자신 제외)
        studyRecordOverlapGuard.requireNoOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                studyRecordId
        );

        // 수정 구간으로 studySeconds를 재계산
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();

        entity.updateTimeRange(startInstant, endInstant, studySeconds);
        StudyRecord saved = studyRecordRepository.saveWithVersionCheck(entity);

        return StudyRecordResult.from(saved);
    }

    public void delete(
            UUID userId,
            Long cohortId,
            UUID studyRecordId,
            Long expectedVersion
    ) {
        // membershipId 검증 및 변환
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        // cohortMembershipId 단위 transaction-scoped advisory lock 획득
        studyWriteLock.acquire(cohortMembershipId);

        // 인증된 소속이 소유한 활성 기록만 삭제 대상으로 조회
        StudyRecord entity = studyRecordQueryRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        validateExpectedVersion(entity, expectedVersion);

        // 현재 기록 소프트 삭제
        entity.softDelete(clock.instant());
        // TODO: 삭제 시, 삭제한 유저에 대한 정보를 log에 남기기 (Optional)

        studyRecordRepository.saveWithVersionCheck(entity);
    }

    // ===== Private Methods =====

    private void validateNoActiveTimer(Long cohortMembershipId) {
        timerRunQueryRepository.findActiveByCohortMembershipId(cohortMembershipId)
                .ifPresent(timer -> {
                    throw new BusinessException(StudyRecordErrorCode.ACTIVE_TIMER_CONFLICT);
                });
    }

    private void validateExpectedVersion(
            StudyRecord entity,
            Long expectedVersion
    ) {
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            throw new BusinessException(StudyRecordErrorCode.VERSION_CONFLICT);
        }
    }

}
