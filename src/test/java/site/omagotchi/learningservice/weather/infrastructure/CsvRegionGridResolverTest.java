package site.omagotchi.learningservice.weather.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.weather.domain.RegionGrid;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvRegionGridResolverTest {

    private CsvRegionGridResolver resolver;

    @BeforeEach
    void setUp() {
        this.resolver = new CsvRegionGridResolver();
    }

    @Test
    @DisplayName("'동구'만 물으면 전국에 동구가 여러 곳이라 전부 후보로 나온다 (모호함)")
    void plainDongguReturnsAllFiveCandidates() {
        List<RegionGrid> result = this.resolver.resolve("동구");

        assertThat(result)
                .hasSize(5)
                .extracting(RegionGrid::sido).containsExactlyInAnyOrder("전남광주통합특별시", "부산광역시", "대구광역시", "대전광역시", "울산광역시");

        assertThat(result)
                .allSatisfy(r -> assertThat(r.eupmyeondong()).isEmpty());
    }

    @Test
    @DisplayName("'동구'는 '강동구'처럼 접미어만 같은 구를 오탐하지 않는다")
    void plainDongguDoesNotMatchGangdonggu() {
        List<RegionGrid> result = this.resolver.resolve("동구");

        assertThat(result)
                .extracting(RegionGrid::sigungu)
                .doesNotContain("강동구");
    }

    @Test
    @DisplayName("'강동구'를 물으면 정확히 그 구 하나만 나온다")
    void gangdongguExactMatch() {
        List<RegionGrid> result = this.resolver.resolve("강동구");

        assertThat(result).hasSize(1);

        RegionGrid region = result.getFirst();

        assertThat(region.sido()).isEqualTo("서울특별시");
        assertThat(region.sigungu()).isEqualTo("강동구");
        assertThat(region.nx()).isEqualTo(62);
        assertThat(region.ny()).isEqualTo(126);
    }

    @Test
    @DisplayName("'광주 동구'처럼 시/도 + 구를 같이 물으면 그 구 하나로 좁혀진다")
    void cityPlusDistrictNarrowsToOne() {
        List<RegionGrid> result = this.resolver.resolve("광주 동구");

        assertThat(result).hasSize(1);
        RegionGrid region = result.getFirst();
        assertThat(region.sido()).isEqualTo("전남광주통합특별시");
        assertThat(region.sigungu()).isEqualTo("동구");
        assertThat(region.eupmyeondong()).isEmpty();
        assertThat(region.nx()).isEqualTo(60);
        assertThat(region.ny()).isEqualTo(74);
    }

    @Test
    @DisplayName("동 이름만 물어도 그 동 하나로 찾아진다")
    void dongNameAloneResolves() {
        List<RegionGrid> result = this.resolver.resolve("충장동");

        assertThat(result).hasSize(1);
        RegionGrid region = result.getFirst();
        assertThat(region.sigungu()).isEqualTo("동구");
        assertThat(region.eupmyeondong()).isEqualTo("충장동");
        assertThat(region.nx()).isEqualTo(59);
        assertThat(region.ny()).isEqualTo(74);
    }

    @Test
    @DisplayName("'광주 동구 충장동'처럼 시/도+구+동을 전부 물어도 동 단위 결과가 사라지지 않는다")
    void fullHierarchyKeepsDongLevel() {
        List<RegionGrid> result = this.resolver.resolve("광주 동구 충장동");

        assertThat(result).hasSize(1);
        RegionGrid region = result.getFirst();
        assertThat(region.eupmyeondong()).isEqualTo("충장동");
        assertThat(region.nx()).isEqualTo(59);
        assertThat(region.ny()).isEqualTo(74);
    }

    @Test
    @DisplayName("'분당구'처럼 시+구가 합쳐진 이름은 정확히 일치하는 게 없어도 부분 포함으로 찾아진다")
    void compoundDistrictNameFallsBackToSubstring() {
        List<RegionGrid> result = this.resolver.resolve("분당구");

        assertThat(result).hasSize(1);
        RegionGrid region = result.getFirst();
        assertThat(region.sido()).isEqualTo("경기도");
        assertThat(region.sigungu()).isEqualTo("성남시분당구");
        assertThat(region.eupmyeondong()).isEmpty();
        assertThat(region.nx()).isEqualTo(62);
        assertThat(region.ny()).isEqualTo(123);
    }

    @Test
    @DisplayName("시/도 이름만 물으면 그 시/도 대표행 하나로 좁혀진다")
    void sidoOnlyNarrowsToSidoRepresentative() {
        List<RegionGrid> result = this.resolver.resolve("서울");

        assertThat(result).hasSize(1);
        RegionGrid region = result.getFirst();
        assertThat(region.sido()).isEqualTo("서울특별시");
        assertThat(region.sigungu()).isEmpty();
        assertThat(region.eupmyeondong()).isEmpty();
        assertThat(region.nx()).isEqualTo(60);
        assertThat(region.ny()).isEqualTo(127);
    }

    @Test
    @DisplayName("존재하지 않는 지역명은 빈 리스트를 반환한다")
    void unknownRegionReturnsEmpty() {
        List<RegionGrid> result = this.resolver.resolve("존재하지않는가상의지역이름");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 또는 빈 문자열 질의는 빈 리스트를 반환한다")
    void blankQueryReturnsEmpty() {
        assertThat(this.resolver.resolve(null)).isEmpty();
        assertThat(this.resolver.resolve("")).isEmpty();
        assertThat(this.resolver.resolve("   ")).isEmpty();
    }

    @Test
    @DisplayName("존재하는 시/도 + 존재하지 않는 구를 같이 물으면 결과가 없다")
    void validCityWithInvalidDistrictReturnsEmpty() {
        List<RegionGrid> result = this.resolver.resolve("광주 존재하지않는구");

        assertThat(result).isEmpty();
    }
}
