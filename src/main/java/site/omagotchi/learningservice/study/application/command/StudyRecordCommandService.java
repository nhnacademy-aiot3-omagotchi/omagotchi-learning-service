package site.omagotchi.learningservice.study.application.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StudyRecordCommandService {

    private final StudyRecordRepository studyRecordRepository;

    public StudyRecordResult create(
            UUID commandId,
            Long cohortMembershipId,
            CreateStudyRecordRequest request
    ) {
        // TODO: Presentation Request 대신 application Command를 입력으로 사용한다.
        // TODO(REC-002, REC-004, REC-006): 분 단위 입력, 시간 순서, 미래 시각과 허용 범위를 검증한다.
        // TODO(OVL-001): 기존 공부 기록과 활성 타이머의 겹침 여부를 검증한다.
        // TODO(DAT-001~003): KST 04:00 경계별로 분할하고 분할 전후 공부 시간을 보존한다.
        // TODO(REC-009, SYN-002): commandId 영수증으로 같은 요청의 중복 저장을 방지한다.

        // 현재 단계에서는 전달받은 구간 전체를 하나의 기록으로 저장한다.
        long studySeconds = Duration.between(request.startTime(), request.endTime()).getSeconds();
        LocalDate aggregationDate = request.startTime()
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDate();

        StudyRecordEntity entity = StudyRecordEntity.builder()
                .cohortMembershipId(cohortMembershipId)
                .aggregationDate(aggregationDate)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .studySeconds(studySeconds)
                .build();

        StudyRecordEntity saved = studyRecordRepository.save(entity);

        return StudyRecordResult.from(saved);
    }

    public StudyRecordResult update(
            UUID commandId,
            Long cohortMembershipId,
            UUID studyRecordId,
            UpdateStudyRecordRequest request
    ) {
        // TODO: Presentation Request 대신 application Command를 입력으로 사용한다.
        // TODO(REC-005, SEC-001): 인증된 소속이 소유한 활성 기록만 수정 대상으로 조회한다.
        // TODO(REC-007): 수정 구간 전체의 초로 studySeconds를 다시 계산한다.
        // TODO(REC-009, OVL-002): expectedVersion과 동시 변경 충돌을 검증한다.
        // TODO(REC-002, REC-004): 분 단위 입력, 시간 순서, 미래 시각과 허용 범위를 검증한다.
        // TODO(OVL-001): 자기 자신을 제외한 기존 기록과 활성 타이머의 겹침을 검증한다.
        // TODO(DAT-001~004): 04:00 경계 분할 결과를 모두 검증한 뒤 하나의 트랜잭션으로 반영한다.
        // TODO(SYN-002): commandId 영수증으로 같은 수정 요청의 중복 반영을 방지한다.

        StudyRecordEntity entity = studyRecordRepository.findById(studyRecordId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        long studySeconds = Duration.between(request.startTime(), request.endTime()).getSeconds();
        LocalDate aggregationDate = request.startTime()
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDate();

        entity.applyUpdate(aggregationDate, request.startTime(), request.endTime(), studySeconds);
        StudyRecordEntity saved = studyRecordRepository.save(entity);

        return StudyRecordResult.from(saved);
    }

    public void delete(
            UUID commandId,
            Long cohortMembershipId,
            UUID studyRecordId
    ) {
        // TODO: application Command를 입력으로 사용한다.
        // TODO(REC-005, SEC-001): 인증된 소속이 소유한 활성 기록만 삭제 대상으로 조회한다.
        // TODO(REC-008): 삭제 기록을 일반 조회·통계·랭킹에서 제외하고 복구·보존 정책을 적용한다.
        // TODO(REC-009, OVL-002): 삭제 버전과 동시 변경 충돌을 검증한다.
        // TODO(SYN-002): commandId 영수증으로 같은 삭제 요청의 중복 반영을 방지한다.

        StudyRecordEntity entity = studyRecordRepository.findById(studyRecordId)
                .orElseThrow(() -> new BusinessException(StudyRecordErrorCode.NOT_FOUND));

        entity.applySoftDelete(Instant.now());
        // TODO: 삭제 시, 삭제한 유저에 대한 정보를 log에 남겨야 한다.

        studyRecordRepository.save(entity);
    }
}
