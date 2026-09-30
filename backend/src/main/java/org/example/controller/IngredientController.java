package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ingredient.IngredientRequest;
import org.example.dto.ingredient.IngredientResponse;
import org.example.service.IngredientCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 유해성분 사전. 목록은 누구나 볼 수 있고(/api/ingredients), 추가/수정/사용 중지는 관리자만(/api/admin/ingredients).
 */
@RestController
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientCatalogService ingredientCatalogService;

    @GetMapping("/api/ingredients")
    public ResponseEntity<List<IngredientResponse>> publicList() {
        return ResponseEntity.ok(ingredientCatalogService.publicList());
    }

    @GetMapping("/api/admin/ingredients")
    public ResponseEntity<List<IngredientResponse>> adminList() {
        return ResponseEntity.ok(ingredientCatalogService.adminList());
    }

    @PostMapping("/api/admin/ingredients")
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ingredientCatalogService.create(request));
    }

    @PutMapping("/api/admin/ingredients/{id}")
    public ResponseEntity<IngredientResponse> update(@PathVariable Long id, @Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.ok(ingredientCatalogService.update(id, request));
    }

    // 사용 중지/다시 사용 ({"enabled": false})
    @PatchMapping("/api/admin/ingredients/{id}/enabled")
    public ResponseEntity<IngredientResponse> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) throw new IllegalArgumentException("enabled 값이 필요합니다");
        return ResponseEntity.ok(ingredientCatalogService.setEnabled(id, enabled));
    }
}
