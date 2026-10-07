package com.truthscan.service;

import com.truthscan.dto.HealthProfile;
import com.truthscan.dto.IngredientFlag;
import com.truthscan.dto.ScoreResult;
import com.truthscan.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns nutrition values and ingredient flags into a 1-5 health rating.
 *
 * Thresholds follow the widely used "traffic light" front-of-pack levels
 * (per 100 g for foods, per 100 ml for drinks). Point weights are a simple
 * starting model: tune them with a nutritionist and publish your method
 * on the About page so ratings are transparent.
 */
@Service
public class ScoringService {

    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    /** low = upper bound for LOW, high = values above this are HIGH. */
    private record Threshold(double low, double high) {
        String level(double value) {
            if (value <= low) return LOW;
            if (value > high) return HIGH;
            return MEDIUM;
        }
    }

    private static final Map<String, Threshold> FOOD = Map.of(
            "sugar", new Threshold(5.0, 22.5),
            "fat", new Threshold(3.0, 17.5),
            "saturatedFat", new Threshold(1.5, 5.0),
            "salt", new Threshold(0.3, 1.5));

    private static final Map<String, Threshold> DRINK = Map.of(
            "sugar", new Threshold(2.5, 11.25),
            "fat", new Threshold(1.5, 8.75),
            "saturatedFat", new Threshold(0.75, 2.5),
            "salt", new Threshold(0.3, 0.75));

    public ScoreResult calculate(Product p, List<IngredientFlag> flags) {
        boolean drink = isDrink(p);
        Map<String, Threshold> t = drink ? DRINK : FOOD;

        Map<String, String> levels = new LinkedHashMap<>();
        putLevel(levels, "sugar", p.getSugar(), t);
        putLevel(levels, "fat", p.getFat(), t);
        putLevel(levels, "saturatedFat", p.getSaturatedFat(), t);
        putLevel(levels, "salt", p.getSalt(), t);

        List<String> missing = new ArrayList<>();
        if (p.getSugar() == null) missing.add("sugar");
        if (p.getSaturatedFat() == null) missing.add("saturated fat");
        if (p.getSalt() == null) missing.add("salt");

        if (!missing.isEmpty()) {
            return new ScoreResult(null, "Not enough data", false, levels,
                    List.of("Missing nutrition values: " + String.join(", ", missing)
                            + ", so we can't rate it yet."));
        }

        double score = 5.0;
        List<String> reasons = new ArrayList<>();

        // Sugar weighs more in drinks: liquid sugar is easy to over-consume
        score -= drink
                ? penalty(levels.get("sugar"), 2.5, 2.0, "sugar", reasons)
                : penalty(levels.get("sugar"), 1.5, 0.5, "sugar", reasons);
        if (drink && p.getSugar() > 5) {
            score -= 1.0;
            reasons.add("Sugary drink (" + fmt(p.getSugar()) + " g sugar per 100 ml)");
        }
        score -= penalty(levels.get("saturatedFat"), 1.0, 0.5, "saturated fat", reasons);
        score -= penalty(levels.get("salt"), 1.0, 0.5, "salt", reasons);
        score -= penalty(levels.get("fat"), 0.5, 0.0, "total fat", reasons);

        // Extra penalty when a nutrient is more than double the "high" limit
        // (e.g. instant noodles with 3 g salt per 100 g)
        if (!drink) score -= veryHigh(p.getSugar(), t.get("sugar"), "sugar", reasons);
        score -= veryHigh(p.getSaturatedFat(), t.get("saturatedFat"), "saturated fat", reasons);
        score -= veryHigh(p.getSalt(), t.get("salt"), "salt", reasons);

        if (!drink && p.getEnergyKcal() != null && p.getEnergyKcal() > 500) {
            score -= 1.0;
            reasons.add("Very energy-dense (" + kcal(p.getEnergyKcal()) + " kcal per 100 g)");
        } else if (!drink && p.getEnergyKcal() != null && p.getEnergyKcal() > 400) {
            score -= 0.5;
            reasons.add("Energy-dense (" + kcal(p.getEnergyKcal()) + " kcal per 100 g)");
        }

        double flagPenalty = 0;
        for (IngredientFlag f : flags) {
            // Added sugar is already counted through the sugar value above
            if (IngredientAnalyzer.ADDED_SUGAR.equals(f.category())) continue;
            if (HIGH.equals(f.severity())) flagPenalty += 0.5;
            else if (MEDIUM.equals(f.severity())) flagPenalty += 0.25;
        }
        flagPenalty = Math.min(flagPenalty, 1.5);
        if (flagPenalty > 0) {
            score -= flagPenalty;
            reasons.add("Ingredients of concern (see list below)");
        }

        // Like Nutri-Score: fibre/protein can't rescue a product that is high in
        // two or more of sugar, saturated fat and salt
        long highCount = java.util.stream.Stream.of("sugar", "saturatedFat", "salt")
                .filter(k -> HIGH.equals(levels.get(k))).count();
        boolean bonusAllowed = !drink && highCount < 2;

        if (bonusAllowed && p.getFiber() != null && p.getFiber() >= 6) {
            score += 0.5;
            reasons.add("High in fibre");
        } else if (bonusAllowed && p.getFiber() != null && p.getFiber() >= 3) {
            score += 0.25;
            reasons.add("Source of fibre");
        }
        if (bonusAllowed && p.getProtein() != null && p.getProtein() >= 10) {
            score += 0.25;
            reasons.add("Good source of protein");
        }

        score = Math.max(1.0, Math.min(5.0, score));
        score = Math.round(score * 2) / 2.0;   // nearest 0.5

        if (reasons.isEmpty()) {
            reasons.add("Low in sugar, saturated fat and salt");
        }
        return new ScoreResult(score, label(score), true, levels, reasons);
    }

