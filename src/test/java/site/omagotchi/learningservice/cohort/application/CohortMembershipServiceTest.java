package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.command.ApproveMembershipCommand;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.application.port.JoinCodePersistence;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

@ExtendWith(MockitoExtension.class)
class CohortMembershipServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");
    private static final UUID MEMBER_USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private JoinCodePersistence joinCodePersistence;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @Mock
    private CohortEventPublisher eventPublisher;

    @InjectMocks
    private CohortMembershipService membershipService;

    @Test
    @DisplayName("멘토 승인 시 시스템 관리자가 아닌 기수 관리자 권한 확인")
    void approveMentorRequiresCohortManagerNotSystemAdmin() {
        Long cohortId = 1L;
        Long membershipId = 100L;
        UUID managerUserId = MANAGER_USER_ID;
        CohortMembership pending = CohortMembership.pending(
                cohortId,
                MEMBER_USER_ID,
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(pending, "id", membershipId);

        when(membershipRepository.findByIdAndStatus(membershipId, CohortMembershipStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(preparingCohort(cohortId)));
        when(membershipRepository.approvePending(
                org.mockito.ArgumentMatchers.eq(membershipId),
                org.mockito.ArgumentMatchers.eq(CohortMembershipStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(CohortMembershipRole.MENTOR),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(managerUserId)
        )).thenReturn(1);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        membershipService.approve(
                membershipId,
                new ApproveMembershipCommand(CohortMembershipRole.MENTOR),
                managerUserId,
                GlobalRole.USER
        );

        verify(accessService).requireManager(cohortId, managerUserId);
        verify(accessService, never()).requireSystemAdmin(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 종료 사실을 팀·점유에 알리는 것이 이 Use Case의 절반이다. 발행하지 않으면 그
     * 사람의 팀 소속과 활성 점유가 그대로 남는다 (GR-16, MR-26).
     */
    @Test
    @DisplayName("소속을 종료하면 종료 이벤트를 발행한다")
    void endPublishesMembershipEndedEvent() {
        Long cohortId = 1L;
        Long membershipId = 100L;
        CohortMembership active = activeMembership(cohortId, membershipId);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(active));
        when(membershipRepository.endActive(
                org.mockito.ArgumentMatchers.eq(membershipId),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);

        assertThat(membershipService.end(membershipId)).isTrue();

        ArgumentCaptor<CohortMembershipEndedEvent> captor =
                ArgumentCaptor.forClass(CohortMembershipEndedEvent.class);
        verify(eventPublisher).publishMembershipEnded(captor.capture());

        CohortMembershipEndedEvent published = captor.getValue();
        assertThat(published.membershipId()).isEqualTo(membershipId);
        assertThat(published.cohortId()).isEqualTo(cohortId);
        assertThat(published.userId()).isEqualTo(MEMBER_USER_ID);
        assertThat(published.endedAt()).isNotNull();
    }

    /**
     * <b>재전달이 전제인 진입점이다.</b> 계정 삭제 훅은 재시도될 수 있고, 두 번째 도착에
     * 예외를 던지면 훅이 실패로 기록되어 무한 재시도에 빠진다. 이벤트도 다시 내면 안 된다 —
     * 이미 정리된 팀·점유에 같은 정리가 또 흐른다.
     */
    @Test
    @DisplayName("이미 종료된 소속은 조용히 넘어가고 이벤트를 다시 내지 않는다")
    void endIsIdempotentForAlreadyEndedMembership() {
        Long membershipId = 100L;
        CohortMembership active = activeMembership(1L, membershipId);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(active));
        when(membershipRepository.endActive(
                org.mockito.ArgumentMatchers.eq(membershipId),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        assertThat(membershipService.end(membershipId)).isFalse();

        verify(eventPublisher, never()).publishMembershipEnded(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("없는 소속을 종료하려 하면 404로 끊는다")
    void endRejectsUnknownMembership() {
        when(membershipRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.end(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND);

        verify(membershipRepository, never()).endActive(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private CohortMembership activeMembership(Long cohortId, Long membershipId) {
        CohortMembership membership =
                CohortMembership.activeManager(cohortId, MEMBER_USER_ID, MANAGER_USER_ID);
        ReflectionTestUtils.setField(membership, "id", membershipId);
        return membership;
    }

    private Cohort preparingCohort(Long cohortId) {
        Cohort cohort = Cohort.create(
                "AIOT 3",
                "test cohort",
                AggregationDateTime.today(),
                AggregationDateTime.today().plusDays(30),
                MANAGER_USER_ID
        );
        ReflectionTestUtils.setField(cohort, "id", cohortId);
        return cohort;
    }
}
