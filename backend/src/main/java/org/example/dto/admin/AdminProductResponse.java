package org.example.dto.admin;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductResponse(
        Long productId,
        String barcode,
        String name,
        BigDecimal price,
        Integer stock,
        String category,
        String categoryLabel,
        String imageUrl,
        String description,
        String rawIngredientsText,
        List<String> allergens,
        List<String> diets) {
}
