package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CohortMembershipQueryServiceTest {
    @Mock CohortMembershipQuery membershipQuery;
    @InjectMocks CohortMembershipQueryService service;
    private static final Long COHORT_ID = 3L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CohortMembershipView VIEW = new CohortMembershipView(10L, COHORT_ID, USER_ID);

    @Test void delegatesActiveStudentBatchQuery() {
        given(membershipQuery.findActiveStudents(COHORT_ID)).willReturn(List.of(VIEW));
        assertThat(service.findActiveStudentMemberships(COHORT_ID)).containsExactly(VIEW);
    }
    @Test void returnsEmptyWithoutCohortId() { assertThat(service.findActiveStudentMemberships(null)).isEmpty(); verify(membershipQuery, never()).findActiveStudents(null); }
    @Test void delegatesActiveMembershipQueries() {
        given(membershipQuery.findActiveById(10L)).willReturn(Optional.of(VIEW));
        given(membershipQuery.findActive(COHORT_ID, USER_ID)).willReturn(Optional.of(VIEW));
        assertThat(service.findActiveMembership(10L)).contains(VIEW);
        assertThat(service.findActiveMembership(COHORT_ID, USER_ID)).contains(VIEW);
    }
    @Test void delegatesBatchReferenceQueries() {
        given(membershipQuery.findUserIds(List.of(10L))).willReturn(Map.of(10L, USER_ID));
        given(membershipQuery.findCohortIds(List.of(10L))).willReturn(Map.of(10L, COHORT_ID));
        assertThat(service.findUserIds(List.of(10L))).containsEntry(10L, USER_ID);
        assertThat(service.findCohortIds(List.of(10L))).containsEntry(10L, COHORT_ID);
    }
    @Test void skipsEmptyBatchReferenceQueries() {
        assertThat(service.findUserIds(List.of())).isEmpty();
        assertThat(service.findCohortIds(List.of())).isEmpty();
        verify(membershipQuery, never()).findUserIds(List.of());
        verify(membershipQuery, never()).findCohortIds(List.of());
    }
}
