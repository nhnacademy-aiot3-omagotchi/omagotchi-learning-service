package site.omagotchi.learningservice.weather.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.weather.domain.RegionGrid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CsvRegionGridResolver {

    private static final String CSV_PATH = "kma-region-grid.csv";

    private final List<RegionGrid> allRegions;

    public CsvRegionGridResolver() {
        this.allRegions = this.loadFromCsv();

        // 극단적으로 모든 줄이 다 실패해서 완전히 빈 리스트가 되는 경우 방어
        if (this.allRegions.isEmpty()) {
            throw new IllegalStateException("지역-격자좌표 CSV에서 유효한 행을 하나도 읽지 못했습니다.");
        }

        log.info("[CsvRegionGridResolver] 지역-격자좌표 매핑 {}건 로드", this.allRegions.size());
    }

    /**
     * CSV 읽어서 리스트로 만들기
     * 컬럼: code, sido, sigungu, eupmyeondong, nx, ny
     * 콤마가 값 안에 안 들어있는 걸 확인해두었음. 단순 split으로 처리해도 됨 (별로 CSV 라이브러리 불필요)
     * <p>
     * CSV 한 줄: "1221000000,전남광주통합특별시,동구,,60,74"
     * -> split(",")
     * -> 배열: ["1221000000", "전남광주통합특별시", "동구", "", "60", "74"]
     * -> RegionGrid("전남광주통합특별시", "동구", "", 60, 74) ← cols[1]~[5]
     */
    private List<RegionGrid> loadFromCsv() {
        List<RegionGrid> result = new ArrayList<>();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {
            bufferedReader.readLine(); // 헤더 스킵

            String line;
            int lineNumber = 1; // 헤더가 1번째 줄
            while ((Objects.nonNull(line = bufferedReader.readLine()))) {
                lineNumber++;

                try {
                    String[] cols = line.split(",", -1);

                    RegionGrid regionGrid = new RegionGrid(
                            cols[1], // sido(시도)
                            cols[2], // sigungu(시군구)
                            cols[3], // eupmyeondong(읍면동)
                            Integer.parseInt(cols[4]), // nx
                            Integer.parseInt(cols[5]) // ny
                    );

                    result.add(regionGrid);
                } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                    // 이 줄 하나가 깨졌다고 전체 기동을 막을 필요 X (로그만 찍고 이 줄만 건너뜀)
                    log.error("[CsvRegionGridResolver] {}번째 줄 파싱 실패, 이 줄은 건너뜀. 내용: {}", lineNumber, line, e);
                }
            }
        } catch (IOException e) {
            // 파일 자체를 못 읽는 상황이라 기동 막아야 함
            throw new IllegalStateException("지역-격자좌표 CSV를 로드할 수 없습니다.", e);
        }

        return result;
    }

    /**
     * 1. 질의를 단어로 쪼갬 - "광주 동구" -> ["광주", "동구"]
     * 2. 키워드별로 "시군구 또는 읍면동에 정확히 일치하는 행이 하나라도 있는지" 미리 확인해서 정확히 일치하는 것만 인정할지, 부분 포함까지 허용할지 정함
     * (예: "동구"는 정확히 일치하는 구가 있어 정확히 일치하는 것만 인정 -> "강동구"/"성동구"/"동구동"은 제외됨
     * "분당구"는 정확히 일치하는 구가 없어 부분 포함까지 허용 -> "성남시분당구"처럼 시+구가 합쳐진 이름도 허용)
     * 3. matchesKeyword로 3838개 중 조건 맞는 것만 거름
     * 4. preferMostGeneralLevel로 정리 (시/도 대표행 우선 -> 구 대표행 우선 -> 동 단위)
     */
    public List<RegionGrid> resolve(String query) {

        if (Objects.isNull(query) || query.isBlank()) {
            log.debug("[CsvRegionGridResolver] query가 null이거나 비어있습니다.");
            return List.of();
        }

        List<String> keywords = Arrays.stream(query.trim().split("\\s+"))
                .filter(k -> !k.isBlank())
                .toList();

        if (keywords.isEmpty()) {
            return List.of();
        }

        // 시군구든 읍면동이든, 어디서든 정확히 일치하는 게 하나라도 있으면 이 키워드는 정확히 일치하는 것만 인정함
        // (부분 포함으로 잘못된 지역이 새어 들어오는 걸 막기 위함)
        Set<String> exactMatchKeywords = keywords.stream()
                .filter(keyword -> this.allRegions.stream()
                        .anyMatch(r -> r.sigungu().equals(keyword) || r.eupmyeondong().equals(keyword)))
                .collect(Collectors.toSet());

        List<RegionGrid> matched = this.allRegions.stream()
                .filter(region -> keywords.stream()
                        .allMatch(keyword -> this.matchesKeyword(region, keyword, exactMatchKeywords.contains(keyword))))
                .toList();

        List<RegionGrid> result = this.preferMostGeneralLevel(matched);

        if (result.isEmpty()) {
            log.info("[CsvRegionGridResolver] 매칭되는 지역 없음: query = {}", query);
        } else if (result.size() > 10) {
            log.info("[CsvRegionGridResolver] 후보가 많음({}개): query = {}", result.size(), query);
        }

        return result;
    }

    /**
     * 한 행이 키워드 하나를 만족하는지 확인
     * sido는 항상 부분 포함으로 판단함 ("광주" -> "전남광주통합특별시" 같은 행정구역 통합 케이스 때문)
     * sigungu/eupmyeondong은 requireExactMatch가 true면 정확히 일치하는지, false면 부분 포함으로 판단함
     */
    private boolean matchesKeyword(RegionGrid regionGrid, String keyword, boolean requireExactMatch) {
        boolean sidoMatch = regionGrid.sido().contains(keyword);
        boolean sigunguMatch = requireExactMatch
                ? regionGrid.sigungu().equals(keyword)
                : regionGrid.sigungu().contains(keyword);
        boolean dongMatch = requireExactMatch
                ? regionGrid.eupmyeondong().equals(keyword)
                : regionGrid.eupmyeondong().contains(keyword);

        return sidoMatch || sigunguMatch || dongMatch;
    }

    // 같은 (시도, 시군구) 안에 대표행(동 없음)이 매칭됐으면 그것만 남기고, 딸린 동 단위 행들은 버림
    // 대표행이 매칭 안 됐다면(쿼리가 특정 동까지 짚었다는 것) 동 단위 행들을 그대로 살림
    private List<RegionGrid> preferDistrictLevel(List<RegionGrid> matched) {

        // "전남광주특별시|동구" 그룹 = [대표행, 충장동, 동명동, ...]
        // query가 "광주 동구"였다면 이 묶음이 통째로 매칭됨
        Map<String, List<RegionGrid>> byDistrict = matched.stream()
                .collect(Collectors.groupingBy(r -> r.sido() + "|" + r.sigungu()));

        List<RegionGrid> result = new ArrayList<>();
        for (List<RegionGrid> group : byDistrict.values()) {
            Optional<RegionGrid> districtLevel = group.stream()
                    .filter(r -> r.eupmyeondong().isBlank())
                    .findFirst();

            if (districtLevel.isPresent()) {
                result.add(districtLevel.get());
            } else {
                result.addAll(group);
            }
        }

        return result;
    }

    private List<RegionGrid> preferMostGeneralLevel(List<RegionGrid> matched) {
        Map<String, List<RegionGrid>> bySido = matched.stream()
                .collect(Collectors.groupingBy(RegionGrid::sido));

        List<RegionGrid> result = new ArrayList<>();
        for (List<RegionGrid> sidoGroup : bySido.values()) {
            Optional<RegionGrid> sidoLevel = sidoGroup.stream()
                    .filter(r -> r.sigungu().isBlank() && r.eupmyeondong().isBlank())
                    .findFirst();

            if (sidoLevel.isPresent()) {
                result.add(sidoLevel.get()); // 시/도 전체 대표행이 있으면 이것만 남기고 끝
                continue;
            }

            result.addAll(preferDistrictLevel(sidoGroup)); // 없으면 기존 구/동 로직 그대로 적용
        }

        return result;
    }
}
