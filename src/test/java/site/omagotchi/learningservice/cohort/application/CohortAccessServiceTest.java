package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 접근")
class CohortAccessServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final Long MEMBERSHIP_ID = 100L;
    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortRepository cohortRepository;

    @InjectMocks
    private CohortAccessService accessService;

    @Nested
    @DisplayName("시스템 관리자 확인")
    class RequireSystemAdmin {

        @Test
        @DisplayName("SYSTEM_ADMIN 허용")
        void acceptsSystemAdmin() {
            // When & Then
            assertDoesNotThrow(() ->
                    accessService.requireSystemAdmin(GlobalRole.SYSTEM_ADMIN)
            );
        }

        @Test
        @DisplayName("USER 거부")
        void rejectsUser() {
            // When
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> accessService.requireSystemAdmin(GlobalRole.USER)
            );

            // Then
            assertSame(CohortErrorCode.SYSTEM_ADMIN_REQUIRED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("활성 기수 소속 식별자 확인")
    class RequireActiveMembershipId {

        @Test
        @DisplayName("활성 소속 식별자 반환")
        void returnsMembershipIdWhenActiveMembershipExists() {
            when(membershipRepository.findActiveMembershipId(USER_ID, COHORT_ID))
                    .thenReturn(Optional.of(MEMBERSHIP_ID));

            Long result = accessService.requireActiveMembershipId(COHORT_ID, USER_ID);

            assertEquals(MEMBERSHIP_ID, result);
        }

        @Test
        @DisplayName("활성 소속 없음 예외")
        void throwsExceptionWhenActiveMembershipDoesNotExist() {
            when(membershipRepository.findActiveMembershipId(USER_ID, COHORT_ID))
                    .thenReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> accessService.requireActiveMembershipId(COHORT_ID, USER_ID)
            );

            assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("기수 매니저 확인")
    class IsManager {

        @Test
        @DisplayName("MANAGER 역할의 활성 소속이면 true")
        void returnsTrueForActiveManager() {
            when(membershipRepository.existsByCohortIdAndUserIdAndRoleAndStatus(
                    COHORT_ID,
                    USER_ID,
                    CohortMembershipRole.MANAGER,
                    CohortMembershipStatus.ACTIVE
            )).thenReturn(true);

            assertTrue(accessService.isManager(COHORT_ID, USER_ID));
        }

        @Test
        @DisplayName("매니저가 아니면 예외 대신 false — 호출자가 자기 오류 Code를 고른다")
        void returnsFalseInsteadOfThrowing() {
            when(membershipRepository.existsByCohortIdAndUserIdAndRoleAndStatus(
                    COHORT_ID,
                    USER_ID,
                    CohortMembershipRole.MANAGER,
                    CohortMembershipStatus.ACTIVE
            )).thenReturn(false);

            assertFalse(accessService.isManager(COHORT_ID, USER_ID));
        }

        @Test
        @DisplayName("소속 자체가 없어도 예외 없이 false — 404와 403을 구분하지 않는다")
        void doesNotDistinguishMissingMembership() {
            when(membershipRepository.existsByCohortIdAndUserIdAndRoleAndStatus(
                    COHORT_ID,
                    USER_ID,
                    CohortMembershipRole.MANAGER,
                    CohortMembershipStatus.ACTIVE
            )).thenReturn(false);

            assertFalse(accessService.isManager(COHORT_ID, USER_ID));
            verify(membershipRepository, never())
                    .findActiveMembershipId(USER_ID, COHORT_ID);
        }
    }

    @Nested
    @DisplayName("활성 학생 소속 식별자 확인")
    class RequireActiveStudentMembershipId {

        @Test
        @DisplayName("종료되지 않은 활성 학생의 소속 식별자 반환")
        void returnsMembershipIdForActiveStudent() {
            CohortMembership membership = mock(CohortMembership.class);
            when(membership.getId()).thenReturn(MEMBERSHIP_ID);
            when(membership.getRole()).thenReturn(CohortMembershipRole.STUDENT);
            when(membershipRepository
                    .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                            COHORT_ID,
                            USER_ID,
                            CohortMembershipStatus.ACTIVE
                    )).thenReturn(Optional.of(membership));

            Long result = accessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID);

            assertEquals(MEMBERSHIP_ID, result);
        }

        @Test
        @DisplayName("활성 소속이 학생이 아니면 접근 거부")
        void rejectsNonStudentMembership() {
            CohortMembership membership = mock(CohortMembership.class);
            when(membership.getRole()).thenReturn(CohortMembershipRole.MANAGER);
            when(membershipRepository
                    .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                            COHORT_ID,
                            USER_ID,
                            CohortMembershipStatus.ACTIVE
                    )).thenReturn(Optional.of(membership));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> accessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID)
            );

            assertSame(CohortErrorCode.COHORT_ACCESS_DENIED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("활성 기수 조회")
    class FindActiveCohorts {

        @Test
        @DisplayName("종료되지 않은 활성 관리자 소속의 활성 기수만 반환")
        void returnsOnlyActiveManagedCohorts() {
            Cohort activeCohort = cohort(1L);
            CohortMembership activeManager = membership(
                    1L,
                    CohortMembershipRole.MANAGER,
                    null
            );
            CohortMembership endedManager = membership(
                    1L,
                    CohortMembershipRole.MANAGER,
                    OffsetDateTime.now()
            );
            CohortMembership inactiveCohortManager = membership(
                    2L,
                    CohortMembershipRole.MANAGER,
                    null
            );

            when(cohortRepository.findByStatus(CohortStatus.ACTIVE))
                    .thenReturn(List.of(activeCohort));
            when(membershipRepository
                    .findByUserIdOrderByRequestedAtDesc(USER_ID))
                    .thenReturn(List.of(
                            endedManager,
                            inactiveCohortManager,
                            activeManager
                    ));

            assertEquals(
                    List.of(1L),
                    accessService.findActiveManagedCohortIds(USER_ID)
            );
        }

        @Test
        @DisplayName("역할과 무관하게 종료되지 않은 활성 소속 기수를 반환")
        void returnsActiveCohortsForEveryMembershipRole() {
            Cohort firstCohort = cohort(1L);
            Cohort secondCohort = cohort(2L);
            CohortMembership manager = membership(
                    1L,
                    null,
                    null
            );
            CohortMembership student = membership(
                    2L,
                    null,
                    null
            );

            when(cohortRepository.findByStatus(CohortStatus.ACTIVE))
                    .thenReturn(List.of(firstCohort, secondCohort));
            when(membershipRepository
                    .findByUserIdOrderByRequestedAtDesc(USER_ID))
                    .thenReturn(List.of(student, manager));

            assertEquals(
                    List.of(2L, 1L),
                    accessService.findActiveCohortIds(USER_ID)
            );
        }

        private Cohort cohort(Long cohortId) {
            Cohort cohort = mock(Cohort.class);
            when(cohort.getId()).thenReturn(cohortId);
            return cohort;
        }

        private CohortMembership membership(
                Long cohortId,
                CohortMembershipRole role,
            OffsetDateTime endedAt
        ) {
            CohortMembership membership = mock(CohortMembership.class);
            if (endedAt == null) {
                when(membership.getCohortId()).thenReturn(cohortId);
            }
            if (role != null) {
                when(membership.getRole()).thenReturn(role);
            }
            when(membership.getStatus())
                    .thenReturn(CohortMembershipStatus.ACTIVE);
            when(membership.getEndedAt()).thenReturn(endedAt);
            return membership;
        }
    }
}
