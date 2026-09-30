package org.example.controller;

import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.example.dto.common.PageResponse;
import org.example.dto.history.ScanHistoryResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.service.ScanHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanHistoryService scanHistoryService;

    @Test
    void 인증없이_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/1/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 히스토리_목록을_조회한다() throws Exception {
        when(scanHistoryService.getHistory(eq(1L), eq(1), eq(10), any())).thenReturn(new PageResponse<>(List.of(
                new ScanHistoryResponse(1L, "테스트 과자", "8801234567890", RiskLevel.MEDIUM,
                        List.of("아스파탐"), List.of("밀"), LocalDateTime.now())
        ), 1, 10, true));

        mockMvc.perform(get("/api/users/1/history").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("테스트 과자"))
                .andExpect(jsonPath("$.items[0].overallRisk").value("MEDIUM"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void 인증없이_리포트를_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/1/history/5/report"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 리포트를_PDF로_다운로드한다() throws Exception {
        Product product = Product.builder().id(1L).name("테스트 과자").barcode("8801234567890").build();
        ScanHistory history = ScanHistory.builder()
                .id(5L).product(product).overallRisk(RiskLevel.MEDIUM)
                .matchedHarmfulIngredients("아스파탐").matchedAllergens("밀")
                .rawIngredientsText("밀가루, 아스파탐").scannedAt(LocalDateTime.now())
                .build();
        when(scanHistoryService.getOne(eq(1L), eq(5L), any())).thenReturn(history);

        mockMvc.perform(get("/api/users/1/history/5/report"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cleaneat-report-5.pdf\""));
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 존재하지_않는_기록의_리포트를_요청하면_404를_반환한다() throws Exception {
        when(scanHistoryService.getOne(eq(1L), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("스캔 기록을 찾을 수 없습니다: id=99"));

        mockMvc.perform(get("/api/users/1/history/99/report"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 단건_삭제에_성공하면_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/users/1/history/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 전체_삭제에_성공하면_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/users/1/history"))
                .andExpect(status().isNoContent());
    }
}
