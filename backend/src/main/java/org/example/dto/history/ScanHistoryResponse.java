package org.example.dto.history;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ScanHistoryResponse {
    private Long id;
    private String productName;
    private String barcode;
    private RiskLevel overallRisk;
    private List<String> matchedHarmfulIngredients;
    private List<String> matchedAllergens;
    private LocalDateTime scannedAt;
}
