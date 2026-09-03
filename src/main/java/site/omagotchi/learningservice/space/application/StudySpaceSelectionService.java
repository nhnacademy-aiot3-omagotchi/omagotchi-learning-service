package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

/** 공용 학습 공간 입장 정책의 소유자. */
@Service
@RequiredArgsConstructor
@Transactional
public class StudySpaceSelectionService {

    private final SpaceRepository spaceRepository;

    /** 체류 전환과 같은 트랜잭션에서 활성 STUDY 공간을 잠그고 승인한다. */
    public void requireSelectableStudySpace(Long spaceId) {
        if (spaceId == null || spaceId <= 0L) {
            throw new BusinessException(SpaceErrorCode.INVALID_SPACE_ID);
        }

        Space studySpace = spaceRepository.findByIdForUpdate(spaceId)
                .orElseThrow(() -> new BusinessException(SpaceErrorCode.NOT_FOUND));
        if (studySpace.getSpaceType() != SpaceType.STUDY || !studySpace.isActive()) {
            throw new BusinessException(SpaceErrorCode.STUDY_SPACE_NOT_SELECTABLE);
        }
    }
}
