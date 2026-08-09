package site.omagotchi.learningservice.space.presentation.response;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceListResponseTest {

    @Test
    void preservesOperationalAndUsageStatusAndManagementFields() {
        ZonedDateTime expiresAt = ZonedDateTime.of(
                2026, 7, 27, 15, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );
        UUID occupierUserId = UUID.randomUUID();
        UUID participantUserId = UUID.randomUUID();
        SpaceListResult item = new SpaceListResult(
                1L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.ACTIVE,
                null,
                11L,
                SpaceUsageStatus.OCCUPIED,
                expiresAt,
                1800L,
                true,
                21L,
                31L,
                occupierUserId,
                List.of(participantUserId)
        );

        SpaceListResponse response = SpaceListResponse.from(item);

        assertThat(response.type()).isEqualTo(SpaceType.MEETING);
        assertThat(response.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(response.status())
                .isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertThat(response.cohortId()).isEqualTo(11L);
        assertThat(response.occupancyExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(response.occupiedBySameCohort()).isTrue();
        assertThat(response.occupancyCohortId()).isEqualTo(21L);
        assertThat(response.occupierMembershipId()).isEqualTo(31L);
        assertThat(response.occupierUserId()).isEqualTo(occupierUserId);
        assertThat(response.participantUserIds())
                .containsExactly(participantUserId);
    }
}
