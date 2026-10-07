package com.truthscan.controller;

import com.truthscan.dto.HealthProfile;
import com.truthscan.dto.ProductResponse;
import com.truthscan.dto.ProductSubmission;
import com.truthscan.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public API.
 *
 *   GET  /api/products/{barcode}?conditions=diabetes&allergies=milk
 *   GET  /api/products/search?q=biscuit
 *   POST /api/products            (submit a missing product)
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public List<ProductResponse> search(@RequestParam("q") String query) {
        return service.search(query);
    }

    @GetMapping("/{barcode}")
    public ProductResponse getByBarcode(@PathVariable String barcode,
                                        @RequestParam(required = false) String conditions,
                                        @RequestParam(required = false) String allergies) {
        return service.getByBarcode(barcode, HealthProfile.parse(conditions, allergies));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse submit(@Valid @RequestBody ProductSubmission submission) {
        return service.submit(submission);
    }
}
