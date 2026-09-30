package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.StoreListing;
import org.example.dto.scan.IngredientMatchResponse;
import org.example.dto.scan.ProductSummaryResponse;
import org.example.dto.scan.ReceiptScanResponse;
import org.example.dto.scan.ReceiptScanResponse.FailedItem;
import org.example.dto.scan.ReceiptScanResponse.ReceiptItem;
import org.example.dto.scan.RiskAssessmentResult;
import org.example.dto.scan.ScanResultResponse;
import org.example.exception.ExternalApiException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.OrderRepository;
import org.example.repository.ProductRepository;
import org.example.repository.ProductRepository.ProductName;
import org.example.repository.StoreListingRepository;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.example.util.IngredientTextParser;
import org.example.util.ProductNameMatcher;
import org.example.util.ReceiptCodeParser;
import org.example.util.ReceiptTextParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 바코드 / 성분표 이미지 / 영수증(QR·바코드·사진) 스캔의 공통 흐름.
 *  제품 찾기 -> 성분 분석(유해성분·알레르기) -> 내 알레르기와 비교 -> 대안 추천 -> (로그인 시) 검사 기록 저장
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private final BarcodeLookupService barcodeLookupService;
    private final IngredientRiskService ingredientRiskService;
    private final OcrService ocrService;
    private final RecommendationService recommendationService;
    private final ScanHistoryService scanHistoryService;
    private final UserPreferenceService userPreferenceService;
    private final StoreListingRepository storeListingRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public ScanResultResponse scanBarcode(String barcode, Authentication authentication) {
        Preferences prefs = userPreferenceService.of(authentication);
        Product product = barcodeLookupService.lookup(barcode);
        Analysis analysis = analyze(product, product.getRawIngredientsText(), prefs);

        recordHistory(authentication, product, product.getRawIngredientsText(), analysis);
        List<ProductSummaryResponse> recommendations =
                recommendationService.recommend(product.getId(), analysis.harmfulNames(), prefs);

        return analysis.toScanResult(product.getName(), product.getBarcode(), recommendations);
    }

    public ScanResultResponse scanImage(MultipartFile file, Authentication authentication) {
        Preferences prefs = userPreferenceService.of(authentication);
        String extractedText = ocrService.extractText(file);
        Analysis analysis = analyze(null, extractedText, prefs);

        recordHistory(authentication, null, extractedText, analysis);
        List<ProductSummaryResponse> recommendations =
                recommendationService.recommend(null, analysis.harmfulNames(), prefs);

        return analysis.toScanResult("OCR 스캔", null, recommendations);
    }

    /**
     * 영수증 QR/바코드 -> 상품 목록 -> 상품마다 성분 분석.
     * 하나가 실패해도(외부 DB에 없는 바코드 등) 나머지는 계속 분석하고 실패 목록으로 알려준다.
     */
    public ReceiptScanResponse scanReceipt(String code, Authentication authentication) {
        Preferences prefs = userPreferenceService.of(authentication);
        ReceiptCodeParser.Parsed parsed = ReceiptCodeParser.parse(code);

        List<ReceiptItem> items = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();
        Set<String> harmfulNames = new LinkedHashSet<>();
        String source;
        String title;

        if (parsed instanceof ReceiptCodeParser.OrderReceipt receipt) {
            Order order = orderRepository.findByTossOrderId(receipt.orderCode())
                    .filter(o -> o.getStatus().isPurchased())
                    .orElseThrow(() -> new ResourceNotFoundException("영수증에 해당하는 주문을 찾을 수 없습니다"));
            source = "CLEANEAT_ORDER";
            title = "CleanEat 주문 영수증 · " + (order.getOrderName() != null ? order.getOrderName() : order.getItems().size() + "개 상품");
            for (OrderItem orderItem : order.getItems()) {
                if (orderItem.isCancelled()) continue;
                StoreListing listing = orderItem.getStoreListing();
                if (listing == null) {
                    failed.add(new FailedItem(orderItem.getProductName(), "판매가 중지된 상품이라 성분 정보가 없습니다"));
                    continue;
                }
                Product product = listing.getProduct();
                Analysis analysis = analyze(product, product.getRawIngredientsText(), prefs);
                harmfulNames.addAll(analysis.harmfulNames());
                recordHistory(authentication, product, product.getRawIngredientsText(), analysis);
                items.add(analysis.toReceiptItem(product, orderItem.getQuantity()));
            }
        } else if (parsed instanceof ReceiptCodeParser.BarcodeList list) {
            source = "BARCODES";
            for (Map.Entry<String, Integer> entry : list.quantities().entrySet()) {
                String barcode = entry.getKey();
                // 영수증 번호/전화번호 같은 숫자는 바코드 검증 숫자가 안 맞으므로 건너뛴다 (우리 DB 상품은 예외)
                if (!ReceiptCodeParser.isValidGtin(barcode) && productRepository.findByBarcode(barcode).isEmpty()) {
                    continue;
                }
                try {
                    Product product = barcodeLookupService.lookup(barcode);
                    Analysis analysis = analyze(product, product.getRawIngredientsText(), prefs);
                    harmfulNames.addAll(analysis.harmfulNames());
                    recordHistory(authentication, product, product.getRawIngredientsText(), analysis);
                    items.add(analysis.toReceiptItem(product, entry.getValue()));
                } catch (ResourceNotFoundException e) {
                    failed.add(new FailedItem(barcode, "제품 정보를 찾을 수 없습니다"));
                } catch (ExternalApiException e) {
                    failed.add(new FailedItem(barcode, "제품 정보 조회에 실패했습니다. 잠시 후 다시 시도해주세요"));
                }
            }
            if (items.isEmpty() && failed.isEmpty()) {
                throw new IllegalArgumentException("영수증에서 상품 바코드를 찾지 못했습니다");
            }
            title = "영수증 상품 " + (items.size() + failed.size()) + "개";
        } else {
            throw new IllegalArgumentException(
                    "인식할 수 없는 영수증 코드입니다. CleanEat 영수증 QR이나 상품 바코드가 담긴 QR을 스캔해주세요");
        }

        return buildReceiptResponse(source, title, items, failed, harmfulNames, prefs);
    }

    /**
     * 영수증 사진 -> OCR -> 상품 줄/바코드 추출 -> DB 상품과 이름 비교로 찾기 -> 상품마다 성분 분석.
     * 영수증에 상품 바코드가 찍혀 있으면 이름보다 바코드가 정확하므로 먼저 쓴다.
     * 찾지 못한 줄은 실패 목록으로 돌려줘서 사용자가 어떤 글자가 읽혔는지 볼 수 있게 한다.
     */
    public ReceiptScanResponse scanReceiptImage(MultipartFile file, Authentication authentication) {
        Preferences prefs = userPreferenceService.of(authentication);
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse(ocrService.extractReceiptText(file));
        if (parsed.lines().isEmpty() && parsed.barcodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "영수증에서 상품명을 읽지 못했습니다. 밝은 곳에서 영수증이 화면에 꽉 차게 다시 찍어주세요");
        }

        // 상품 id -> 찾은 상품 (같은 상품이 바코드 줄과 이름 줄에서 모두 잡혀도 한 번만)
        Map<Long, FoundProduct> found = new LinkedHashMap<>();
        List<FailedItem> failed = new ArrayList<>();

        for (String barcode : parsed.barcodes()) {
            if (!ReceiptCodeParser.isValidGtin(barcode) && productRepository.findByBarcode(barcode).isEmpty()) continue;
            try {
                Product product = barcodeLookupService.lookup(barcode);
                found.putIfAbsent(product.getId(), new FoundProduct(product, 1, barcode, true));
            } catch (ResourceNotFoundException | ExternalApiException e) {
                failed.add(new FailedItem(barcode, "바코드에 해당하는 제품 정보를 찾을 수 없습니다"));
            }
        }

        // 이름 비교에는 id/이름만 읽고(성분 등 연관 데이터 없이), 실제로 찾은 상품만 따로 불러온다
        List<ProductName> catalog = productRepository.findAllNames();
        for (ReceiptTextParser.ReceiptLine line : parsed.lines()) {
            Optional<ProductNameMatcher.Match<ProductName>> match =
                    ProductNameMatcher.best(line.name(), catalog, ProductName::name);
            if (match.isEmpty()) {
                if (failed.size() < MAX_FAILED_LINES) failed.add(new FailedItem(line.name(), "일치하는 상품을 찾지 못했습니다"));
                continue;
            }
            Long productId = match.get().item().id();
            Product product = found.containsKey(productId)
                    ? found.get(productId).product()
                    : productRepository.findById(productId).orElse(null);
            if (product == null) continue; // 비교하는 사이 지워진 상품
            found.merge(productId, new FoundProduct(product, line.quantity(), line.name(), false), FoundProduct::merge);
        }

        List<ReceiptItem> items = new ArrayList<>();
        Set<String> harmfulNames = new LinkedHashSet<>();
        for (FoundProduct f : found.values()) {
            Analysis analysis = analyze(f.product(), f.product().getRawIngredientsText(), prefs);
            harmfulNames.addAll(analysis.harmfulNames());
            recordHistory(authentication, f.product(), f.product().getRawIngredientsText(), analysis);
            items.add(analysis.toReceiptItem(f.product(), f.quantity(), f.matchedText()));
        }

        String title = items.isEmpty() ? "영수증 사진 · 찾은 상품이 없어요" : "영수증 사진 · 상품 " + items.size() + "개 찾음";
        return buildReceiptResponse("RECEIPT_TEXT", title, items, failed, harmfulNames, prefs);
    }

    // 영수증 사진에서 상품이 아닌 잡음 줄이 많이 나올 수 있어서 실패 목록은 이만큼만 보여준다
    private static final int MAX_FAILED_LINES = 20;

    /**
     * 영수증에서 찾은 상품 하나.
     * 바코드로 찾은 것과 이름으로 찾은 것이 같은 상품이면 한 줄로 합친다 -
     * 보통 영수증은 "상품명 줄 + 그 아래 바코드 줄"이라 수량을 더하면 두 배가 되므로 큰 쪽을 쓴다.
     * 이름 줄끼리 겹치면 (같은 상품을 따로 두 번 찍은 경우) 수량을 더한다.
     */
    private record FoundProduct(Product product, int quantity, String matchedText, boolean fromBarcode) {
        FoundProduct merge(FoundProduct other) {
            boolean bothNames = !fromBarcode && !other.fromBarcode;
            int qty = bothNames ? quantity + other.quantity : Math.max(quantity, other.quantity);
            String text = fromBarcode ? other.matchedText : matchedText;
            return new FoundProduct(product, qty, text, fromBarcode && other.fromBarcode);
        }
    }

    private ReceiptScanResponse buildReceiptResponse(String source, String title, List<ReceiptItem> items,
                                                     List<FailedItem> failed, Set<String> harmfulNames, Preferences prefs) {
        RiskLevel overall = items.stream().map(ReceiptItem::overallRisk)
                .max(Comparator.comparingInt(ScanService::severity)).orElse(RiskLevel.LOW);
        int allergyWarnings = (int) items.stream().filter(i -> !i.userAllergyWarnings().isEmpty()).count();
        List<ProductSummaryResponse> recommendations = recommendationService.recommend(null, harmfulNames, prefs);

        return new ReceiptScanResponse(source, title, overall, allergyWarnings, items, failed, recommendations);
    }

    /**
     * 성분 분석 + 내 알레르기 비교.
     * CleanEat 판매 상품은 상품 데이터에 정리된 알레르기 성분을 함께 쓴다 (설명 글만으로는 알레르기를 알 수 없음).
     */
    private Analysis analyze(Product product, String ingredientsText, Preferences prefs) {
        RiskAssessmentResult result = ingredientRiskService.assess(IngredientTextParser.parse(ingredientsText));
        Optional<StoreListing> listing = product != null && product.getId() != null
                ? storeListingRepository.findByProductId(product.getId())
                : Optional.empty();

        Set<String> allergens = new LinkedHashSet<>(result.getAllergenMatches());
        listing.ifPresent(l -> allergens.addAll(l.getAllergens().stream().sorted().toList()));
        List<String> allergenList = List.copyOf(allergens);

        Boolean dietMatch = listing.isPresent() && prefs.hasDiet() ? prefs.fitsDiet(listing.get().getSuitableDiets()) : null;
        return new Analysis(result.getOverallRisk(), result.getHarmfulIngredients(), allergenList,
                AllergenMatcher.matches(allergenList, prefs.expandedAllergens()), dietMatch,
                listing.map(l -> l.getProduct().getId()).orElse(null));
    }

    private void recordHistory(Authentication authentication, Product product, String rawText, Analysis analysis) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return;
        }
        scanHistoryService.recordForUsername(authentication.getName(), product, rawText, analysis.overallRisk(),
                analysis.harmfulIngredients().stream().map(IngredientMatchResponse::getIngredientName).toList(),
                analysis.allergens());
    }

    private static int severity(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
    }

    private record Analysis(RiskLevel overallRisk,
                            List<IngredientMatchResponse> harmfulIngredients,
                            List<String> allergens,
                            List<String> userAllergyWarnings,
                            Boolean dietMatch,
                            Long storeProductId) {

        Set<String> harmfulNames() {
            return harmfulIngredients.stream().map(IngredientMatchResponse::getIngredientName).collect(Collectors.toSet());
        }

        ScanResultResponse toScanResult(String name, String barcode, List<ProductSummaryResponse> recommendations) {
            return new ScanResultResponse(name, barcode, overallRisk, harmfulIngredients, allergens,
                    userAllergyWarnings, recommendations);
        }

        ReceiptItem toReceiptItem(Product product, int quantity) {
            return toReceiptItem(product, quantity, null);
        }

        ReceiptItem toReceiptItem(Product product, int quantity, String matchedText) {
            return new ReceiptItem(product.getName(), product.getBarcode(), quantity, storeProductId, overallRisk,
                    harmfulIngredients, allergens, userAllergyWarnings, dietMatch, matchedText);
        }
    }
}
