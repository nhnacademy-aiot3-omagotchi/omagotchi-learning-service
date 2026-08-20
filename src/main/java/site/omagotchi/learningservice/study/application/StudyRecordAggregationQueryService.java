package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 다른 Feature가 확정된 공부 기록의 합계를 조회하는 공개 계약.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRecordAggregationQueryService {

    private final StudyRecordQueryRepository studyRecordQueryRepository;

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

        // TODO: 실행 중 timer_runs의 경과 시간은 별도 공개 조회 계약으로 제공한다.
        return studyRecordQueryRepository.findConfirmedDurations(
                cohortMembershipIds,
                startDate,
                endDate
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
