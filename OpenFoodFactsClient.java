package com.truthscan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.truthscan.model.Product;
import com.truthscan.model.ProductSource;
import com.truthscan.model.ProductStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Looks up products in the free Open Food Facts database.
 * Their data is licensed under the Open Database License (ODbL):
 * credit them on your site (see about.html) and keep the User-Agent set.
 */
@Service
public class OpenFoodFactsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsClient.class);

    private static final String FIELDS = String.join(",",
            "product_name", "product_name_en", "brands", "categories",
            "ingredients_text", "ingredients_text_en", "nutriments",
            "additives_tags", "allergens_tags", "image_front_url", "image_url");

    private final RestClient client;

    public OpenFoodFactsClient(@Value("${app.off.base-url}") String baseUrl,
                               @Value("${app.off.user-agent}") String userAgent,
                               @Value("${app.off.timeout-ms:6000}") long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    /** Returns the product, or empty if Open Food Facts doesn't have it or can't be reached. */
    public Optional<Product> fetch(String barcode) {
        try {
            JsonNode body = client.get()
                    .uri("/product/{code}?fields={fields}", barcode, FIELDS)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || body.path("status").asInt() != 1) {
                return Optional.empty();
            }
            return Optional.of(toProduct(barcode, body.path("product")));

        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Open Food Facts lookup failed for {}: {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    Product toProduct(String barcode, JsonNode json) {
        Product p = new Product();
        p.setBarcode(barcode);

        String name = firstNonBlank(text(json, "product_name_en"), text(json, "product_name"));
        p.setName(limit(name == null ? "Unknown product" : name, 255));
        p.setBrand(limit(text(json, "brands"), 255));
        p.setCategory(limit(text(json, "categories"), 255));
        p.setIngredientsText(limit(
                firstNonBlank(text(json, "ingredients_text_en"), text(json, "ingredients_text")), 5000));
        p.setAdditives(limit(joinArray(json.path("additives_tags")), 1000));
        p.setAllergens(limit(joinArray(json.path("allergens_tags")), 1000));
        p.setImageUrl(limit(firstNonBlank(text(json, "image_front_url"), text(json, "image_url")), 1000));

        JsonNode n = json.path("nutriments");
        p.setEnergyKcal(energyKcal(number(n, "energy-kcal_100g"), number(n, "energy_100g")));
        p.setSugar(number(n, "sugars_100g"));
        p.setFat(number(n, "fat_100g"));
        p.setSaturatedFat(number(n, "saturated-fat_100g"));
        p.setFiber(number(n, "fiber_100g"));
        p.setProtein(number(n, "proteins_100g"));

        Double salt = number(n, "salt_100g");
        if (salt == null) {
            Double sodium = number(n, "sodium_100g");      // salt = sodium x 2.5
            salt = sodium == null ? null : Math.round(sodium * 2.5 * 1000) / 1000.0;
        }
        p.setSalt(salt);

        p.setSource(ProductSource.OPEN_FOOD_FACTS);
        p.setStatus(ProductStatus.APPROVED);
        return p;
    }

    /**
     * Crowd-sourced data sometimes has a kcal value that doesn't match the kJ value
     * (e.g. per-serving kcal typed into the per-100 g field). energy_100g is in kJ.
     * If the two disagree by more than 25 %, trust the kJ figure.
     */
    static Double energyKcal(Double kcal, Double kj) {
        Double fromKj = kj == null ? null : Math.round(kj / 4.184 * 10) / 10.0;
        if (kcal == null) return fromKj;
        if (fromKj != null && fromKj > 0 && Math.abs(kcal - fromKj) / fromKj > 0.25) return fromKj;
        return Math.round(kcal * 10) / 10.0;
    }

    // ---------- JSON helpers ----------

    private static String text(JsonNode json, String field) {
        JsonNode v = json.get(field);
        if (v == null || v.isNull()) return null;
        String s = decodeHtml(v.asText()).trim();
        return s.isEmpty() ? null : s;
    }

    /** Some Open Food Facts texts contain HTML entities such as &quot; - turn them back into characters. */
    static String decodeHtml(String s) {
        if (s.indexOf('&') < 0) return s;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("&(#\\d+|quot|amp|apos|#39|lt|gt|nbsp);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String r = switch (e) {
                case "quot" -> "\"";
                case "amp" -> "&";
                case "apos", "#39" -> "'";
                case "lt" -> "<";
                case "gt" -> ">";
                case "nbsp" -> " ";
                default -> String.valueOf((char) Integer.parseInt(e.substring(1)));
            };
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Open Food Facts sometimes sends numbers as strings, so accept both. */
    private static Double number(JsonNode json, String field) {
        JsonNode v = json.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.asDouble();
        try {
            return Double.parseDouble(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinArray(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) return null;
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asText()));
        return String.join(",", values);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }

    private static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
