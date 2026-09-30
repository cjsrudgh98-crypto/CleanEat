package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Ingredient;
import org.example.dto.ingredient.IngredientRequest;
import org.example.dto.ingredient.IngredientResponse;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 유해성분 사전 조회/관리. 검사(IngredientRiskService)는 이 사전을 그대로 쓰므로 여기서 고치면 다음 검사부터 바로 반영된다.
 * 성분은 지우지 않고 "사용 중지"한다 - 기본 사전 성분은 지워도 서버 재시작 때 시드 파일에서 다시 들어오기 때문.
 */
@Service
@RequiredArgsConstructor
public class IngredientCatalogService {

    // 성분 사전 화면에 보여줄 분류 순서 (목록에 없는 분류는 뒤에 이름 순)
    private static final List<String> CATEGORY_ORDER = List.of(
            "감미료", "착색료", "보존료", "발색제", "산화방지제", "향미증진제", "유화제·증점제", "기타 첨가물", "당류", "지방");

    private final IngredientRepository ingredientRepository;

    /** 공개 성분 사전 - 검사에 쓰이는 성분만 */
    @Transactional(readOnly = true)
    public List<IngredientResponse> publicList() {
        return sorted(ingredientRepository.findAll().stream().filter(Ingredient::isActive).toList());
    }

    /** 관리자 - 사용 중지한 성분 포함 */
    @Transactional(readOnly = true)
    public List<IngredientResponse> adminList() {
        return sorted(ingredientRepository.findAll());
    }

    @Transactional
    public IngredientResponse create(IngredientRequest request) {
        String name = request.getName().trim();
        if (ingredientRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new DuplicateResourceException("이미 사전에 있는 성분입니다: " + name);
        }
        Ingredient ingredient = Ingredient.builder().name(name).enabled(true).build();
        apply(ingredient, request);
        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public IngredientResponse update(Long id, IngredientRequest request) {
        Ingredient ingredient = find(id);
        String name = request.getName().trim();
        ingredientRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("이미 사전에 있는 성분입니다: " + name);
                });
        ingredient.setName(name);
        apply(ingredient, request);
        return toResponse(ingredient);
    }

    @Transactional
    public IngredientResponse setEnabled(Long id, boolean enabled) {
        Ingredient ingredient = find(id);
        ingredient.setEnabled(enabled);
        return toResponse(ingredient);
    }

    private Ingredient find(Long id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("사전에 없는 성분입니다: id=" + id));
    }

    private static void apply(Ingredient ingredient, IngredientRequest request) {
        ingredient.setRiskLevel(request.getRiskLevel());
        ingredient.setCategory(blankToNull(request.getCategory()));
        ingredient.setDescription(blankToNull(request.getDescription()));
        Set<String> aliases = new LinkedHashSet<>();
        if (request.getAliases() != null) {
            request.getAliases().stream().filter(a -> a != null && !a.isBlank()).map(String::trim).forEach(aliases::add);
        }
        ingredient.getAliases().clear();
        ingredient.getAliases().addAll(aliases);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int categoryRank(String category) {
        int index = category == null ? -1 : CATEGORY_ORDER.indexOf(category);
        return index >= 0 ? index : CATEGORY_ORDER.size();
    }

    private static List<IngredientResponse> sorted(List<Ingredient> ingredients) {
        return ingredients.stream()
                .sorted(Comparator.comparingInt((Ingredient i) -> categoryRank(i.getCategory()))
                        .thenComparing(i -> i.getCategory() == null ? "" : i.getCategory())
                        .thenComparing(Ingredient::getName))
                .map(IngredientCatalogService::toResponse)
                .toList();
    }

    private static IngredientResponse toResponse(Ingredient i) {
        return new IngredientResponse(i.getId(), i.getName(), i.getRiskLevel().name(), i.getCategory(), i.getDescription(),
                i.getAliases().stream().sorted().toList(), i.isActive());
    }
}
