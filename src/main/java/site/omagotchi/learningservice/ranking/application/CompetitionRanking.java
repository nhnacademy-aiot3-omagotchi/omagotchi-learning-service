package site.omagotchi.learningservice.ranking.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CompetitionRanking {

    // 양수 점수를 내림차순으로 정렬하고 동점 다음 순위를 건너뛰는 competition ranking을 계산한다.
    static <T> List<Ranked<T>> rank(
            Collection<T> values,
            ToLongFunction<T> score,
            Comparator<T> stableOrder
    ) {
        List<Scored<T>> sortedValues = values.stream()
                .map(value -> new Scored<>(value, score.applyAsLong(value)))
                .filter(value -> value.score() > 0L)
                .sorted((left, right) -> compare(left, right, stableOrder))
                .toList();

        List<Ranked<T>> rankedValues = new ArrayList<>(sortedValues.size());
        Long previousScore = null;
        long rank = 0L;
        for (int index = 0; index < sortedValues.size(); index++) {
            Scored<T> value = sortedValues.get(index);
            if (!Long.valueOf(value.score()).equals(previousScore)) {
                rank = index + 1L;
                previousScore = value.score();
            }
            rankedValues.add(new Ranked<>(rank, value.value()));
        }
        return List.copyOf(rankedValues);
    }

    // 점수 내림차순을 우선하고 동점일 때 전달받은 안정 정렬 기준을 적용한다.
    private static <T> int compare(
            Scored<T> left,
            Scored<T> right,
            Comparator<T> stableOrder
    ) {
        int scoreOrder = Long.compare(right.score(), left.score());
        return scoreOrder != 0
                ? scoreOrder
                : stableOrder.compare(left.value(), right.value());
    }

    record Ranked<T>(long rank, T value) {
    }

    private record Scored<T>(T value, long score) {
    }
}
