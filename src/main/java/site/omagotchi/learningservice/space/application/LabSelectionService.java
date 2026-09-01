package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

/**
 * 실습실 선택 정책의 소유자.
 *
 * <p>선택 가능 판정에 필요한 세 가지 — LAB·활성·기수 배정, 정원, 현재 재실과 회의 후
 * 복귀 예약 — 을 모두 공간 기능이 소유한다. 다른 기능은 이 Service의 public Method만
 * 호출하고, 호출한 Transaction 안에서 대상 공간 행의 쓰기 잠금이 유지된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LabSelectionService {

    private final SpaceRepository spaceRepository;
    private final SpacePresenceQueryService spacePresenceQueryService;

    /**
     * 선택한 실습실을 승인한다. 반환할 때까지 대상 공간 행의 쓰기 잠금을 유지해,
     * 이 검증과 이어지는 체류 전환 사이에 공간이 비활성화되거나 배정 해제되지 않게 한다.
     *
     * @param cohortId     출결이 속한 기수
     * @param attendanceId 기존 출결이면 식별자, 새 체크인이면 {@code null}
     * @param spaceId      선택한 실습실
     */
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

        SpacePresenceSummary summary = spacePresenceQueryService.summarize(spaceId);
        boolean alreadyReserved = attendanceId != null
                && spacePresenceQueryService.isReserved(spaceId, attendanceId);
        if (!alreadyReserved && summary.reservedCount() >= lab.getCapacity()) {
            throw new BusinessException(SpaceErrorCode.LAB_CAPACITY_EXCEEDED);
        }
    }
}
