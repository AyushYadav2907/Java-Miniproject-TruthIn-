package com.truthscan.repository;

import com.truthscan.model.Product;
import com.truthscan.model.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcode(String barcode);

    List<Product> findByStatusOrderByCreatedAtAsc(ProductStatus status);

    /** Case-insensitive search on name or brand, approved products only. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = com.truthscan.model.ProductStatus.APPROVED
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.name
            """)
    List<Product> search(@Param("q") String query, Pageable pageable);
}
