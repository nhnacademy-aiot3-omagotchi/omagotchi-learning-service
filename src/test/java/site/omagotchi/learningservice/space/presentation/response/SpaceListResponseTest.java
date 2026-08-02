package site.omagotchi.learningservice.space.presentation.response;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.space.application.query.SpaceListItem;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceListResponseTest {

    @Test
    void preservesOperationalAndUsageStatusAndManagementFields() {
        ZonedDateTime expiresAt = ZonedDateTime.of(
                2026, 7, 27, 15, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );
        SpaceListItem item = new SpaceListItem(
                1L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.ACTIVE,
                null,
                11L,
                SpaceUsageStatus.OCCUPIED,
                expiresAt,
                1800L
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
    }
}
