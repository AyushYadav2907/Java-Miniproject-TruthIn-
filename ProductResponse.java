package com.truthscan.dto;

import java.util.List;
import java.util.Map;

/** Everything the product page needs, in one JSON response. */
public record ProductResponse(
        Long id,
        String barcode,
        String name,
        String brand,
        String category,
        String imageUrl,
        String ingredientsText,
        NutritionInfo nutrition,
        Double score,
        String scoreLabel,
        boolean dataComplete,
        Map<String, String> nutrientLevels,
        List<String> scoreReasons,
        List<IngredientFlag> flags,
        List<String> allergens,
        List<String> mayContain,
        List<String> personalWarnings,
        String source,
        String sourceUrl,
        String status
) {
}
