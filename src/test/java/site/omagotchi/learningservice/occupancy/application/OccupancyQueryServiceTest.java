package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 다른 Feature에 노출하는 점유 상태 조회.
 *
 * <p>{@code space} 파트가 공간 목록의 사용 상태를 파생 계산하는 데 쓴다. 이 계약이
 * 자리 잡으면 저쪽이 {@code room_occupancies}를 직접 읽던 엔티티·리포지토리를 지울 수 있다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyQueryServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @InjectMocks
    private OccupancyQueryService occupancyQueryService;

    /** 소비처가 공간 목록과 조인하므로 spaceId로 키잡아 돌려준다. */
    @Test
    @DisplayName("사용 중인 회의실을 공간 식별자로 키잡아 돌려준다.")
    void test1() {
        given(occupancyRepository.findActiveBySpaceIds(List.of(1L, 2L), NOW))
                .willReturn(List.of(new SpaceOccupancyView(2L, NOW.plusHours(1))));

        Map<Long, SpaceOccupancyView> found =
                occupancyQueryService.findActiveBySpaceIds(List.of(1L, 2L), NOW);

        assertThat(found).containsOnlyKeys(2L);
        assertThat(found.get(2L).expiresAt()).isEqualTo(NOW.plusHours(1));
    }

    /** 비어 있는 방은 키가 없다 — 소비처는 {@code null} 여부로 사용 상태를 판단한다. */
    @Test
    @DisplayName("점유가 없는 회의실은 결과에 담기지 않는다.")
    void test2() {
        given(occupancyRepository.findActiveBySpaceIds(List.of(1L), NOW))
                .willReturn(List.of());

        assertThat(occupancyQueryService.findActiveBySpaceIds(List.of(1L), NOW)).isEmpty();
    }

    /**
     * 만료 판정을 리포지토리에 위임하되 기준 시각은 호출부가 정한다. 유니크 인덱스는
     * {@code status}만 보고 {@code expires_at}은 보지 않아 만료된 행이 ACTIVE로 남아 있고,
     * 이 필터가 없으면 목록에는 "사용 중"인데 점유는 성공하는 상태가 보인다.
     */
    @Test
    @DisplayName("만료 판정 기준 시각을 그대로 전달한다.")
    void test3() {
        given(occupancyRepository.findActiveBySpaceIds(List.of(1L), NOW)).willReturn(List.of());

        occupancyQueryService.findActiveBySpaceIds(List.of(1L), NOW);

        verify(occupancyRepository).findActiveBySpaceIds(List.of(1L), NOW);
    }

    /** 공간이 N개여도 쿼리는 1회여야 한다 — 빈 입력에 헛질의를 보내지 않는다. */
    @Test
    @DisplayName("조회할 공간이 없으면 질의하지 않는다.")
    void test4() {
        assertThat(occupancyQueryService.findActiveBySpaceIds(List.of(), NOW)).isEmpty();
        assertThat(occupancyQueryService.findActiveBySpaceIds(null, NOW)).isEmpty();

        verify(occupancyRepository, never()).findActiveBySpaceIds(any(), any());
    }

    @Test
    @DisplayName("기준 시각이 없으면 질의하지 않는다.")
    void test5() {
        assertThat(occupancyQueryService.findActiveBySpaceIds(List.of(1L), null)).isEmpty();

        verify(occupancyRepository, never()).findActiveBySpaceIds(any(), any());
    }
}
