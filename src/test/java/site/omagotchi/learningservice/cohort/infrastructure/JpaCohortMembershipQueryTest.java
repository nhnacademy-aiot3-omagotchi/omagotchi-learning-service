package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 소속 공개 조회 어댑터")
class JpaCohortMembershipQueryTest {

    @Mock
    private CohortMembershipRepository repository;

    @InjectMocks
    private JpaCohortMembershipQuery query;

    @Test
    @DisplayName("종료 소속 조회는 PENDING과 REJECTED를 제외하고 실제 종료 시각을 반환한다")
    void findsOnlyEndedMembershipsWithActualEndTime() {
        OffsetDateTime endedAt = OffsetDateTime.parse("2026-09-04T09:00:00Z");
        CohortMembership pending = membership(1L, CohortMembershipStatus.PENDING, null);
        CohortMembership rejected = membership(2L, CohortMembershipStatus.REJECTED, null);
        CohortMembership ended = membership(3L, CohortMembershipStatus.ENDED, endedAt);
        when(repository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(pending, rejected, ended));

        assertThat(query.findEndedMemberships(List.of(1L, 2L, 3L)))
                .containsOnlyKeys(3L)
                .containsEntry(3L, endedAt);
    }

    private CohortMembership membership(
            Long membershipId,
            CohortMembershipStatus status,
            OffsetDateTime endedAt
    ) {
        CohortMembership membership = CohortMembership.pending(
                10L,
                UUID.randomUUID(),
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(membership, "id", membershipId);
        ReflectionTestUtils.setField(membership, "status", status);
        ReflectionTestUtils.setField(membership, "endedAt", endedAt);
        return membership;
    }
}
