package com.truthscan.controller;

import com.truthscan.dto.ProductResponse;
import com.truthscan.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Review of user submissions. Every request needs the "X-Admin-Key" header
 * (checked by AdminKeyInterceptor).
 *
 *   GET    /api/admin/products/pending
 *   POST   /api/admin/products/{id}/approve
 *   DELETE /api/admin/products/{id}          (reject)
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminController {

    private final ProductService service;

    public AdminController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<ProductResponse> pending() {
        return service.pending();
    }

    @PostMapping("/{id}/approve")
    public ProductResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id) {
        service.reject(id);
    }
}
