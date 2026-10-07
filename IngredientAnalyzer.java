package com.truthscan.service;

import com.truthscan.dto.IngredientFlag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an ingredient list and finds additives, hidden sugars, refined flour,
 * trans-fat sources and allergens.
 *
 * The lists below are a starting point. Review them with a nutritionist and
 * extend them (or move them into a database table) as your product data grows.
 */
@Component
public class IngredientAnalyzer {

    // Category names, also used by ScoringService for personalised warnings
    public static final String ADDED_SUGAR = "Added sugar";
    public static final String REFINED_FLOUR = "Refined flour";
    public static final String TRANS_FAT = "Trans fat risk";
    public static final String PALM_OIL = "Palm oil";
    public static final String SWEETENER = "Sweetener";
    public static final String COLOUR = "Colour";
    public static final String PRESERVATIVE = "Preservative";
    public static final String FLAVOUR_ENHANCER = "Flavour enhancer";
    public static final String CAFFEINE = "Caffeine";
    public static final String ARTIFICIAL_FLAVOUR = "Artificial flavour";
    public static final String EMULSIFIER = "Emulsifier / thickener";
    public static final String ANTIOXIDANT = "Antioxidant";

    private record Additive(String name, String category, String severity, String reason) {
    }

    /** INS / E-number additives worth pointing out. Key = code without prefix, lower case. */
    private static final Map<String, Additive> ADDITIVES = new LinkedHashMap<>();

    static {
        String colourReason = "Synthetic colour; linked to hyperactivity in some children.";
        ADDITIVES.put("102", new Additive("Tartrazine", COLOUR, "MEDIUM", colourReason));
        ADDITIVES.put("110", new Additive("Sunset Yellow FCF", COLOUR, "MEDIUM", colourReason));
        ADDITIVES.put("122", new Additive("Carmoisine", COLOUR, "MEDIUM", colourReason));
        ADDITIVES.put("124", new Additive("Ponceau 4R", COLOUR, "MEDIUM", colourReason));
        ADDITIVES.put("129", new Additive("Allura Red", COLOUR, "MEDIUM", colourReason));
        ADDITIVES.put("133", new Additive("Brilliant Blue FCF", COLOUR, "LOW", "Synthetic colour."));
        ADDITIVES.put("150c", new Additive("Ammonia caramel", COLOUR, "LOW", "Caramel colour made with ammonia."));
        ADDITIVES.put("150d", new Additive("Sulphite ammonia caramel", COLOUR, "LOW",
                "Caramel colour common in colas."));

        ADDITIVES.put("211", new Additive("Sodium benzoate", PRESERVATIVE, "MEDIUM",
                "Preservative; can form small amounts of benzene with vitamin C in drinks."));
        ADDITIVES.put("220", new Additive("Sulphur dioxide", PRESERVATIVE, "MEDIUM",
                "Can trigger reactions in people with asthma or sulphite sensitivity."));
        ADDITIVES.put("223", new Additive("Sodium metabisulphite", PRESERVATIVE, "MEDIUM",
                "Can trigger reactions in people with asthma or sulphite sensitivity."));
        ADDITIVES.put("250", new Additive("Sodium nitrite", PRESERVATIVE, "HIGH",
                "Used in processed meats; regular intake is linked to higher cancer risk."));
        ADDITIVES.put("251", new Additive("Sodium nitrate", PRESERVATIVE, "HIGH",
                "Used in processed meats; regular intake is linked to higher cancer risk."));

        ADDITIVES.put("319", new Additive("TBHQ", ANTIOXIDANT, "MEDIUM", "Synthetic antioxidant; keep intake low."));
        ADDITIVES.put("320", new Additive("BHA", ANTIOXIDANT, "HIGH",
                "Classified as possibly carcinogenic to humans (IARC Group 2B)."));
        ADDITIVES.put("321", new Additive("BHT", ANTIOXIDANT, "MEDIUM", "Synthetic antioxidant; keep intake low."));

        ADDITIVES.put("407", new Additive("Carrageenan", EMULSIFIER, "LOW",
                "Thickener; may upset digestion in sensitive people."));
        ADDITIVES.put("433", new Additive("Polysorbate 80", EMULSIFIER, "LOW",
                "Emulsifier; early research suggests it may affect gut health."));
        ADDITIVES.put("471", new Additive("Mono- and diglycerides of fatty acids", EMULSIFIER, "LOW",
                "Emulsifier typical of ultra-processed foods."));

        String enhancerReason = "Flavour enhancer; adds sodium and is typical of ultra-processed snacks.";
        ADDITIVES.put("621", new Additive("Monosodium glutamate (MSG)", FLAVOUR_ENHANCER, "LOW", enhancerReason));
        ADDITIVES.put("627", new Additive("Disodium guanylate", FLAVOUR_ENHANCER, "LOW", enhancerReason));
        ADDITIVES.put("631", new Additive("Disodium inosinate", FLAVOUR_ENHANCER, "LOW", enhancerReason));
        ADDITIVES.put("635", new Additive("Disodium 5'-ribonucleotides", FLAVOUR_ENHANCER, "LOW", enhancerReason));

        ADDITIVES.put("950", new Additive("Acesulfame potassium", SWEETENER, "MEDIUM", "Artificial sweetener."));
        ADDITIVES.put("951", new Additive("Aspartame", SWEETENER, "MEDIUM",
                "Artificial sweetener; IARC 'possibly carcinogenic' (2B); not suitable for people with PKU."));
        ADDITIVES.put("954", new Additive("Saccharin", SWEETENER, "MEDIUM", "Artificial sweetener."));
        ADDITIVES.put("955", new Additive("Sucralose", SWEETENER, "MEDIUM", "Artificial sweetener."));
    }

