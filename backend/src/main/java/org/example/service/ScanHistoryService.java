package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.example.domain.User;
import org.example.dto.common.PageResponse;
import org.example.dto.history.ScanHistoryResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.ScanHistoryRepository;
import org.example.repository.UserRepository;
import org.example.util.TextLimits;
import org.example.util.IngredientTextParser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScanHistoryService {

    private final ScanHistoryRepository scanHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public void recordForUsername(String username, Product product, String rawIngredientsText, RiskLevel overallRisk,
                                   List<String> matchedHarmfulIngredients, List<String> matchedAllergens) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + username));

        ScanHistory history = ScanHistory.builder()
                .user(user)
                .product(product)
                .rawIngredientsText(TextLimits.truncate(rawIngredientsText, TextLimits.RAW_INGREDIENTS))
                .overallRisk(overallRisk)
                .matchedHarmfulIngredients(TextLimits.truncate(String.join(", ", matchedHarmfulIngredients), TextLimits.MATCH_LIST))
                .matchedAllergens(TextLimits.truncate(String.join(", ", matchedAllergens), TextLimits.MATCH_LIST))
                .build();
        scanHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScanHistoryResponse> getHistory(Long userId, int page, int size, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        return PageResponse.of(
                scanHistoryRepository.findByUserIdOrderByScannedAtDescIdDesc(userId, PageResponse.request(page, size)),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScanHistory getOne(Long userId, Long historyId, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        return scanHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("스캔 기록을 찾을 수 없습니다: id=" + historyId));
    }

    @Transactional
    public void deleteOne(Long userId, Long historyId, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        ScanHistory history = scanHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("스캔 기록을 찾을 수 없습니다: id=" + historyId));
        scanHistoryRepository.delete(history);
    }

    @Transactional
    public void deleteAll(Long userId, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        scanHistoryRepository.deleteByUserId(userId);
    }

    private ScanHistoryResponse toResponse(ScanHistory history) {
        return new ScanHistoryResponse(
                history.getId(),
                history.getProduct() != null ? history.getProduct().getName() : "OCR 스캔",
                history.getProduct() != null ? history.getProduct().getBarcode() : null,
                history.getOverallRisk(),
                IngredientTextParser.parse(history.getMatchedHarmfulIngredients()),
                IngredientTextParser.parse(history.getMatchedAllergens()),
                history.getScannedAt()
        );
    }
}