    /** Warnings based on the visitor's own health profile. */
    public List<String> personalWarnings(Product p, HealthProfile profile,
                                         List<IngredientFlag> flags, List<String> allergens,
                                         List<String> traces, Map<String, String> levels) {
        List<String> warnings = new ArrayList<>();
        String per = isDrink(p) ? "per 100 ml" : "per 100 g";

        for (String allergen : allergens) {
            if (profile.allergies().contains(allergen)) {
                warnings.add("Contains " + allergen + ", which is in your allergy list.");
            }
        }
        for (String allergen : traces) {
            if (profile.allergies().contains(allergen)) {
                warnings.add("May contain traces of " + allergen + " (in your allergy list).");
            }
        }
        if (!profile.allergies().isEmpty() && isBlank(p.getIngredientsText())) {
            warnings.add("Ingredient list is missing. Check the pack for allergens before eating.");
        }

        if (profile.has("diabetes")) {
            if (p.getSugar() != null && p.getSugar() > 5) {
                warnings.add("Diabetes: " + fmt(p.getSugar()) + " g sugar " + per + ".");
            }
            if (hasCategory(flags, IngredientAnalyzer.ADDED_SUGAR)) {
                warnings.add("Diabetes: contains added sugars.");
            }
            if (hasCategory(flags, IngredientAnalyzer.REFINED_FLOUR)) {
                warnings.add("Diabetes: refined flour can spike blood sugar.");
            }
        }
        if (profile.has("hypertension") && isAtLeastMedium(levels.get("salt"))) {
            warnings.add("Blood pressure: " + fmt(p.getSalt()) + " g salt " + per + ".");
        }
        if (profile.has("heart")) {
            if (isAtLeastMedium(levels.get("saturatedFat"))) {
                warnings.add("Heart health: " + fmt(p.getSaturatedFat()) + " g saturated fat " + per + ".");
            }
            if (hasCategory(flags, IngredientAnalyzer.TRANS_FAT)) {
                warnings.add("Heart health: may contain trans fats.");
            }
        }
        if (profile.has("weight_loss")) {
            if (p.getEnergyKcal() != null && p.getEnergyKcal() > 350) {
                warnings.add("Weight loss: " + kcal(p.getEnergyKcal()) + " kcal " + per + " is high.");
            }
            if (HIGH.equals(levels.get("sugar")) || HIGH.equals(levels.get("fat"))) {
                warnings.add("Weight loss: high in sugar or fat.");
            }
        }
        if (profile.has("pregnancy")) {
            if (hasCategory(flags, IngredientAnalyzer.CAFFEINE)) {
                warnings.add("Pregnancy: contains caffeine. Keep total daily intake low.");
            }
            if (hasCategory(flags, IngredientAnalyzer.SWEETENER)) {
                warnings.add("Pregnancy: contains artificial sweeteners. Ask your doctor.");
            }
        }
        if (profile.has("kids")) {
            if (hasCategory(flags, IngredientAnalyzer.COLOUR)) {
                warnings.add("Children: contains synthetic colours.");
            }
            if (hasCategory(flags, IngredientAnalyzer.CAFFEINE)) {
                warnings.add("Children: contains caffeine.");
            }
        }
        return warnings;
    }

    // ---------- helpers ----------

    static boolean isDrink(Product p) {
        String c = p.getCategory() == null ? "" : p.getCategory().toLowerCase(Locale.ROOT);
        return c.contains("beverage") || c.contains("drink") || c.contains("juice")
                || c.contains("soda") || c.contains("cola");
    }

    private static void putLevel(Map<String, String> levels, String key, Double value, Map<String, Threshold> t) {
        if (value != null) {
            levels.put(key, t.get(key).level(value));
        }
    }

    private static double penalty(String level, double high, double medium, String what, List<String> reasons) {
        if (HIGH.equals(level)) {
            reasons.add("High in " + what);
            return high;
        }
        if (MEDIUM.equals(level) && medium > 0) {
            reasons.add("Moderate " + what);
            return medium;
        }
        return 0;
    }

    private static double veryHigh(Double value, Threshold t, String what, List<String> reasons) {
        if (value != null && value > t.high() * 2) {
            reasons.add("Very high in " + what);
            return 0.5;
        }
        return 0;
    }

    private static String kcal(Double v) {
        return v == null ? "?" : String.valueOf(Math.round(v));
    }

    private static String label(double score) {
        if (score >= 4.5) return "Excellent";
        if (score >= 3.5) return "Good";
        if (score >= 2.5) return "Okay in moderation";
        if (score >= 1.5) return "Poor";
        return "Limit";
    }

    private static boolean hasCategory(List<IngredientFlag> flags, String category) {
        return flags.stream().anyMatch(f -> category.equals(f.category()));
    }

    private static boolean isAtLeastMedium(String level) {
        return MEDIUM.equals(level) || HIGH.equals(level);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String fmt(Double v) {
        if (v == null) return "?";
        return v == Math.floor(v) ? String.valueOf(v.longValue()) : String.format(Locale.ROOT, "%.1f", v);
    }
}
