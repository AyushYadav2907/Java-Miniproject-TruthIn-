package com.truthscan.dto;

/** Nutrition values per 100 g / 100 ml. Any value may be null when unknown. */
public record NutritionInfo(
        Double energyKcal,
        Double sugar,
        Double fat,
        Double saturatedFat,
        Double salt,
        Double fiber,
        Double protein
) {
}
