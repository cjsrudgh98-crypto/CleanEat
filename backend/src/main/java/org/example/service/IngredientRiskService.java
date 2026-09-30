package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Ingredient;
import org.example.domain.RiskLevel;
import org.example.dto.scan.IngredientMatchResponse;
import org.example.dto.scan.RiskAssessmentResult;
import org.example.repository.IngredientRepository;
import org.example.util.HarmfulIngredientMatcher;
import org.example.util.IngredientDictionary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 성분 목록에서 유해성분 / 알레르기 유발물질을 찾는다.
 * - 유해성분: DB 유해성분 사전(ingredients + 별칭)에서 HarmfulIngredientMatcher로 한국어·영어·프랑스어·E-번호 표기를
 *   찾아 위험도/설명을 붙인다. 관리자가 사전에 추가한 성분도 바로 찾는다 (사용 중지한 성분은 제외).
 * - 알레르기: IngredientDictionary로 찾은 "이 제품에 들어 있는" 알레르기 전체.
 *   (사용자 본인의 알레르기와 겹치는지는 ScanService에서 따로 계산한다)
 */
@Service
@RequiredArgsConstructor
public class IngredientRiskService {

    private final IngredientRepository ingredientRepository;

    public RiskAssessmentResult assess(List<String> rawIngredients) {
        List<Ingredient> dictionary = ingredientRepository.findAll();
        Map<String, Ingredient> knownByName = dictionary.stream()
                .collect(Collectors.toMap(Ingredient::getName, Function.identity(), (a, b) -> a));
        HarmfulIngredientMatcher matcher = HarmfulIngredientMatcher.of(dictionary);

        // 같은 성분이 여러 번 나와도 한 번만 (예: "아스파탐", "감미료(아스파탐)")
        Map<String, IngredientMatchResponse> harmfulMatches = new LinkedHashMap<>();
        Set<String> allergenMatches = new LinkedHashSet<>();

        for (String rawIngredient : rawIngredients) {
            for (String name : matcher.find(rawIngredient)) {
                Ingredient known = knownByName.get(name);
                if (known != null) {
                    harmfulMatches.putIfAbsent(name,
                            new IngredientMatchResponse(known.getName(), known.getRiskLevel(), known.getDescription()));
                }
            }
            allergenMatches.addAll(IngredientDictionary.findAllergens(rawIngredient));
        }

        List<IngredientMatchResponse> matches = List.copyOf(harmfulMatches.values());
        return new RiskAssessmentResult(rawIngredients, matches, List.copyOf(allergenMatches), calculateOverallRisk(matches));
    }

    private RiskLevel calculateOverallRisk(List<IngredientMatchResponse> matches) {
        boolean hasHigh = matches.stream().anyMatch(m -> m.getRiskLevel() == RiskLevel.HIGH);
        if (hasHigh) {
            return RiskLevel.HIGH;
        }
        boolean hasMedium = matches.stream().anyMatch(m -> m.getRiskLevel() == RiskLevel.MEDIUM);
        if (hasMedium) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
