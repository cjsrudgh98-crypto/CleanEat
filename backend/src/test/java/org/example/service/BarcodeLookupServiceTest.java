package org.example.service;

import org.example.domain.Ingredient;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.exception.ExternalApiException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.IngredientRepository;
import org.example.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
class BarcodeLookupServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    private BarcodeLookupService serviceWithResponse(String jsonBody, HttpStatus status) {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        return new BarcodeLookupService(webClient, productRepository, ingredientRepository);
    }

    @Test
    void 이미_DB에_있는_바코드면_외부API를_호출하지_않는다() {
        BarcodeLookupService service = serviceWithResponse("{}", HttpStatus.OK);
        Product existing = Product.builder().id(1L).name("기존 제품").barcode("8801234567890").build();
        when(productRepository.findByBarcode("8801234567890")).thenReturn(Optional.of(existing));

        Product result = service.lookup("8801234567890");

        assertThat(result).isEqualTo(existing);
        verify(productRepository, never()).save(any());
    }

    @Test
    void 신규_바코드는_외부API_조회_후_저장하고_유해성분만_연결한다() {
        String json = """
                {"status":1,"product":{"product_name":"테스트 과자","ingredients_text":"밀가루, 아스파탐"}}
                """;
        BarcodeLookupService service = serviceWithResponse(json, HttpStatus.OK);
        when(productRepository.findByBarcode("8801234567890")).thenReturn(Optional.empty());
        when(ingredientRepository.findAll()).thenReturn(List.of(
                Ingredient.builder().id(1L).name("아스파탐").riskLevel(RiskLevel.MEDIUM).build()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        Product result = service.lookup("8801234567890");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("테스트 과자");
        // "밀가루"처럼 유해성분 목록에 없는 성분은 더 이상 DB에 자동 등록되지 않는다
        assertThat(result.getIngredients()).extracting(Ingredient::getName).containsExactly("아스파탐");
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    void 같은_바코드를_다른_요청이_먼저_저장했으면_그_상품을_쓴다() {
        String json = """
                {"status":1,"product":{"product_name":"테스트 과자","ingredients_text":"밀가루"}}
                """;
        BarcodeLookupService service = serviceWithResponse(json, HttpStatus.OK);
        Product savedByOther = Product.builder().id(20L).name("테스트 과자").barcode("8801234567890").build();
        // 처음 확인할 때는 없었는데, 외부 API를 기다리는 사이 다른 요청이 저장했다
        when(productRepository.findByBarcode("8801234567890"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedByOther));
        when(ingredientRepository.findAll()).thenReturn(List.of());
        when(productRepository.save(any(Product.class)))
                .thenThrow(new DataIntegrityViolationException("uk_products_barcode"));

        Product result = service.lookup("8801234567890");

        assertThat(result.getId()).isEqualTo(20L);
    }

    @Test
    void 한국어_성분표가_있으면_우선_사용하고_영어_성분도_사전으로_연결한다() {
        String json = """
                {"status":1,"product":{"product_name":"Cola","product_name_ko":"콜라",
                 "ingredients_text":"Carbonated water, sugar, aspartame (E951), sodium benzoate",
                 "ingredients_text_ko":""}}
                """;
        BarcodeLookupService service = serviceWithResponse(json, HttpStatus.OK);
        when(productRepository.findByBarcode("5449000000996")).thenReturn(Optional.empty());
        when(ingredientRepository.findAll()).thenReturn(List.of(
                // 영어 표기는 사전의 별칭으로 찾는다 (seed/harmful-ingredients.json)
                Ingredient.builder().id(1L).name("아스파탐").riskLevel(RiskLevel.MEDIUM)
                        .aliases(Set.of("aspartame", "e951")).build(),
                Ingredient.builder().id(2L).name("안식향산나트륨").riskLevel(RiskLevel.MEDIUM)
                        .aliases(Set.of("sodium benzoate", "e211")).build()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.lookup("5449000000996");

        assertThat(result.getName()).isEqualTo("콜라");
        assertThat(result.getIngredients()).extracting(Ingredient::getName)
                .containsExactlyInAnyOrder("아스파탐", "안식향산나트륨");
    }

    @Test
    void 존재하지_않는_제품이면_ResourceNotFoundException을_던진다() {
        String json = """
                {"status":0,"product":null}
                """;
        BarcodeLookupService service = serviceWithResponse(json, HttpStatus.OK);
        when(productRepository.findByBarcode("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 외부API가_오류를_반환하면_ExternalApiException을_던진다() {
        BarcodeLookupService service = serviceWithResponse("{}", HttpStatus.INTERNAL_SERVER_ERROR);
        when(productRepository.findByBarcode("9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("9999999999"))
                .isInstanceOf(ExternalApiException.class);
    }
}
