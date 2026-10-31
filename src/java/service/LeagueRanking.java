package service;

import java.util.*;
import java.util.function.*;

/** Shared RR order: points, difference, head-to-head points, scored, alphabet. */
public final class LeagueRanking {
    private LeagueRanking() {}
    public static void addHeadToHead(Map<String, Map<String,Integer>> results, String a, String b, int points) {
        results.computeIfAbsent(a, key -> new HashMap<>()).merge(b, points, Integer::sum);
    }
    public static <T> void sort(List<T> rows, Function<T,String> id, Function<T,String> name,
            ToIntFunction<T> points, ToIntFunction<T> difference, ToIntFunction<T> scored,
            Map<String,Map<String,Integer>> results) {
        // A tied mini-table avoids non-transitive pairwise comparisons in a three-way tie.
        Map<String,Integer> h2h = new HashMap<>();
        for (T row : rows) {
            int total = 0;
            Map<String,Integer> against = results.getOrDefault(id.apply(row), Collections.emptyMap());
            for (T opponent : rows) {
                if (points.applyAsInt(row) == points.applyAsInt(opponent)
                        && difference.applyAsInt(row) == difference.applyAsInt(opponent))
                    total += against.getOrDefault(id.apply(opponent), 0);
            }
            h2h.put(id.apply(row), total);
        }
        rows.sort(Comparator.<T>comparingInt(points).reversed()
                .thenComparing(Comparator.comparingInt(difference).reversed())
                .thenComparing(Comparator.<T>comparingInt(row -> h2h.get(id.apply(row))).reversed())
                .thenComparing(Comparator.comparingInt(scored).reversed())
                .thenComparing(row -> name.apply(row) == null ? "" : name.apply(row).trim().toLowerCase(Locale.ROOT))
                .thenComparing(id));
    }
}
