package com.uiptv.application;

import java.util.*;

/** Resolve overlaps within one source, without combining schedules from different sources. */
public final class EpgSchedules {
    private EpgSchedules() { }
    private static final Set<String> GENERIC_TITLES = Set.of(
            "movie", "movies", "film", "films", "cinema", "drama", "series", "tv series", "episode",
            "programme", "program", "programming", "tv programme", "tv program", "show", "tv show",
            "entertainment", "general entertainment", "music", "sports", "sport", "news", "live", "live tv",
            "documentary", "comedy", "cartoon", "children", "kids", "religious", "teleshopping", "shopping",
            "action", "romance", "thriller", "adventure", "horror", "family", "suspense", "musical", "serial", "serials",
            "reality", "reality show", "lifestyle", "travel", "cooking", "animation", "soap", "soap opera", "talk show",
            "infomercial", "paid programming", "cricket", "football", "tennis", "hockey", "basketball", "baseball", "rugby",
            "motor sport", "motorsport", "sports programme", "religious programme", "news bulletin", "news update",
            "tba", "tbc", "t b a", "t b c", "to be announced", "to be confirmed", "no details", "no details available",
            "no programme details", "no programme details available", "no program details", "no program details available",
            "programme details unavailable", "program details unavailable",
            "no programme guide available", "no guide available", "no guide data available", "guide source unavailable",
            "no information", "no information available", "no info", "no data", "no data available",
            "no programme information", "no program information", "programme information unavailable",
            "no programme information available", "no program information available",
            "program information unavailable", "schedule unavailable", "not available", "unknown",
            "película", "pelicula", "películas", "filme", "filmes", "émission", "emission", "programm",
            "фильм", "программа", "фільм", "програма", "फिल्म", "फ़िल्म", "कार्यक्रम", "فلم", "پروگرام",
            "电影", "電影", "节目", "節目", "映画", "番組", "영화", "프로그램");
    private static final java.util.regex.Pattern GENERIC_SEPARATORS=java.util.regex.Pattern.compile("[\\s\\p{Punct}]+");
    public static boolean isGenericTitle(String title) {
        if (title == null || title.isBlank()) return true;
        String normalized=GENERIC_SEPARATORS.matcher(title.strip().toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return GENERIC_TITLES.contains(normalized);
    }
    private static boolean generic(String title) { return isGenericTitle(title); }
    /** Source order wins unless its entry is generic. Resolve each time interval independently. */
    public static List<EpgProgramme> preferSources(List<List<EpgProgramme>> sources) {
        record Candidate(EpgProgramme programme,int source) { }
        var priority=Comparator.comparing((Candidate c) -> generic(c.programme().title())).thenComparingInt(Candidate::source)
                .thenComparing(Comparator.comparingLong((Candidate c) -> c.programme().start()).reversed())
                .thenComparingLong(c -> c.programme().stop()).thenComparing(c -> c.programme().title()).thenComparing(c -> c.programme().description());
        Map<Long,List<Candidate>> starts = new HashMap<>();
        Map<Long,List<Candidate>> stops = new HashMap<>();
        TreeSet<Long> boundaries = new TreeSet<>();
        for(int source=0;source<sources.size();source++) for(EpgProgramme p:sources.get(source)) {
            if(p.stop()<=p.start()) continue;
            Candidate candidate=new Candidate(p,source);
            starts.computeIfAbsent(p.start(),_ -> new ArrayList<>()).add(candidate);
            stops.computeIfAbsent(p.stop(),_ -> new ArrayList<>()).add(candidate);
            boundaries.add(p.start()); boundaries.add(p.stop());
        }
        var times = new ArrayList<>(boundaries);
        var active = new TreeSet<>(priority);
        List<EpgProgramme> result = new ArrayList<>();
        Candidate previous = null;
        for(int i=0;i+1<times.size();i++) {
            long start = times.get(i);
            long stop = times.get(i + 1);
            active.removeAll(stops.getOrDefault(start,List.of())); active.addAll(starts.getOrDefault(start,List.of()));
            if(active.isEmpty()) { previous=null; continue; }
            Candidate winner=active.first();
            EpgProgramme p=winner.programme();
            if(winner.equals(previous) && !result.isEmpty() && result.getLast().stop()==start) {
                EpgProgramme last=result.getLast(); result.set(result.size()-1,new EpgProgramme(last.title(),last.description(),last.start(),stop));
            } else result.add(new EpgProgramme(p.title(),p.description(),start,stop));
            previous=winner;
        }
        return List.copyOf(result);
    }

    public static List<EpgProgramme> normalize(List<EpgProgramme> input) {
        var priority = Comparator.comparing((EpgProgramme p) -> generic(p.title()))
                .thenComparing(Comparator.comparingLong(EpgProgramme::start).reversed())
                .thenComparing(Comparator.comparingLong(EpgProgramme::stop).reversed())
                .thenComparing(EpgProgramme::title).thenComparing(EpgProgramme::description);
        Map<Long, List<EpgProgramme>> starts = new HashMap<>();
        Map<Long, List<EpgProgramme>> stops = new HashMap<>();
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
            long start = boundaries.get(i);
            long stop = boundaries.get(i + 1);
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
