package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Ingredient;
import org.example.domain.Product;
import org.example.dto.external.OpenFoodFactsResponse;
import org.example.exception.ExternalApiException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.IngredientRepository;
import org.example.repository.ProductRepository;
import org.example.util.HarmfulIngredientMatcher;
import org.example.util.IngredientTextParser;
import org.example.util.TextLimits;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BarcodeLookupService {

    private final WebClient openFoodFactsWebClient;
    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;

    /**
     * 일부러 트랜잭션을 걸지 않는다 - 외부 API를 기다리는 동안(최대 5초) DB 연결을 붙잡지 않고,
     * 저장이 충돌해도(아래) 망가진 트랜잭션 없이 다시 읽을 수 있게. 조회/저장은 각각 리포지토리 트랜잭션으로 실행된다.
     */
    public Product lookup(String barcode) {
        return productRepository.findByBarcode(barcode)
                .orElseGet(() -> fetchAndSave(barcode));
    }

    private Product fetchAndSave(String barcode) {
        OpenFoodFactsResponse response;
        try {
            response = openFoodFactsWebClient.get()
                    .uri("/api/v2/product/{barcode}.json", barcode)
                    .retrieve()
                    .bodyToMono(OpenFoodFactsResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorMap(WebClientResponseException.class,
                            e -> new ExternalApiException("Open Food Facts 조회에 실패했습니다: " + e.getStatusCode(), e))
                    .block();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Open Food Facts 호출 중 오류가 발생했습니다", e);
        }

        if (response == null || response.getStatus() != 1 || response.getProduct() == null) {
            throw new ResourceNotFoundException("바코드에 해당하는 제품을 찾을 수 없습니다: " + barcode);
        }

        OpenFoodFactsResponse.ProductData data = response.getProduct();
        // 한국어 정보가 있으면 우선 사용 (성분 사전이 한국어 표기를 가장 잘 알아본다)
        String productName = firstNonBlank(data.getProductNameKo(), data.getProductName(), "이름 없는 제품");
        String ingredientsText = firstNonBlank(data.getIngredientsTextKo(), data.getIngredientsText(),
                data.getIngredientsTextEn(), "");

        // 분석(유해성분 연결)은 전체 글자로 하고, 저장만 컬럼 길이에 맞게 자른다
        Product product = Product.builder()
                .name(TextLimits.truncate(productName, TextLimits.PRODUCT_NAME))
                .barcode(barcode)
                .rawIngredientsText(TextLimits.truncate(ingredientsText, TextLimits.RAW_INGREDIENTS))
                .ingredients(resolveHarmfulIngredients(ingredientsText))
                .build();

        try {
            return productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            // 같은 바코드를 동시에 처음 조회한 다른 요청이 먼저 저장했다 (바코드 고유 제약) - 그 상품을 쓴다
            return productRepository.findByBarcode(barcode).orElseThrow(() -> e);
        }
    }

    /**
     * 성분표에서 DB에 등록된 "유해성분"만 찾아 제품과 연결한다 (추천 필터/위험도 계산에 쓰임).
     * 예전처럼 모르는 성분을 새로 등록하지 않는다 - 그렇게 하면 모든 성분이 "안전" 유해성분으로 잡히는 문제가 있었다.
     */
    private Set<Ingredient> resolveHarmfulIngredients(String ingredientsText) {
        List<String> tokens = IngredientTextParser.parse(ingredientsText);
        List<Ingredient> known = ingredientRepository.findAll();
        HarmfulIngredientMatcher matcher = HarmfulIngredientMatcher.of(known);
        Set<Ingredient> result = new HashSet<>();
        for (String token : tokens) {
            Set<String> names = matcher.find(token);
            for (Ingredient ingredient : known) {
                if (names.contains(ingredient.getName())) result.add(ingredient);
            }
        }
        return result;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
