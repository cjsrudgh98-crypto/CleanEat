package org.example.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.RiskLevel;

import java.util.List;

@Getter
@AllArgsConstructor
public class RiskAssessmentResult {
    private List<String> normalizedIngredients;
    private List<IngredientMatchResponse> harmfulIngredients;
    private List<String> allergenMatches;
    private RiskLevel overallRisk;
}
