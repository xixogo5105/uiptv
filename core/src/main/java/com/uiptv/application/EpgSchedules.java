package com.uiptv.application;

import java.util.*;

/** Resolve overlaps within one source, without combining schedules from different sources. */
public final class EpgSchedules {
    private EpgSchedules() { }
    private static boolean generic(String title) { return Set.of("movie", "film", "programme", "program").contains(title.strip().toLowerCase(Locale.ROOT)); }
    public static List<EpgProgramme> normalize(List<EpgProgramme> input) {
        var priority = Comparator.comparing((EpgProgramme p) -> generic(p.title()))
                .thenComparing(Comparator.comparingLong(EpgProgramme::start).reversed())
                .thenComparing(Comparator.comparingLong(EpgProgramme::stop).reversed())
                .thenComparing(EpgProgramme::title).thenComparing(EpgProgramme::description);
        Map<Long, List<EpgProgramme>> starts = new HashMap<>(), stops = new HashMap<>();
        TreeSet<Long> times = new TreeSet<>();
        for (EpgProgramme p : input.stream().filter(p -> p.stop() > p.start()).distinct().toList()) {
            starts.computeIfAbsent(p.start(), _ -> new ArrayList<>()).add(p);
            stops.computeIfAbsent(p.stop(), _ -> new ArrayList<>()).add(p);
            times.add(p.start()); times.add(p.stop());
        }
        var boundaries = new ArrayList<>(times);
        TreeSet<EpgProgramme> active = new TreeSet<>(priority);
        List<EpgProgramme> result = new ArrayList<>();
        EpgProgramme previousWinner = null;
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            long start = boundaries.get(i), stop = boundaries.get(i + 1);
            active.removeAll(stops.getOrDefault(start, List.of()));
            active.addAll(starts.getOrDefault(start, List.of()));
            if (active.isEmpty()) { previousWinner = null; continue; }
            EpgProgramme winner = active.first();
            if (winner.equals(previousWinner) && result.getLast().stop() == start) {
                EpgProgramme previous = result.getLast();
                result.set(result.size() - 1, new EpgProgramme(previous.title(), previous.description(), previous.start(), stop));
            } else result.add(new EpgProgramme(winner.title(), winner.description(), start, stop));
            previousWinner = winner;
        }
        return List.copyOf(result);
    }
}
