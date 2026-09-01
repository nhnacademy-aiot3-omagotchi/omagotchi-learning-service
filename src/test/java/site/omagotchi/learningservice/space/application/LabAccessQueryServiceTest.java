package site.omagotchi.learningservice.space.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabAccessQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "019d2a48-80c0-4d6a-9a15-0b16d2dd74f2"
    );

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpacePresenceQueryService spacePresenceQueryService;

    @InjectMocks
    private LabAccessQueryService service;

    @Test
    @DisplayName("자기 기수의 활성 실습실과 현재·복귀 예약 인원을 모두 반환한다")
    void returnsEveryActiveLabWithCurrentAndReturnReservations() {
        Space first = activeLab(10L, "실습실 A", 20);
        Space second = activeLab(20L, "실습실 B", 30);
        when(spaceRepository.findActiveLabsByCohortId(42L))
                .thenReturn(List.of(first, second));
        when(spacePresenceQueryService.summarize(List.of(10L, 20L)))
                .thenReturn(Map.of(
                        10L, new SpacePresenceSummary(3L, 2L),
                        20L, new SpacePresenceSummary(1L, 0L)
                ));

        var result = service.findSelectableLabs(42L, USER_ID);

        assertThat(result).extracting("spaceId", "name", "capacity", "reservedCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, "실습실 A", 20, 5L),
                        org.assertj.core.groups.Tuple.tuple(20L, "실습실 B", 30, 1L)
                );
        verify(cohortAccessService).requireActiveStudentMembershipId(42L, USER_ID);
    }

    @Test
    @DisplayName("활성 실습실이 없으면 체류 집계 없이 빈 목록을 반환한다")
    void returnsEmptyListWithoutRunningPresenceQueryWhenNoLabExists() {
        when(spaceRepository.findActiveLabsByCohortId(42L)).thenReturn(List.of());

        assertThat(service.findSelectableLabs(42L, USER_ID)).isEmpty();

        verify(cohortAccessService).requireActiveStudentMembershipId(42L, USER_ID);
    }

    private Space activeLab(Long id, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return Space.restore(
                id,
                42L,
                name,
                SpaceType.LAB,
                capacity,
                SpaceOperationalStatus.ACTIVE,
                null,
                now.minusDays(1),
                now,
                null
        );
    }
}
