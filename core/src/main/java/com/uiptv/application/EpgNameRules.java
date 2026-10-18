package com.uiptv.application;

import java.util.*;
import java.util.regex.Pattern;

/** Immutable snapshot of literal database rules, shared by every match in one batch. */
public final class EpgNameRules {
    public record Rule(String term, String scope) { }
    private final List<Rule> ordered;
    public List<Rule> rules() { return ordered; }
    private final Pattern annotations;
    private final Pattern qualities;
    private final String fingerprint;

    public EpgNameRules(List<Rule> rules) {
        ordered = rules.stream().distinct().sorted(Comparator.comparing(Rule::term).thenComparing(Rule::scope)).toList();
        String annotationTerms = alternatives(ordered, "annotation");
        String qualityTerms = alternatives(ordered, "quality");
        annotations=Pattern.compile("\\(\\s*(?:"+annotationTerms+")\\s*\\)|\\[\\s*(?:"+annotationTerms+")\\s*\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        qualities=Pattern.compile("(?<![\\p{L}\\p{N}])(?:"+qualityTerms+")(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        fingerprint = EpgCacheApplicationService.hash(ordered.stream().map(r -> r.scope() + ":" + r.term().length() + ":" + r.term()).collect(java.util.stream.Collectors.joining("\n")));
    }
    private static String alternatives(List<Rule> rules,String scope) {
        return rules.stream().filter(r -> r.scope().equals(scope)).map(Rule::term)
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .map(Pattern::quote).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.joining("|"), s -> s.isEmpty() ? "(?!)" : s));
    }
    public String fingerprint() { return fingerprint; }
    public String display(String value) {
        String result = XmltvGuide.normalize(value);
        result = annotations.matcher(result).replaceAll(" ");
        return XmltvGuide.normalize(result);
    }
    public String quality(String value) {
        if (value != null && value.strip().startsWith("###")) return ""; // Playlist section headings are not stations.
        String result = display(value);
        result = qualities.matcher(result).replaceAll(" ");
        // Circled availability markers are presentation, whereas + marks a time-shift station.
        return result.replaceAll("[Ⓐ-Ⓩⓐ-ⓩ]", "").replaceAll("[^\\p{L}\\p{N}+]", "");
    }
    public Map<String,String> aliases(Map<String,String> names, boolean quality) {
        Map<String,String> result = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        names.forEach((name,id) -> {
            String key = quality ? quality(name) : display(name);
            String previous = result.putIfAbsent(key,id);
            if (previous != null && !previous.equals(id)) ambiguous.add(key);
        });
        ambiguous.forEach(result::remove); result.remove(""); return Map.copyOf(result);
    }
}
