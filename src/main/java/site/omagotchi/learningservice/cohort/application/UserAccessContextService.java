package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.application.result.CohortAccessSummary;
import site.omagotchi.learningservice.cohort.application.result.UserAccessContextResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessType;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 전역 역할과 현재 기수 소속을 조합해 로그인 사용자의 접근 컨텍스트를 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccessContextService {

    private final CohortMembershipQuery membershipQuery;
    private final CohortPersistence cohortPersistence;

    public UserAccessContextResult getContext(UUID userId, GlobalRole globalRole) {
        if (globalRole == GlobalRole.SYSTEM_ADMIN) {
            return new UserAccessContextResult(
                    globalRole,
                    UserAccessType.SYSTEM_ADMIN,
                    List.of(),
                    List.of()
            );
        }

        List<CohortMembership> currentMemberships = membershipQuery
                .findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .filter(this::isCurrent)
                .toList();

        Map<Long, Cohort> cohortsById = cohortPersistence.findAllById(
                        currentMemberships.stream()
                                .map(CohortMembership::getCohortId)
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .filter(cohort -> cohort.getStatus() != CohortStatus.CLOSED)
                .collect(Collectors.toMap(Cohort::getId, Function.identity()));

        List<CohortAccessSummary> managedCohorts = summaries(
                currentMemberships,
                CohortMembershipRole.MANAGER,
                cohortsById
        );
        List<CohortAccessSummary> studentCohorts = summaries(
                currentMemberships,
                CohortMembershipRole.STUDENT,
                cohortsById
        );

        UserAccessType accessType = !managedCohorts.isEmpty()
                ? UserAccessType.COHORT_MANAGER
                : !studentCohorts.isEmpty()
                ? UserAccessType.STUDENT
                : UserAccessType.USER;

        return new UserAccessContextResult(
                globalRole,
                accessType,
                managedCohorts,
                studentCohorts
        );
    }

    private boolean isCurrent(CohortMembership membership) {
        return membership.getStatus() == CohortMembershipStatus.ACTIVE
                && membership.getEndedAt() == null;
    }

    private List<CohortAccessSummary> summaries(
            List<CohortMembership> memberships,
            CohortMembershipRole role,
            Map<Long, Cohort> cohortsById
    ) {
        return memberships.stream()
                .filter(membership -> membership.getRole() == role)
                .map(CohortMembership::getCohortId)
                .distinct()
                .map(cohortsById::get)
                .filter(java.util.Objects::nonNull)
                .map(CohortAccessSummary::from)
                .toList();
    }
}
