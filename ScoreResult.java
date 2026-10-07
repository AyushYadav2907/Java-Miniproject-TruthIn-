package com.truthscan.dto;

import java.util.List;
import java.util.Map;

/**
 * Output of the scoring engine.
 *
 * @param score          1.0 - 5.0 in steps of 0.5, or null when there is not enough data
 * @param label          short description of the score
 * @param dataComplete   false when key nutrition values are missing
 * @param nutrientLevels "sugar" -> "LOW" / "MEDIUM" / "HIGH" etc.
 * @param reasons        why the score went up or down
 */
public record ScoreResult(
        Double score,
        String label,
        boolean dataComplete,
        Map<String, String> nutrientLevels,
        List<String> reasons
) {
}
