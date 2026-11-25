package service;

import dao.LeagueSeasonResultDAO.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LeagueSeasonTransition {
    private LeagueSeasonTransition() {}

    public record Placement(String name, int level, String reason) {
        public String getName() { return name; }
        public int getLevel() { return level; }
        public String getReason() { return reason; }
    }

    public static List<Placement> plan(List<Result> previous, Map<String, String> available, int divisions) {
        Map<String, Placement> placements = new LinkedHashMap<>();
        Map<Integer, Integer> expected = new HashMap<>();
        int[] counts = new int[divisions + 1];
        for (Result result : previous) {
            int next = result.next();
            expected.merge(next, 1, Integer::sum);
            String name = available.get(result.key());
            if (name == null) continue;
            String reason = next < result.level() ? "Lên hạng trực tiếp"
                    : next > result.level() ? "Xuống hạng" : "";
            placements.put(result.key(), new Placement(name, next, reason));
            counts[next]++;
        }
        for (int level = 1; level < divisions; level++) {
            int vacancies = expected.getOrDefault(level, 0) - counts[level];
            if (vacancies <= 0) continue;
            for (Result candidate : previous) {
                if (candidate.level() != level + 1) continue;
                Placement current = placements.get(candidate.key());
                if (current == null || current.level() != level + 1) continue;
                placements.put(candidate.key(), new Placement(current.name(), level, "Đôn hạng"));
                counts[level]++;
                counts[level + 1]--;
                if (--vacancies == 0) break;
            }
        }
        available.forEach((key, name) -> placements.putIfAbsent(key, new Placement(name, divisions, "Đội mới")));
        List<Placement> result = new ArrayList<>(placements.values());
        result.sort(java.util.Comparator.comparingInt(Placement::level).thenComparing(Placement::name));
        return result;
    }
}
