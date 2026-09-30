package org.example.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.RiskLevel;

@Getter
@AllArgsConstructor
public class IngredientMatchResponse {
    private String ingredientName;
    private RiskLevel riskLevel;
    private String description;
}
