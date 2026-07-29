package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.application.time.StudyTimePolicy;
import site.omagotchi.learningservice.study.domain.entity.StudyRecord;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StudyRecordCommandService {

    private final CohortAccessService cohortAccessService;
    private final StudyRecordRepository studyRecordRepository;
    private final StudyRecordQueryRepository studyRecordQueryRepository;
    private final Clock clock;
    private final StudyWriteLock studyWriteLock;

    public StudyRecordResult create(
            UUID commandId,
            UUID userId,
            Long cohortId,
            CreateStudyRecordCommand command
    ) {
        // TODO(REC-009, SYN-002): commandId 영수증으로 같은 요청의 중복 저장을 방지한다.

        // membershipId 검증 및 변환
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        Instant startInstant = command.startTime();
        Instant endInstant = command.endTime();

        // 기록 시간 범위, 집계 경계 겹침 검증
        validateTimeRange(startInstant, endInstant);
        validateSingleAggregationDate(startInstant, endInstant);

        // cohortMembershipId 단위 transaction-scoped advisory lock 획득
        studyWriteLock.acquire(cohortMembershipId);

        // 오버랩 검증
        validateNoExistingRecordOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                null
        );

        // 현재 단계에서는 전달받은 구간 전체를 하나의 기록으로 저장한다.
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();
        LocalDate aggregationDate = StudyTimePolicy.aggregationDate(startInstant);

        StudyRecord entity = StudyRecord.builder()
                .cohortMembershipId(cohortMembershipId)
                .aggregationDate(aggregationDate)
                .startTime(startInstant)
                .endTime(endInstant)
                .studySeconds(studySeconds)
                .build();

        StudyRecord saved = studyRecordRepository.save(entity);

        return StudyRecordResult.from(saved);
    }

    public StudyRecordResult update(
            UUID commandId,
            UUID userId,
            Long cohortId,
            UUID studyRecordId,
            UpdateStudyRecordCommand command
    ) {
        // TODO(SYN-002): commandId 영수증으로 같은 수정 요청의 중복 반영을 방지한다.

        // membershipId 검증 및 변환
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        // cohortMembershipId 단위 transaction-scoped advisory lock 획득
        studyWriteLock.acquire(cohortMembershipId);

        // 인증된 소속이 소유한 활성 기록만 수정 대상으로 조회
        StudyRecord entity = studyRecordQueryRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        validateExpectedVersion(entity, command.expectedVersion());

        Instant startInstant = command.startTime();
        Instant endInstant = command.endTime();

        // 기록 시간 범위, 집계 경계 겹침 검증
        validateTimeRange(startInstant, endInstant);
        validateSingleAggregationDate(startInstant, endInstant);

        // 오버랩 검증 (자신 제외)
        validateNoExistingRecordOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                studyRecordId
        );

        // 수정 구간으로 studySeconds를 재계산
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();
        // 기준 시간 계산
        LocalDate aggregationDate = StudyTimePolicy.aggregationDate(startInstant);

        entity.applyUpdate(aggregationDate, startInstant, endInstant, studySeconds);
        StudyRecord saved = studyRecordRepository.saveWithVersionCheck(entity);

        return StudyRecordResult.from(saved);
    }

    public void delete(
            UUID commandId,
            UUID userId,
            Long cohortId,
            UUID studyRecordId,
            Long expectedVersion
    ) {
        // TODO(SYN-002): commandId 영수증으로 같은 삭제 요청의 중복 반영을 방지한다.

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
        entity.applySoftDelete(clock.instant());
        // TODO: 삭제 시, 삭제한 유저에 대한 정보를 log에 남기기 (Optional)

        studyRecordRepository.saveWithVersionCheck(entity);
    }

    // ===== Private Methods =====

    private void validateTimeRange(Instant startInstant, Instant endInstant) {
        // startTime < endTime 검증
        if (!startInstant.isBefore(endInstant)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // 미래 시간 저장 예외 검증
        if (endInstant.isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // TODO: KDT 학습 기간 범위 검증 + 과거 기록 범위 검증(Optional)
    }

    private void validateNoExistingRecordOverlap(
            Long cohortMembershipId,
            Instant startInstant,
            Instant endInstant,
            UUID excludedStudyRecordId
    ) {
        boolean overlaps = studyRecordQueryRepository.existsActiveOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                excludedStudyRecordId
        );

        if (overlaps) {
            throw new BusinessException(StudyRecordErrorCode.OVERLAP);
        }
    }

    private void validateExpectedVersion(
            StudyRecord entity,
            Long expectedVersion
    ) {
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            throw new BusinessException(StudyRecordErrorCode.VERSION_CONFLICT);
        }
    }

    // 반개구간 [startInstant, endInstant)이 KST 04:00 집계 경계를 넘는지 검증한다.
    private void validateSingleAggregationDate(
            Instant startInstant,
            Instant endInstant
    ) {
        if (StudyTimePolicy.crossesAggregationBoundary(startInstant, endInstant)) {
            throw new BusinessException(StudyRecordErrorCode.AGGREGATION_BOUNDARY_CROSSED);
        }
    }
}
