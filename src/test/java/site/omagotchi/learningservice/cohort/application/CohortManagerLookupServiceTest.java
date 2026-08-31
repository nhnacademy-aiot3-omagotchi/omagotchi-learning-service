package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.result.UserManagedCohortsResult;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortManagerLookupServiceTest {

    private static final UUID FIRST_USER_ID = new UUID(0L, 10L);
    private static final UUID SECOND_USER_ID = new UUID(0L, 11L);

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @InjectMocks
    private CohortManagerLookupService managerLookupService;

    @Test
    @DisplayName("사용자별로 운영 기수를 묶어 반환")
    void groupsAssignmentsByUser() {
        when(membershipRepository.findActiveManagerAssignments(anyCollection()))
                .thenReturn(List.of(
                        assignment(FIRST_USER_ID, 1L, "1기"),
                        assignment(FIRST_USER_ID, 2L, "2기"),
                        assignment(SECOND_USER_ID, 3L, "3기")
                ));

        List<UserManagedCohortsResult> results = managerLookupService.findManagedCohorts(
                List.of(FIRST_USER_ID, SECOND_USER_ID), GlobalRole.SYSTEM_ADMIN);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).userId()).isEqualTo(FIRST_USER_ID);
        assertThat(results.get(0).cohorts())
                .extracting(cohort -> cohort.cohortName())
                .containsExactly("1기", "2기");
        assertThat(results.get(1).cohorts()).hasSize(1);
    }

    @Test
    @DisplayName("운영 권한이 없는 사용자는 결과에서 제외")
    void omitsUsersWithoutAssignment() {
        when(membershipRepository.findActiveManagerAssignments(anyCollection()))
                .thenReturn(List.of());

        assertThat(managerLookupService.findManagedCohorts(
                List.of(FIRST_USER_ID), GlobalRole.SYSTEM_ADMIN)).isEmpty();
    }

    @Test
    @DisplayName("중복 식별자는 제거 후 조회")
    void deduplicatesUserIds() {
        when(membershipRepository.findActiveManagerAssignments(anyCollection()))
                .thenReturn(List.of());

        managerLookupService.findManagedCohorts(
                List.of(FIRST_USER_ID, FIRST_USER_ID), GlobalRole.SYSTEM_ADMIN);

        verify(membershipRepository).findActiveManagerAssignments(List.of(FIRST_USER_ID));
    }

    @Test
    @DisplayName("전역 관리자가 아니면 조회 전에 거부")
    void rejectsNonSystemAdmin() {
        doThrow(new BusinessException(CohortErrorCode.SYSTEM_ADMIN_REQUIRED))
                .when(accessService).requireSystemAdmin(any());

        assertThatThrownBy(() -> managerLookupService.findManagedCohorts(
                List.of(FIRST_USER_ID), GlobalRole.USER))
                .isInstanceOf(BusinessException.class);
        verify(membershipRepository, never()).findActiveManagerAssignments(anyCollection());
    }

    @Test
    @DisplayName("빈 목록 요청 거부")
    void rejectsEmptyUserIds() {
        assertThatThrownBy(() -> managerLookupService.findManagedCohorts(
                List.of(), GlobalRole.SYSTEM_ADMIN))
                .isInstanceOf(BusinessException.class);
        verify(membershipRepository, never()).findActiveManagerAssignments(anyCollection());
    }

    @Test
    @DisplayName("Bean Validation을 우회한 상한 초과 요청 거부")
    void rejectsTooManyUserIds() {
        List<UUID> userIds = java.util.stream.IntStream
                .rangeClosed(1, CohortManagerLookupService.USER_IDS_MAX + 1)
                .mapToObj(sequence -> new UUID(0L, sequence))
                .toList();

        assertThatThrownBy(() -> managerLookupService.findManagedCohorts(
                userIds, GlobalRole.SYSTEM_ADMIN))
                .isInstanceOf(BusinessException.class);
        verify(membershipRepository, never()).findActiveManagerAssignments(anyCollection());
    }

    private static CohortMembershipRepository.CohortManagerAssignmentProjection assignment(
            UUID userId,
            Long cohortId,
            String cohortName
    ) {
        return new CohortMembershipRepository.CohortManagerAssignmentProjection() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public Long getCohortId() {
                return cohortId;
            }

            @Override
            public String getCohortName() {
                return cohortName;
            }

            @Override
            public CohortMembershipRole getRole() {
                return CohortMembershipRole.MANAGER;
            }
        };
    }
}