    /** Longer phrases first, so "glucose syrup" is reported once rather than also as "glucose". */
    private static final List<String> SUGAR_WORDS = List.of(
            "high fructose corn syrup", "corn syrup", "glucose syrup", "liquid glucose",
            "invert sugar", "invert syrup", "brown sugar", "cane sugar", "malt extract",
            "maltodextrin", "dextrose", "sucrose", "fructose", "jaggery", "honey", "glucose", "sugar");

    private static final List<String> REFINED_FLOUR_WORDS = List.of("refined wheat flour", "maida");
    private static final List<String> TRANS_FAT_WORDS = List.of("hydrogenated", "vanaspati", "interesterified");
    private static final List<String> PALM_WORDS = List.of("palm oil", "palmolein", "palm olein", "palm fat");
    private static final List<String> CAFFEINE_WORDS = List.of("caffeine");
    private static final List<String> ARTIFICIAL_FLAVOUR_WORDS = List.of(
            "artificial flavouring", "artificial flavoring", "artificial flavour", "artificial flavor",
            "nature identical flavouring", "nature identical flavoring", "nature-identical flavouring");

    /** Where a label's "may contain traces" statement starts. */
    private static final Pattern TRACES_START = Pattern.compile("\\bmay (?:also )?contain\\b|\\btraces? of\\b");

    /** Allergen name -> words that indicate it. Allergen names are what the profile page uses. */
    private static final Map<String, List<String>> ALLERGEN_WORDS = new LinkedHashMap<>();

    static {
        ALLERGEN_WORDS.put("milk", List.of("milk", "milk solids", "whey", "casein", "butter", "ghee",
                "cheese", "cream", "lactose", "curd", "paneer", "khoa", "yogurt", "yoghurt"));
        ALLERGEN_WORDS.put("peanuts", List.of("peanut", "groundnut"));
        ALLERGEN_WORDS.put("tree nuts", List.of("almond", "cashew", "walnut", "pistachio", "hazelnut",
                "pecan", "macadamia"));
        ALLERGEN_WORDS.put("gluten", List.of("wheat", "maida", "atta", "barley", "rye", "semolina",
                "sooji", "suji", "gluten"));
        ALLERGEN_WORDS.put("soy", List.of("soy", "soya", "soybean"));
        ALLERGEN_WORDS.put("egg", List.of("egg"));
        ALLERGEN_WORDS.put("sesame", List.of("sesame", "til"));
        ALLERGEN_WORDS.put("fish", List.of("fish"));
        ALLERGEN_WORDS.put("crustaceans", List.of("shrimp", "prawn", "crab", "lobster"));
        ALLERGEN_WORDS.put("mustard", List.of("mustard"));
    }

    /** Open Food Facts allergen tags -> our allergen names. */
    private static final Map<String, String> OFF_ALLERGEN_NAMES = Map.of(
            "milk", "milk",
            "peanuts", "peanuts",
            "nuts", "tree nuts",
            "gluten", "gluten",
            "soybeans", "soy",
            "eggs", "egg",
            "sesame-seeds", "sesame",
            "fish", "fish",
            "crustaceans", "crustaceans",
            "mustard", "mustard");

    /** Phrases containing "butter" that are not dairy. */
    private static final List<String> NON_DAIRY_BUTTERS = List.of(
            "cocoa butter", "peanut butter", "nut butter", "shea butter", "kokum butter", "mango butter");

    /**
     * Additive codes written as "INS 211", "E211", "E-211" or in lists "(330, 211)", "(508 & 412)".
     * Numbers followed by a unit like "(250 ml)" or "(100%)" are ignored.
     */
    private static final Pattern ADDITIVE_CODE = Pattern.compile(
            "(?:\\b(?:ins|e)\\s*-?\\s*|[(,&/]\\s*|\\bor\\s+)(\\d{3,4}[a-d]?)\\b(?!\\s*(?:%|g\\b|mg|mcg|ml|kg|kcal|kj))");

