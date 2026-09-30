package org.example.service;

import org.example.domain.Cart;
import org.example.domain.CartItem;
import org.example.domain.Product;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.cart.AddCartItemRequest;
import org.example.dto.cart.CartResponse;
import org.example.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private org.example.repository.CartRepository cartRepository;

    @Mock
    private org.example.repository.StoreListingRepository storeListingRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private Authentication authentication;

    private CartService cartService;

    private User user;
    private Product product;
    private StoreListing listing;
    private Cart cart;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, storeListingRepository, currentUserService, userPreferenceService);
        lenient().when(userPreferenceService.of(authentication)).thenReturn(UserPreferenceService.Preferences.NONE);

        user = User.builder().id(1L).username("cleaneat_user").build();
        product = Product.builder().id(10L).name("무첨가 현미 과자").barcode("8800000000011").build();
        listing = StoreListing.builder().id(100L).product(product).price(new BigDecimal(4500)).stock(5).build();
        cart = Cart.builder().id(1000L).user(user).build();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    }

    @Test
    void 처음_담으면_새_항목이_생긴다() {
        when(storeListingRepository.findByProductId(10L)).thenReturn(Optional.of(listing));
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(10L);
        request.setQuantity(2);

        CartResponse response = cartService.addItem(authentication, request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal(9000));
    }

    @Test
    void 이미_담긴_상품이면_수량을_합산한다() {
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(1).build());
        when(storeListingRepository.findByProductId(10L)).thenReturn(Optional.of(listing));
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(10L);
        request.setQuantity(2);

        CartResponse response = cartService.addItem(authentication, request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void 합친_수량이_int_범위를_넘어도_음수가_되어_통과하지_않는다() {
        listing.setStock(Integer.MAX_VALUE);
        CartItem existing = CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(5).build();
        cart.getItems().add(existing);
        when(storeListingRepository.findByProductId(10L)).thenReturn(Optional.of(listing));
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(10L);
        request.setQuantity(Integer.MAX_VALUE);

        assertThatThrownBy(() -> cartService.addItem(authentication, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("개까지 담을 수 있습니다");
        assertThat(existing.getQuantity()).isEqualTo(5);
    }

    @Test
    void 재고보다_많이_담으면_예외를_던진다() {
        when(storeListingRepository.findByProductId(10L)).thenReturn(Optional.of(listing));
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(10L);
        request.setQuantity(999);

        assertThatThrownBy(() -> cartService.addItem(authentication, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문가능한_상품이_아니면_예외를_던진다() {
        when(storeListingRepository.findByProductId(any())).thenReturn(Optional.empty());
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(999L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(authentication, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 항목을_삭제할_수_있다() {
        CartItem item = CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(1).build();
        cart.getItems().add(item);

        CartResponse response = cartService.removeItem(authentication, 1L);

        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void 없는_항목을_삭제하면_예외를_던진다() {
        assertThatThrownBy(() -> cartService.removeItem(authentication, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
