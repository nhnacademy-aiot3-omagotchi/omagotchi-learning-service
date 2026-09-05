package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 잠금 공개 경계")
class CohortLockServiceTest {

    private static final Long MEMBERSHIP_ID = 10L;

    @Mock
    private CohortPersistence cohortPersistence;

    @Mock
    private CohortMembershipRepository cohortMembershipRepository;

    @InjectMocks
    private CohortLockService service;

    @Test
    @DisplayName("소속 행을 잠근 뒤 ACTIVE 상태일 때만 공개한다")
    void locksMembershipBeforeCheckingActiveStatus() {
        CohortMembership membership = CohortMembership.activeManager(
                3L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        when(cohortMembershipRepository.findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ACTIVE
        ))
                .thenReturn(Optional.of(membership));

        assertThat(service.lockActiveMembership(MEMBERSHIP_ID))
                .hasValueSatisfying(view -> {
                    assertThat(view.membershipId()).isEqualTo(MEMBERSHIP_ID);
                    assertThat(view.cohortId()).isEqualTo(3L);
                });

        verify(cohortMembershipRepository).findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("ACTIVE 조건의 잠금 조회에서 대상이 없으면 빈 결과를 반환한다")
    void returnsEmptyWithoutActiveMembership() {
        when(cohortMembershipRepository.findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThat(service.lockActiveMembership(MEMBERSHIP_ID)).isEmpty();
    }

    @Test
    @DisplayName("종료 정리는 ENDED 조건으로 소속 행을 잠근다")
    void locksEndedMembershipForCleanup() {
        CohortMembership membership = CohortMembership.activeManager(
                3L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        ReflectionTestUtils.setField(
                membership,
                "status",
                CohortMembershipStatus.ENDED
        );
        when(cohortMembershipRepository.findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ENDED
        )).thenReturn(Optional.of(membership));

        assertThat(service.lockEndedMembership(MEMBERSHIP_ID)).isPresent();

        verify(cohortMembershipRepository).findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ENDED
        );
    }

    @Test
    @DisplayName("유효하지 않은 소속 ID는 DB 잠금을 시도하지 않는다")
    void rejectsInvalidMembershipIdWithoutQuery() {
        assertThat(service.lockEndedMembership(null)).isEmpty();
        assertThat(service.lockActiveMembership(0L)).isEmpty();

        verify(cohortMembershipRepository, never())
                .findWithLockByIdAndStatus(null, CohortMembershipStatus.ENDED);
        verify(cohortMembershipRepository, never())
                .findWithLockByIdAndStatus(0L, CohortMembershipStatus.ACTIVE);
    }
}