    /** Finds everything worth flagging in the ingredients. */
    public List<IngredientFlag> analyze(String ingredientsText, String additivesCsv) {
        String text = normalise(ingredientsText);
        List<IngredientFlag> flags = new ArrayList<>();

        List<String> sugars = findWords(text, SUGAR_WORDS);
        if (!sugars.isEmpty()) {
            flags.add(new IngredientFlag(String.join(", ", sugars), ADDED_SUGAR, "MEDIUM",
                    "Contains added sugar" + (sugars.size() > 1 ? " in " + sugars.size() + " forms" : "") + "."));
        }
        if (!findWords(text, REFINED_FLOUR_WORDS).isEmpty()) {
            flags.add(new IngredientFlag("Refined wheat flour (maida)", REFINED_FLOUR, "MEDIUM",
                    "Low in fibre and raises blood sugar quickly."));
        }
        if (!findWords(text, TRANS_FAT_WORDS).isEmpty()) {
            flags.add(new IngredientFlag("Hydrogenated / interesterified fat", TRANS_FAT, "HIGH",
                    "May contain trans fats, which raise the risk of heart disease."));
        }
        if (!findWords(text, PALM_WORDS).isEmpty()) {
            flags.add(new IngredientFlag("Palm oil", PALM_OIL, "LOW", "High in saturated fat."));
        }
        if (!findWords(text, CAFFEINE_WORDS).isEmpty()) {
            flags.add(new IngredientFlag("Caffeine", CAFFEINE, "LOW",
                    "Not recommended for children; limit during pregnancy."));
        }
        if (!findWords(text, ARTIFICIAL_FLAVOUR_WORDS).isEmpty()) {
            flags.add(new IngredientFlag("Artificial / nature-identical flavouring", ARTIFICIAL_FLAVOUR, "LOW",
                    "Added flavour, typical of highly processed foods."));
        }

        for (String code : findAdditiveCodes(text, additivesCsv)) {
            Additive a = lookupAdditive(code);
            if (a != null) {
                flags.add(new IngredientFlag(a.name() + " (INS " + code + ")", a.category(), a.severity(), a.reason()));
            }
        }
        return flags;
    }

    /**
     * Allergens the product contains: found in the ingredient text (before any
     * "may contain" statement) plus known allergens reported by the data source.
     */
    public List<String> detectAllergens(String ingredientsText, String allergensCsv) {
        String[] parts = splitTraces(normalise(ingredientsText));
        Set<String> found = new LinkedHashSet<>(allergensIn(parts[0]));

        if (allergensCsv != null) {
            for (String raw : allergensCsv.split(",")) {
                String tag = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]{2}:", "");
                // Only accept real allergen names (crowd-sourced tags sometimes hold other text)
                String name = OFF_ALLERGEN_NAMES.containsKey(tag) ? OFF_ALLERGEN_NAMES.get(tag)
                        : ALLERGEN_WORDS.containsKey(tag) ? tag : null;
                if (name != null) {
                    found.add(name);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Allergens listed after "may contain" / "traces of" (cross-contamination warnings). */
    public List<String> detectTraces(String ingredientsText, List<String> containsAllergens) {
        String[] parts = splitTraces(normalise(ingredientsText));
        List<String> traces = new ArrayList<>(allergensIn(parts[1]));
        traces.removeAll(containsAllergens);
        return traces;
    }

    /** [text before "may contain", text after it] */
    private static String[] splitTraces(String text) {
        Matcher m = TRACES_START.matcher(text);
        return m.find()
                ? new String[] {text.substring(0, m.start()), text.substring(m.start())}
                : new String[] {text, ""};
    }

    private static Set<String> allergensIn(String text) {
        Set<String> found = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : ALLERGEN_WORDS.entrySet()) {
            String searchText = text;
            if (entry.getKey().equals("milk")) {
                for (String phrase : NON_DAIRY_BUTTERS) {
                    searchText = searchText.replace(phrase, " ");
                }
            }
            if (!findWords(searchText, entry.getValue()).isEmpty()) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    // ---------- helpers ----------

    private static String normalise(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Returns the words from the list that appear as whole words (plurals allowed). */
    private static List<String> findWords(String text, List<String> words) {
        List<String> found = new ArrayList<>();
        if (text.isEmpty()) {
            return found;
        }
        String remaining = text;
        for (String word : words) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "(?:s|es)?\\b");
            Matcher m = p.matcher(remaining);
            if (m.find()) {
                found.add(word);
                // Blank out this phrase so "glucose syrup" isn't also counted as "glucose",
                // while a separate plain "sugar" elsewhere in the list is still found.
                remaining = m.replaceAll(" ");
            }
        }
        return found;
    }

    private static Set<String> findAdditiveCodes(String text, String additivesCsv) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = ADDITIVE_CODE.matcher(text);
        while (m.find()) {
            codes.add(m.group(1));
        }
        if (additivesCsv != null) {
            for (String raw : additivesCsv.split(",")) {
                // Open Food Facts style "en:e150d" or plain "e211" / "211"
                String code = raw.trim().toLowerCase(Locale.ROOT)
                        .replaceFirst("^[a-z]{2}:", "")
                        .replaceFirst("^(?:e|ins)\\s*-?", "");
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    private static Additive lookupAdditive(String code) {
        Additive a = ADDITIVES.get(code);
        if (a == null) {
            // "322i" -> "322", "150d" stays as listed
            String base = code.replaceAll("[^0-9].*$", "");
            a = ADDITIVES.get(base);
        }
        return a;
    }
}
