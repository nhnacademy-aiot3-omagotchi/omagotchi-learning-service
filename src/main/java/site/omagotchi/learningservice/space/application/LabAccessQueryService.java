package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.PresenceSpaceQueryService;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.result.SelectableLabView;
import site.omagotchi.learningservice.space.domain.Space;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 학생이 자기 기수에서 선택할 수 있는 활성 실습실 목록을 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabAccessQueryService {

    private final CohortAccessService cohortAccessService;
    private final SpaceRepository spaceRepository;
    private final PresenceSpaceQueryService presenceSpaceQueryService;

    public List<SelectableLabView> findSelectableLabs(Long cohortId, UUID userId) {
        cohortAccessService.requireActiveStudentMembershipId(cohortId, userId);

        List<Space> labs = spaceRepository.findActiveLabsByCohortId(cohortId);
        if (labs.isEmpty()) {
            return List.of();
        }

        Map<Long, SpacePresenceSummary> summaries = presenceSpaceQueryService.summarize(
                labs.stream().map(Space::getId).toList()
        );

        return labs.stream()
                .map(lab -> new SelectableLabView(
                        lab.getId(),
                        lab.getName(),
                        lab.getCapacity(),
                        summaries.getOrDefault(
                                lab.getId(),
                                SpacePresenceSummary.empty()
                        ).reservedCount()
                ))
                .toList();
    }
}
