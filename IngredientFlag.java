package com.truthscan.dto;

/**
 * Something noteworthy found in the ingredient list.
 *
 * @param name     what was found, e.g. "Sodium benzoate (INS 211)"
 * @param category e.g. "Additive", "Added sugar", "Sweetener"
 * @param severity "HIGH", "MEDIUM" or "LOW"
 * @param reason   plain-language explanation shown to the user
 */
public record IngredientFlag(String name, String category, String severity, String reason) {
}
