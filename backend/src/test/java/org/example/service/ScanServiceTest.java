package org.example.service;

import org.example.domain.DietType;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.StoreListing;
import org.example.dto.scan.IngredientMatchResponse;
import org.example.dto.scan.ReceiptScanResponse;
import org.example.dto.scan.RiskAssessmentResult;
import org.example.dto.scan.ScanResultResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.OrderRepository;
import org.example.repository.ProductRepository;
import org.example.repository.StoreListingRepository;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanServiceTest {

    @Mock private BarcodeLookupService barcodeLookupService;
    @Mock private IngredientRiskService ingredientRiskService;
    @Mock private OcrService ocrService;
    @Mock private RecommendationService recommendationService;
    @Mock private ScanHistoryService scanHistoryService;
    @Mock private UserPreferenceService userPreferenceService;
    @Mock private StoreListingRepository storeListingRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;

    private ScanService scanService;

    private static final String NUTELLA = "3017620422003";
    private static final String COLA = "5449000000996";

    @BeforeEach
    void setUp() {
        scanService = new ScanService(barcodeLookupService, ingredientRiskService, ocrService, recommendationService,
                scanHistoryService, userPreferenceService, storeListingRepository, productRepository, orderRepository);
        // 견과류 알레르기 + 비건 식단 사용자 (authentication은 null로 넘겨도 선호도는 목에서 받음)
        List<String> allergies = List.of("견과류");
        when(userPreferenceService.of(any())).thenReturn(
                new Preferences(true, Set.of(DietType.VEGAN), allergies, AllergenMatcher.expand(allergies)));
        when(recommendationService.recommend(any(), any(), any())).thenReturn(List.of());
        when(storeListingRepository.findByProductId(any())).thenReturn(Optional.empty());
    }

    private Product product(long id, String name, String barcode, String ingredients) {
        return Product.builder().id(id).name(name).barcode(barcode).rawIngredientsText(ingredients).build();
    }

    private RiskAssessmentResult risk(RiskLevel level, List<String> allergens, IngredientMatchResponse... harmful) {
        return new RiskAssessmentResult(List.of(), List.of(harmful), allergens, level);
    }

    @Test
    void 바코드_스캔_결과에_내_알레르기_경고가_따로_담긴다() {
        when(barcodeLookupService.lookup(NUTELLA)).thenReturn(product(1L, "누텔라", NUTELLA, "sucre, noisettes, lait"));
        when(ingredientRiskService.assess(any())).thenReturn(risk(RiskLevel.LOW, List.of("헤이즐넛", "우유")));

        ScanResultResponse result = scanService.scanBarcode(NUTELLA, null);

        assertThat(result.getAllergenMatches()).containsExactly("헤이즐넛", "우유");
        // 견과류 알레르기 -> 헤이즐넛만 내 알레르기 경고
        assertThat(result.getUserAllergyWarnings()).containsExactly("헤이즐넛");
    }

    @Test
    void 영수증_QR의_바코드들을_각각_분석하고_찾지못한_바코드는_실패목록에_넣는다() {
        String unknown = "4006381333931"; // 검증 숫자는 맞지만 제품 DB에 없는 바코드
        when(barcodeLookupService.lookup(NUTELLA)).thenReturn(product(1L, "누텔라", NUTELLA, "noisettes"));
        when(barcodeLookupService.lookup(COLA)).thenReturn(product(2L, "콜라", COLA, "aspartame"));
        when(barcodeLookupService.lookup(unknown)).thenThrow(new ResourceNotFoundException("없음"));
        when(ingredientRiskService.assess(any()))
                .thenReturn(risk(RiskLevel.LOW, List.of("헤이즐넛")))
                .thenReturn(risk(RiskLevel.MEDIUM, List.of(),
                        new IngredientMatchResponse("아스파탐", RiskLevel.MEDIUM, "인공감미료")));

        // 영수증 번호(검증 숫자 불일치, DB에도 없음)는 바코드로 취급하지 않는다
        ReceiptScanResponse result = scanService.scanReceipt(
                "영수증 12345678901\n" + NUTELLA + "*2\n" + COLA + "\n" + unknown, null);

        assertThat(result.source()).isEqualTo("BARCODES");
        assertThat(result.items()).extracting(ReceiptScanResponse.ReceiptItem::productName).containsExactly("누텔라", "콜라");
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.items().get(0).userAllergyWarnings()).containsExactly("헤이즐넛");
        assertThat(result.failed()).extracting(ReceiptScanResponse.FailedItem::code).containsExactly(unknown);
        assertThat(result.overallRisk()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.allergyWarningCount()).isEqualTo(1);
        verify(barcodeLookupService, never()).lookup("12345678901");
    }

    @Test
    void CleanEat_주문_영수증이면_주문상품을_분석하고_상품데이터의_알레르기와_식단을_쓴다() {
        Product almond = product(10L, "무염 구운 아몬드", "8800000000059", "소금과 기름 없이 그대로 구운 아몬드");
        StoreListing listing = StoreListing.builder().id(100L).product(almond).price(BigDecimal.TEN).stock(10)
                .allergens(new HashSet<>(Set.of("아몬드"))).suitableDiets(new HashSet<>(Set.of(DietType.VEGAN))).build();
        OrderItem item = OrderItem.builder().storeListing(listing).productName("무염 구운 아몬드")
                .unitPrice(BigDecimal.TEN).quantity(3).build();
        Order order = Order.builder().id(1L).status(OrderStatus.PAID).orderName("무염 구운 아몬드")
                .tossOrderId("CE-0123456789abcdef0123456789abcdef").build();
        order.getItems().add(item);

        when(orderRepository.findByTossOrderId("CE-0123456789abcdef0123456789abcdef")).thenReturn(Optional.of(order));
        when(storeListingRepository.findByProductId(10L)).thenReturn(Optional.of(listing));
        when(ingredientRiskService.assess(any())).thenReturn(risk(RiskLevel.LOW, List.of()));

        ReceiptScanResponse result = scanService.scanReceipt("CLEANEAT-RECEIPT:CE-0123456789abcdef0123456789abcdef", null);

        assertThat(result.source()).isEqualTo("CLEANEAT_ORDER");
        ReceiptScanResponse.ReceiptItem scanned = result.items().get(0);
        assertThat(scanned.quantity()).isEqualTo(3);
        assertThat(scanned.storeProductId()).isEqualTo(10L);
        assertThat(scanned.allergenMatches()).containsExactly("아몬드");
        assertThat(scanned.userAllergyWarnings()).containsExactly("아몬드");
        assertThat(scanned.dietMatch()).isTrue();
    }

    @Test
    void 결제되지_않은_주문의_영수증은_찾을_수_없다() {
        Order pending = Order.builder().id(1L).status(OrderStatus.PENDING_PAYMENT)
                .tossOrderId("CE-0123456789abcdef0123456789abcdef").build();
        when(orderRepository.findByTossOrderId(anyString())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> scanService.scanReceipt("CE-0123456789abcdef0123456789abcdef", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 영수증_사진의_상품명과_바코드로_상품을_찾고_같은_상품은_한번만_센다() {
        Product almond = product(10L, "무염 구운 아몬드", "8800000000059", "아몬드");
        Product tofu = product(11L, "유기농 두부", "8800000008058", "대두");
        when(ocrService.extractReceiptText(any())).thenReturn("""
                001 무염구운아몬드   1  6,900
                8800000000059
                002 유기농두부      2  6,600
                003 삼겹살         1 15,000
                합계                 28,500
                """);
        when(productRepository.findByBarcode("8800000000059")).thenReturn(Optional.of(almond));
        when(barcodeLookupService.lookup("8800000000059")).thenReturn(almond);
        when(productRepository.findAllNames()).thenReturn(List.of(
                new ProductRepository.ProductName(10L, "무염 구운 아몬드"), new ProductRepository.ProductName(11L, "유기농 두부")));
        // 아몬드는 바코드로 이미 찾았으므로 다시 불러오지 않는다
        when(productRepository.findById(11L)).thenReturn(Optional.of(tofu));
        when(ingredientRiskService.assess(any())).thenReturn(risk(RiskLevel.LOW, List.of()));

        ReceiptScanResponse result = scanService.scanReceiptImage(null, null);

        assertThat(result.source()).isEqualTo("RECEIPT_TEXT");
        // 아몬드는 이름 줄 + 바코드 줄 두 번 잡히지만 한 번만, 수량도 1
        assertThat(result.items()).extracting(ReceiptScanResponse.ReceiptItem::productName)
                .containsExactly("무염 구운 아몬드", "유기농 두부");
        assertThat(result.items()).extracting(ReceiptScanResponse.ReceiptItem::quantity).containsExactly(1, 2);
        assertThat(result.items().get(1).matchedText()).isEqualTo("유기농두부");
        // DB에 없는 상품 줄은 실패 목록으로
        assertThat(result.failed()).extracting(ReceiptScanResponse.FailedItem::code).containsExactly("삼겹살");
    }

    @Test
    void 영수증_사진에서_글자를_못_읽으면_다시_찍으라고_안내한다() {
        when(ocrService.extractReceiptText(any())).thenReturn("  \n --- \n");

        assertThatThrownBy(() -> scanService.scanReceiptImage(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다시 찍어주세요");
    }

    @Test
    void 인식할_수_없는_코드면_안내_메시지와_함께_실패한다() {
        assertThatThrownBy(() -> scanService.scanReceipt("https://example.com/hello", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영수증 QR");
    }
}
