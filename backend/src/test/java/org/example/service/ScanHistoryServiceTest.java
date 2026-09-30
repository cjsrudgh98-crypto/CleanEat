package org.example.service;

import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.example.domain.User;
import org.example.dto.common.PageResponse;
import org.example.dto.history.ScanHistoryResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.ScanHistoryRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanHistoryServiceTest {

    @Mock
    private ScanHistoryRepository scanHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    private ScanHistoryService scanHistoryService;

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("cleaneat_user", "password", Collections.emptyList());

    @Test
    void 스캔기록을_저장한다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        User user = User.builder().id(1L).username("cleaneat_user").build();
        Product product = Product.builder().id(1L).name("테스트 과자").barcode("8801234567890").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));

        scanHistoryService.recordForUsername("cleaneat_user", product, "밀가루, 아스파탐",
                RiskLevel.MEDIUM, List.of("아스파탐"), List.of("밀"));

        ArgumentCaptor<ScanHistory> captor = ArgumentCaptor.forClass(ScanHistory.class);
        verify(scanHistoryRepository).save(captor.capture());
        ScanHistory saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getOverallRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(saved.getMatchedHarmfulIngredients()).isEqualTo("아스파탐");
        assertThat(saved.getMatchedAllergens()).isEqualTo("밀");
    }

    @Test
    void 존재하지_않는_사용자명으로_저장하면_예외를_던진다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanHistoryService.recordForUsername(
                "unknown_user", null, "", RiskLevel.LOW, List.of(), List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 히스토리_목록을_최신순으로_조회한다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        Product product = Product.builder().id(1L).name("테스트 과자").barcode("8801234567890").build();
        ScanHistory history = ScanHistory.builder()
                .id(1L).product(product).overallRisk(RiskLevel.HIGH)
                .matchedHarmfulIngredients("아질산나트륨").matchedAllergens("").build();
        when(scanHistoryRepository.findByUserIdOrderByScannedAtDescIdDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(history), PageRequest.of(0, 20), false));

        List<ScanHistoryResponse> result = scanHistoryService.getHistory(1L, 0, 20, authentication).items();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("테스트 과자");
        assertThat(result.get(0).getMatchedHarmfulIngredients()).containsExactly("아질산나트륨");
    }

    @Test
    void 페이지_크기는_1에서_100_사이로_맞춘다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        when(scanHistoryRepository.findByUserIdOrderByScannedAtDescIdDesc(eq(1L), any()))
                .thenAnswer(invocation -> new SliceImpl<>(List.of(), invocation.getArgument(1), false));

        PageResponse<ScanHistoryResponse> tooBig = scanHistoryService.getHistory(1L, -3, 100_000, authentication);

        assertThat(tooBig.page()).isZero();
        assertThat(tooBig.size()).isEqualTo(PageResponse.MAX_SIZE);
    }

    @Test
    void 제품정보가_없는_OCR_기록은_상품명이_OCR스캔으로_표시된다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        ScanHistory history = ScanHistory.builder()
                .id(1L).product(null).overallRisk(RiskLevel.LOW)
                .matchedHarmfulIngredients("").matchedAllergens("").build();
        when(scanHistoryRepository.findByUserIdOrderByScannedAtDescIdDesc(eq(1L), any()))
                .thenReturn(new SliceImpl<>(List.of(history)));

        List<ScanHistoryResponse> result = scanHistoryService.getHistory(1L, 0, 20, authentication).items();

        assertThat(result.get(0).getProductName()).isEqualTo("OCR 스캔");
        assertThat(result.get(0).getBarcode()).isNull();
    }

    @Test
    void 단건_삭제시_존재하지_않으면_예외를_던진다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        when(scanHistoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanHistoryService.deleteOne(1L, 99L, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 단건_삭제에_성공한다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);
        ScanHistory history = ScanHistory.builder().id(5L).overallRisk(RiskLevel.LOW).build();
        when(scanHistoryRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(history));

        scanHistoryService.deleteOne(1L, 5L, authentication);

        verify(scanHistoryRepository).delete(history);
    }

    @Test
    void 전체_삭제시_사용자ID로_모두_삭제한다() {
        scanHistoryService = new ScanHistoryService(scanHistoryRepository, userRepository, currentUserService);

        scanHistoryService.deleteAll(1L, authentication);

        verify(scanHistoryRepository).deleteByUserId(1L);
        verify(scanHistoryRepository, never()).delete(any());
    }
}
