package com.truthscan.model;

/**
 * Review state of a product. Only APPROVED products are shown publicly.
 * Rejected submissions are deleted, so there is no REJECTED state.
 */
public enum ProductStatus {
    APPROVED,
    PENDING
}
