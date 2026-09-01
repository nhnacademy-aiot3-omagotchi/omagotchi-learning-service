package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.PresenceSpaceQueryService;
import site.omagotchi.learningservice.attendance.application.port.AttendanceLabAccessPort;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

/** 공간 기능이 소유한 실습실 선택·정원 정책의 출결 제공 계약. */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceLabAccessService implements AttendanceLabAccessPort {

    private final SpaceRepository spaceRepository;
    private final PresenceSpaceQueryService presenceSpaceQueryService;

    @Override
    public void requireSelectableLab(Long cohortId, Long attendanceId, Long spaceId) {
        if (spaceId == null || spaceId <= 0L) {
            throw new BusinessException(SpaceErrorCode.INVALID_SPACE_ID);
        }

        Space lab = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(SpaceErrorCode.NOT_FOUND));

        if (lab.getSpaceType() != SpaceType.LAB
                || !lab.isActive()
                || !cohortId.equals(lab.getCohortId())) {
            throw new BusinessException(SpaceErrorCode.LAB_NOT_SELECTABLE);
        }

        SpacePresenceSummary summary = presenceSpaceQueryService.summarize(spaceId);
        boolean alreadyReserved = attendanceId != null
                && presenceSpaceQueryService.isReserved(spaceId, attendanceId);
        if (!alreadyReserved && summary.reservedCount() >= lab.getCapacity()) {
            throw new BusinessException(SpaceErrorCode.LAB_CAPACITY_EXCEEDED);
        }
    }
}
