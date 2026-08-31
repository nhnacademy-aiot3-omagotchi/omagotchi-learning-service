package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.result.ManagedCohortResult;
import site.omagotchi.learningservice.cohort.application.result.UserManagedCohortsResult;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 전역 관리자 화면의 "기수 운영 권한" 조회 Use Case다.
 *
 * <p>전역 관리자는 전체 사용자 중 누구를 기수 매니저로 승격할지 결정한다. 그래서 이
 * 조회는 <b>운영 권한(MANAGER)만</b> 반환한다. STUDENT 소속까지 섞으면 거의 모든 행이
 * 채워져 "누가 운영자인가"라는 화면의 질문에 답하지 못한다.</p>
 *
 * <p>승격 자체는 이미 존재하는 {@code POST /api/v1/cohorts/{cohort-id}/managers}가
 * 소유한다. 여기서는 상태를 바꾸지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortManagerLookupService {

    public static final int USER_IDS_MAX = 100;

    private final CohortMembershipRepository membershipRepository;
    private final CohortAccessService accessService;

    /**
     * 요청한 사용자들의 활성 기수 매니저 소속을 일괄 조회한다.
     *
     * <p>운영 권한이 없는 사용자는 결과에서 제외된다. 호출부(BFF)가 사용자 목록 기준으로
     * 병합하므로, 100건을 물어 3건만 돌아오는 것이 정상 동작이다.</p>
     */
    public List<UserManagedCohortsResult> findManagedCohorts(
            Collection<UUID> userIds,
            GlobalRole globalRole
    ) {
        // Filter Chain의 role 검사와 별개로 Use Case 경계에서 한 번 더 확인한다.
        accessService.requireSystemAdmin(globalRole);

        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        List<UUID> distinctUserIds = userIds.stream().distinct().toList();
        if (distinctUserIds.size() > USER_IDS_MAX
                || distinctUserIds.stream().anyMatch(userId -> userId == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 사용자별로 묶되 Query의 정렬 순서를 유지한다.
        Map<UUID, List<ManagedCohortResult>> grouped = new LinkedHashMap<>();
        for (CohortMembershipRepository.CohortManagerAssignmentProjection assignment
                : membershipRepository.findActiveManagerAssignments(distinctUserIds)) {
            grouped.computeIfAbsent(assignment.getUserId(), key -> new ArrayList<>())
                    .add(new ManagedCohortResult(
                            assignment.getCohortId(),
                            assignment.getCohortName(),
                            assignment.getRole()
                    ));
        }

        return grouped.entrySet().stream()
                .map(entry -> new UserManagedCohortsResult(entry.getKey(), entry.getValue()))
                .toList();
    }
}
