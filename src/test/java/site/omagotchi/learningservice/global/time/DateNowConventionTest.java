package site.omagotchi.learningservice.global.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "오늘"의 기준이 두 개가 되는 것을 막는 규약 테스트다.
 *
 * <p>출결·학습 기록은 {@link AggregationDateTime}의 KST 04:00 집계일을 키로 저장한다.
 * 그런데 {@code LocalDate.now()}는 JVM 기본 타임존을 따르므로, 서버나 CI가 UTC면
 * KST 00:00~04:00 구간에서 날짜가 하루 어긋난다. 실제로 이 어긋남 때문에 출결 통합
 * 테스트가 "기록 0건"으로 깨진 적이 있다.</p>
 *
 * <p>기준을 하나로 유지하기 위해 no-arg {@code now()} 직접 호출을 전면 금지하고,
 * "오늘"이 필요하면 {@link AggregationDateTime#today()}를 쓰게 한다. 실행 시각에
 * 의존하는 검증이 아니라 소스를 훑는 검증이므로 언제 돌려도 결과가 같다.</p>
 */
@DisplayName("날짜 기준 규약")
class DateNowConventionTest {

    private static final List<Path> SCAN_ROOTS = List.of(
            Path.of("src", "main", "java"),
            Path.of("src", "test", "java")
    );

    private static final List<String> BANNED = List.of(
            "LocalDate.now()",
            "LocalDateTime.now()",
            "LocalTime.now()"
    );

    /**
     * 규약 자체를 다루는 파일이라 예외로 둔다.
     *
     * <p>{@code AggregationDateTime}은 유일하게 허용된 진입점이고, 이 테스트는 금지어를
     * 문자열로 들고 있어서 스스로를 위반으로 집는다.</p>
     */
    private static final List<String> ALLOWED_FILES = List.of(
            "AggregationDateTime.java",
            "DateNowConventionTest.java"
    );

    @Test
    @DisplayName("LocalDate.now() 계열을 직접 부르지 않는다")
    void doesNotCallDateNowDirectly() {
        List<String> violations = new ArrayList<>();

        for (Path root : SCAN_ROOTS) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !ALLOWED_FILES.contains(path.getFileName().toString()))
                        .forEach(path -> collectViolations(path, violations));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        assertTrue(
                violations.isEmpty(),
                """
                JVM 기본 타임존에 의존하는 날짜 호출이 있습니다. \
                AggregationDateTime.today() 또는 today(clock)을 쓰십시오.
                """ + String.join("\n", violations)
        );
    }

    private static void collectViolations(Path path, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (isComment(line)) {
                continue;
            }
            for (String banned : BANNED) {
                if (line.contains(banned)) {
                    violations.add("  %s:%d  %s".formatted(path, index + 1, line.trim()));
                }
            }
        }
    }

    // 주석에 예시로 적힌 경우까지 잡으면 규약을 설명할 수 없게 된다.
    private static boolean isComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
