package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ManualStudyRecordPolicy {

    private final Clock clock;

    // 수동 기록에만 적용되는 입력 정책에 대한 검증
    public void validate(Instant startTime, Instant endTime) {
        if (!StudyTimePolicy.isMinuteAligned(startTime)
                || !StudyTimePolicy.isMinuteAligned(endTime)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (!startTime.isBefore(endTime) || endTime.isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (StudyTimePolicy.crossesAggregationBoundary(startTime, endTime)) {
            throw new BusinessException(StudyRecordErrorCode.AGGREGATION_BOUNDARY_CROSSED);
        }
    }
}
