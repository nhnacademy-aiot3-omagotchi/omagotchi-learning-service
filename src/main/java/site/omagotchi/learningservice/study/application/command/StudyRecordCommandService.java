package site.omagotchi.learningservice.study.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.global.util.StudyTimeParser;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StudyRecordCommandService {

    private final StudyRecordRepository studyRecordRepository;
    private final DateTimeProvider dateTimeProvider;

    public StudyRecordResult create(
            UUID commandId,
            Long cohortMembershipId,
            CreateStudyRecordCommand command
    ) {
        // TODO(DAT-001~003): KST 04:00 경계별로 분할하고 분할 전후 공부 시간을 보존한다.
        // TODO(REC-009, SYN-002): commandId 영수증으로 같은 요청의 중복 저장을 방지한다.

        // Instance로 날짜 파싱
        Instant startInstant = StudyTimeParser.parseToInstant(command.date(), command.startTime());
        Instant endInstant = StudyTimeParser.parseToInstant(command.date(), command.endTime());

        // 기록 시간 범위 검증
        validateTimeRange(startInstant, endInstant);

        // 오버랩 검증
        validateNoExistingRecordOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                null
        );

        // 현재 단계에서는 전달받은 구간 전체를 하나의 기록으로 저장한다.
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();
        LocalDate aggregationDate = dateTimeProvider.calculateAggregationDate(startInstant);

        StudyRecordEntity entity = StudyRecordEntity.builder()
                .cohortMembershipId(cohortMembershipId)
                .aggregationDate(aggregationDate)
                .startTime(startInstant)
                .endTime(endInstant)
                .studySeconds(studySeconds)
                .build();

        StudyRecordEntity saved = studyRecordRepository.save(entity);

        return StudyRecordResult.from(saved);
    }

    public StudyRecordResult update(
            UUID commandId,
            Long cohortMembershipId,
            UUID studyRecordId,
            UpdateStudyRecordCommand command
    ) {
        // TODO(REC-009, OVL-002): expectedVersion과 동시 변경 충돌을 검증한다.
        // TODO(DAT-001~004): 04:00 경계 분할 결과를 모두 검증한 뒤 하나의 트랜잭션으로 반영한다.
        // TODO(SYN-002): commandId 영수증으로 같은 수정 요청의 중복 반영을 방지한다.

        // 인증된 소속이 소유한 활성 기록만 수정 대상으로 조회
        StudyRecordEntity entity = studyRecordRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        // Instance로 날짜 파싱
        Instant startInstant = StudyTimeParser.parseToInstant(command.date(), command.startTime());
        Instant endInstant = StudyTimeParser.parseToInstant(command.date(), command.endTime());

        // 기록 시간 범위 검증
        validateTimeRange(startInstant, endInstant);

        // 오버랩 검증 (자신 제외)
        validateNoExistingRecordOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                studyRecordId
        );

        // (REC-007): 수정 구간 전체의 초로 studySeconds를 다시 계산한다.
        long studySeconds = Duration.between(startInstant, endInstant).getSeconds();
        // 기준 시간 계산
        LocalDate aggregationDate = dateTimeProvider.calculateAggregationDate(startInstant);

        entity.applyUpdate(aggregationDate, startInstant, endInstant, studySeconds);
        StudyRecordEntity saved = studyRecordRepository.save(entity);

        return StudyRecordResult.from(saved);
    }

    public void delete(
            UUID commandId,
            Long cohortMembershipId,
            UUID studyRecordId
    ) {
        // TODO(REC-009, OVL-002): 삭제 버전과 동시 변경 충돌을 검증한다.
        // TODO(SYN-002): commandId 영수증으로 같은 삭제 요청의 중복 반영을 방지한다.

        // (REC-005, SEC-001): 인증된 소속이 소유한 활성 기록만 삭제 대상으로 조회한다.
        StudyRecordEntity entity = studyRecordRepository
                .findActiveByIdAndCohortMembershipId(studyRecordId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        // (REC-008): 삭제 기록을 일반 조회·통계·랭킹에서 제외하고 복구·보존 정책을 적용한다.
        entity.applySoftDelete(dateTimeProvider.currentInstant());
        // TODO: 삭제 시, 삭제한 유저에 대한 정보를 log에 남겨야 한다.

        studyRecordRepository.save(entity);
    }

    private void validateTimeRange(Instant startInstant, Instant endInstant) {
        // startTime < endTime 검증
        if (!startInstant.isBefore(endInstant)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // 미래 시간 저장 예외 검증
        if (endInstant.isAfter(dateTimeProvider.currentInstant())) {
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
        boolean overlaps = studyRecordRepository.existsActiveOverlap(
                cohortMembershipId,
                startInstant,
                endInstant,
                excludedStudyRecordId
        );

        if (overlaps) {
            throw new BusinessException(StudyRecordErrorCode.OVERLAP);
        }
    }
}
