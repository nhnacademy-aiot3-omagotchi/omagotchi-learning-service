package site.omagotchi.learningservice.space.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceOccupancyJpaQueryReaderTest {

    @Mock
    private SpringDataRoomOccupancyRepository repository;

    @InjectMocks
    private SpaceOccupancyJpaQueryReader adapter;

    @Test
    void delegatesExistsQueryWithEquivalentOffsetDateTime() {
        ZonedDateTime now = ZonedDateTime.parse(
                "2026-07-29T10:00:00+09:00[Asia/Seoul]"
        );
        OffsetDateTime offsetNow = now.toOffsetDateTime();
        when(repository.existsActiveBySpaceId(1L, offsetNow))
                .thenReturn(true);

        assertThat(adapter.existsActiveOccupancy(1L, now)).isTrue();
        verify(repository).existsActiveBySpaceId(1L, offsetNow);
    }
}
