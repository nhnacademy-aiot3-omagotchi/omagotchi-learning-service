package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 접근")
class CohortAccessServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final Long MEMBERSHIP_ID = 100L;
    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortMembershipRepository membershipRepository;

    @InjectMocks
    private CohortAccessService accessService;

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
}
