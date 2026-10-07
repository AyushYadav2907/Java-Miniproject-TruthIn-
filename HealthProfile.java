package com.truthscan.dto;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The visitor's health profile, sent by the browser as query parameters, e.g.
 * {@code ?conditions=diabetes,hypertension&allergies=milk,peanuts}.
 * Nothing about the visitor is stored on the server.
 */
public record HealthProfile(Set<String> conditions, Set<String> allergies) {

    public static final HealthProfile NONE = new HealthProfile(Set.of(), Set.of());

    public static HealthProfile parse(String conditions, String allergies) {
        return new HealthProfile(split(conditions), split(allergies));
    }

    public boolean has(String condition) {
        return conditions.contains(condition);
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
