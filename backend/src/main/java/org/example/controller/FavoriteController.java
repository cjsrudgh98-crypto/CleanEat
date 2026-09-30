package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.product.StoreListingResponse;
import org.example.service.FavoriteService;
import org.example.service.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 찜하기 (로그인 필요 - SecurityConfig의 anyRequest().authenticated()) */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final ProductCatalogService productCatalogService;

    @GetMapping
    public ResponseEntity<List<StoreListingResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(productCatalogService.favorites(authentication));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(@PathVariable Long productId, Authentication authentication) {
        favoriteService.add(productId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId, Authentication authentication) {
        favoriteService.remove(productId, authentication);
        return ResponseEntity.noContent().build();
    }
}
