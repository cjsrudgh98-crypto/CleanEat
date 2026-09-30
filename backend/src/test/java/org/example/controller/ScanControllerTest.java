package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.dto.scan.BarcodeScanRequest;
import org.example.dto.scan.IngredientMatchResponse;
import org.example.dto.scan.RiskAssessmentResult;
import org.example.service.BarcodeLookupService;
import org.example.service.IngredientRiskService;
import org.example.service.OcrService;
import org.example.service.RecommendationService;
import org.example.service.ScanHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BarcodeLookupService barcodeLookupService;

    @MockBean
    private IngredientRiskService ingredientRiskService;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private OcrService ocrService;

    @MockBean
    private ScanHistoryService scanHistoryService;

    @Test
    void 바코드_스캔시_위험도_결과를_반환한다() throws Exception {
        Product product = Product.builder()
                .id(1L)
                .name("테스트 과자")
                .barcode("8801234567890")
                .rawIngredientsText("밀가루, 아스파탐, 계란")
                .build();

        RiskAssessmentResult riskResult = new RiskAssessmentResult(
                List.of("밀가루", "아스파탐", "계란"),
                List.of(new IngredientMatchResponse("아스파탐", RiskLevel.MEDIUM, "인공감미료")),
                List.of("밀", "계란"),
                RiskLevel.MEDIUM
        );

        when(barcodeLookupService.lookup(anyString())).thenReturn(product);
        when(ingredientRiskService.assess(any())).thenReturn(riskResult);
        when(recommendationService.recommend(any(), any(), any())).thenReturn(List.of());

        BarcodeScanRequest request = new BarcodeScanRequest();
        request.setBarcode("8801234567890");

        mockMvc.perform(post("/api/scan/barcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("테스트 과자"))
                .andExpect(jsonPath("$.overallRisk").value("MEDIUM"))
                .andExpect(jsonPath("$.harmfulIngredients[0].ingredientName").value("아스파탐"));
    }

    @Test
    void 바코드가_비어있으면_400을_반환한다() throws Exception {
        BarcodeScanRequest request = new BarcodeScanRequest();
        request.setBarcode("");

        mockMvc.perform(post("/api/scan/barcode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
