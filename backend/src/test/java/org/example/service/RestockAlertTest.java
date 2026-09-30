package org.example.service;

import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.mail.EmailSender;
import org.example.repository.ProductRepository;
import org.example.repository.RestockAlertRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 재입고 알림 - 실제 DB(H2)로 신청 -> 재고 조정 -> 메일(목) -> 신청 삭제까지 */
@SpringBootTest
class RestockAlertTest {

    @Autowired private RestockAlertService restockAlertService;
    @Autowired private AdminProductService adminProductService;
    @Autowired private ProductCatalogService productCatalogService;
    @Autowired private RestockAlertRepository restockAlertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreListingRepository storeListingRepository;

    @MockitoBean private EmailSender emailSender;

    private User withEmail;
    private User noEmail;
    private StoreListing soldOut;

    @BeforeEach
    void setUp() {
        withEmail = userRepository.save(User.builder().username("restock_a").nickname("알림A").password("x")
                .email("restock-a@example.com").build());
        noEmail = userRepository.save(User.builder().username("restock_b").nickname("알림B").password("x").build());
        Product product = productRepository.save(Product.builder().name("품절 테스트 과자").barcode("RESTOCK-0001").build());
        soldOut = storeListingRepository.save(StoreListing.builder().product(product).price(new BigDecimal(1000)).stock(0)
                .category(ProductCategory.SNACK).build());
    }

    @AfterEach
    void tearDown() {
        restockAlertRepository.deleteAll();
        storeListingRepository.delete(soldOut);
        productRepository.delete(soldOut.getProduct());
        userRepository.deleteAll(List.of(withEmail, noEmail));
    }

    // 실제 요청처럼 principal을 UserDetails로 (JwtAuthenticationFilter가 만드는 모양 - 문자열이면 비로그인으로 취급된다)
    private static Authentication as(User user) {
        var principal = org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password("x").authorities("ROLE_USER").build();
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Long productId() {
        return soldOut.getProduct().getId();
    }

    @Test
    void 품절_상품에_신청하고_관리자가_재고를_채우면_바로_메일을_보내고_신청을_지운다() {
        restockAlertService.subscribe(productId(), as(withEmail));
        restockAlertService.subscribe(productId(), as(withEmail)); // 두 번 눌러도 한 건
        assertThat(restockAlertRepository.count()).isEqualTo(1);

        adminProductService.adjustStock(productId(), 10);

        verify(emailSender).send(eq("restock-a@example.com"), contains("품절 테스트 과자 재입고"), anyString());
        assertThat(restockAlertRepository.count()).isZero();
    }

    @Test
    void 재고가_있던_상품의_재고를_늘릴_때는_보내지_않는다() {
        restockAlertService.subscribe(productId(), as(withEmail));
        adminProductService.adjustStock(productId(), 5);   // 품절 -> 5 (보냄)
        // 이제 재고가 있어서 새 신청은 안 된다
        assertThatThrownBy(() -> restockAlertService.subscribe(productId(), as(withEmail)))
                .isInstanceOf(IllegalArgumentException.class);
        adminProductService.adjustStock(productId(), 5);   // 5 -> 10 (보낼 대상 없음)

        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void 재고가_있는_상품이나_이메일이_없는_회원은_신청할_수_없다() {
        assertThatThrownBy(() -> restockAlertService.subscribe(productId(), as(noEmail)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이메일");

        adminProductService.adjustStock(productId(), 1);
        assertThatThrownBy(() -> restockAlertService.subscribe(productId(), as(withEmail)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("구매할 수 있는");
        assertThat(restockAlertRepository.count()).isZero();
    }

    @Test
    void 취소하면_재고가_생겨도_보내지_않는다() {
        restockAlertService.subscribe(productId(), as(withEmail));
        restockAlertService.unsubscribe(productId(), as(withEmail));

        adminProductService.adjustStock(productId(), 10);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 관리자_조정이_아닌_경로로_재고가_돌아와도_주기_확인에서_보낸다() {
        restockAlertService.subscribe(productId(), as(withEmail));
        // 주문 취소/반품 등으로 재고가 돌아온 경우 (관리자 재고 조정을 거치지 않음)
        soldOut.setStock(2);
        storeListingRepository.save(soldOut);

        assertThat(restockAlertService.sendRestocked()).isEqualTo(1);
        verify(emailSender).send(eq("restock-a@example.com"), anyString(), anyString());
        assertThat(restockAlertService.sendRestocked()).isZero(); // 한 번만
    }

    @Test
    void 상품_정보에_내가_알림을_신청했는지_표시된다() {
        assertThat(productCatalogService.getByProductId(productId(), as(withEmail)).getRestockAlert()).isFalse();
        restockAlertService.subscribe(productId(), as(withEmail));
        assertThat(productCatalogService.getByProductId(productId(), as(withEmail)).getRestockAlert()).isTrue();
        assertThat(productCatalogService.getByProductId(productId(), null).getRestockAlert()).isNull();
    }
}
