package com.truthscan.service;

import com.truthscan.dto.HealthProfile;
import com.truthscan.dto.IngredientFlag;
import com.truthscan.dto.NutritionInfo;
import com.truthscan.dto.ProductResponse;
import com.truthscan.dto.ProductSubmission;
import com.truthscan.dto.ScoreResult;
import com.truthscan.exception.ApiException;
import com.truthscan.model.Product;
import com.truthscan.model.ProductSource;
import com.truthscan.model.ProductStatus;
import com.truthscan.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Finds products (own database first, then Open Food Facts), handles user
 * submissions and admin review, and builds the response for the product page.
 */
@Service
public class ProductService {

    private final ProductRepository repo;
    private final OpenFoodFactsClient openFoodFacts;
    private final IngredientAnalyzer analyzer;
    private final ScoringService scoring;

    public ProductService(ProductRepository repo, OpenFoodFactsClient openFoodFacts,
                          IngredientAnalyzer analyzer, ScoringService scoring) {
        this.repo = repo;
        this.openFoodFacts = openFoodFacts;
        this.analyzer = analyzer;
        this.scoring = scoring;
    }

    /** Looks up a scanned barcode. */
    public ProductResponse getByBarcode(String barcode, HealthProfile profile) {
        String code = cleanBarcode(barcode);

        // 1. Our own database (fast; includes reviewed user submissions)
        Optional<Product> local = repo.findByBarcode(code);
        if (local.isPresent()) {
            Product p = local.get();
            if (p.getStatus() == ProductStatus.PENDING) {
                throw ApiException.pendingReview(
                        "Thanks! This product was submitted and is waiting for review.");
            }
            return toResponse(p, profile);
        }

        // 2. Open Food Facts, saved locally so the next scan is instant
        Product fetched = openFoodFacts.fetch(code)
                .orElseThrow(() -> ApiException.notFound(
                        "We don't have this product yet. You can add it from the label."));
        return toResponse(saveOrReuse(fetched), profile);
    }

    public List<ProductResponse> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw ApiException.badRequest("Type at least 2 characters to search");
        }
        return repo.search(q, PageRequest.of(0, 20)).stream()
                .map(p -> toResponse(p, HealthProfile.NONE))
                .toList();
    }

    /** A user typed in a product from its label. It stays hidden until an admin approves it. */
    @Transactional
    public ProductResponse submit(ProductSubmission s) {
        String code = cleanBarcode(s.barcode());
        repo.findByBarcode(code).ifPresent(existing -> {
            throw existing.getStatus() == ProductStatus.PENDING
                    ? ApiException.conflict("This product has already been submitted and is awaiting review.")
                    : ApiException.conflict("This product is already in our database.");
        });

        Product p = new Product();
        p.setBarcode(code);
        p.setName(s.name().trim());
        p.setBrand(trimToNull(s.brand()));
        p.setCategory(trimToNull(s.category()));
        p.setIngredientsText(trimToNull(s.ingredientsText()));
        p.setAllergens(trimToNull(s.allergens()));
        p.setEnergyKcal(s.energyKcal());
        p.setSugar(s.sugar());
        p.setFat(s.fat());
        p.setSaturatedFat(s.saturatedFat());
        p.setSalt(s.salt());
        p.setFiber(s.fiber());
        p.setProtein(s.protein());
        p.setSubmittedBy(trimToNull(s.submittedBy()));
        p.setSource(ProductSource.USER);
        p.setStatus(ProductStatus.PENDING);

        return toResponse(repo.save(p), HealthProfile.NONE);
    }

    // ---------- Admin ----------

    public List<ProductResponse> pending() {
        return repo.findByStatusOrderByCreatedAtAsc(ProductStatus.PENDING).stream()
                .map(p -> toResponse(p, HealthProfile.NONE))
                .toList();
    }

    @Transactional
    public ProductResponse approve(Long id) {
        Product p = repo.findById(id).orElseThrow(() -> ApiException.notFound("No product with id " + id));
        p.setStatus(ProductStatus.APPROVED);
        return toResponse(repo.save(p), HealthProfile.NONE);
    }

    @Transactional
    public void reject(Long id) {
        if (!repo.existsById(id)) {
            throw ApiException.notFound("No product with id " + id);
        }
        repo.deleteById(id);
    }

    // ---------- Building the response ----------

    ProductResponse toResponse(Product p, HealthProfile profile) {
        List<IngredientFlag> flags = analyzer.analyze(p.getIngredientsText(), p.getAdditives());
        List<String> allergens = analyzer.detectAllergens(p.getIngredientsText(), p.getAllergens());
        List<String> traces = analyzer.detectTraces(p.getIngredientsText(), allergens);
        ScoreResult score = scoring.calculate(p, flags);
        List<String> warnings = scoring.personalWarnings(p, profile, flags, allergens, traces,
                score.nutrientLevels());

        String sourceUrl = p.getSource() == ProductSource.OPEN_FOOD_FACTS
                ? "https://world.openfoodfacts.org/product/" + p.getBarcode()
                : null;

        return new ProductResponse(
                p.getId(),
                p.getBarcode(),
                p.getName(),
                p.getBrand(),
                p.getCategory(),
                p.getImageUrl(),
                p.getIngredientsText(),
                new NutritionInfo(p.getEnergyKcal(), p.getSugar(), p.getFat(), p.getSaturatedFat(),
                        p.getSalt(), p.getFiber(), p.getProtein()),
                score.score(),
                score.label(),
                score.dataComplete(),
                score.nutrientLevels(),
                score.reasons(),
                flags,
                allergens,
                traces,
                warnings,
                p.getSource().name(),
                sourceUrl,
                p.getStatus().name());
    }

    // ---------- helpers ----------

    /** Two people scanning the same new product at once: the second save hits the unique key. */
    private Product saveOrReuse(Product p) {
        try {
            return repo.save(p);
        } catch (DataIntegrityViolationException e) {
            return repo.findByBarcode(p.getBarcode()).orElseThrow(() -> e);
        }
    }

    static String cleanBarcode(String raw) {
        String code = raw == null ? "" : raw.replaceAll("\\s", "");
        if (!code.matches("\\d{8,14}")) {
            throw ApiException.badRequest("Barcode must be 8 to 14 digits");
        }
        return code;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
