package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Cart;
import org.example.domain.CartItem;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.cart.AddCartItemRequest;
import org.example.dto.cart.CartItemResponse;
import org.example.dto.cart.CartResponse;
import org.example.exception.AllergyConfirmationRequiredException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.CartRepository;
import org.example.repository.StoreListingRepository;
import org.example.util.AllergenMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CartService {

    // 한 상품을 장바구니에 담을 수 있는 최대 수량 (요청 검증과 기존 수량 + 추가 수량 합계 모두에 적용)
    public static final int MAX_QUANTITY = 999;

    private final CartRepository cartRepository;
    private final StoreListingRepository storeListingRepository;
    private final CurrentUserService currentUserService;
    private final UserPreferenceService userPreferenceService;

    @Transactional
    public CartResponse getCart(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return toResponse(findOrCreateCart(user), authentication);
    }

    @Transactional
    public CartResponse addItem(Authentication authentication, AddCartItemRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        Cart cart = findOrCreateCart(user);
        StoreListing listing = storeListingRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("주문 가능한 상품이 아닙니다: productId=" + request.getProductId()));

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getStoreListing().getId().equals(listing.getId()))
                .findFirst()
                .orElse(null);

        // 내 알레르기 성분이 든 상품은 처음 담을 때 한 번 확인을 받는다 (이미 담긴 상품의 수량 추가는 확인 끝난 것으로 봄)
        Set<String> myAllergens = userPreferenceService.of(authentication).expandedAllergens();
        List<String> warnings = AllergenMatcher.matches(listing.getAllergens(), myAllergens);
        if (existing == null && !warnings.isEmpty() && !request.isAllergyConfirmed()) {
            throw new AllergyConfirmationRequiredException(
                    listing.getProduct().getId(), listing.getProduct().getName(), warnings);
        }

        // long으로 더한다 - int로 더하면 아주 큰 수량에서 음수로 넘쳐서 재고 검사를 통과해 버린다
        long requestedQuantity = (existing != null ? existing.getQuantity() : 0L) + request.getQuantity();
        if (requestedQuantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("한 상품은 " + MAX_QUANTITY + "개까지 담을 수 있습니다");
        }
        if (requestedQuantity > listing.getStock()) {
            throw new IllegalArgumentException("재고가 부족합니다: 남은 재고 " + listing.getStock() + "개");
        }

        if (existing != null) {
            existing.setQuantity((int) requestedQuantity);
        } else {
            cart.getItems().add(CartItem.builder().cart(cart).storeListing(listing).quantity(request.getQuantity()).build());
        }

        // 새로 추가한 CartItem은 flush 전까지 id가 없어서, 응답에 id를 그대로 담아 보내려면 여기서 flush가 필요하다
        cartRepository.flush();
        return toResponse(cart, authentication);
    }

    @Transactional
    public CartResponse updateItemQuantity(Authentication authentication, Long cartItemId, int quantity) {
        User user = currentUserService.getCurrentUser(authentication);
        Cart cart = findOrCreateCart(user);
        CartItem item = findItem(cart, cartItemId);

        if (quantity > item.getStoreListing().getStock()) {
            throw new IllegalArgumentException("재고가 부족합니다: 남은 재고 " + item.getStoreListing().getStock() + "개");
        }
        item.setQuantity(quantity);

        return toResponse(cart, authentication);
    }

    @Transactional
    public CartResponse removeItem(Authentication authentication, Long cartItemId) {
        User user = currentUserService.getCurrentUser(authentication);
        Cart cart = findOrCreateCart(user);
        CartItem item = findItem(cart, cartItemId);
        cart.getItems().remove(item);

        return toResponse(cart, authentication);
    }

    Cart findOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }

    private CartItem findItem(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("장바구니 항목을 찾을 수 없습니다: id=" + cartItemId));
    }

    private CartResponse toResponse(Cart cart, Authentication authentication) {
        Set<String> myAllergens = userPreferenceService.of(authentication).expandedAllergens();
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getId(),
                        item.getStoreListing().getProduct().getId(),
                        item.getStoreListing().getProduct().getName(),
                        item.getStoreListing().getPrice(),
                        item.getQuantity(),
                        item.getStoreListing().getStock(),
                        AllergenMatcher.matches(item.getStoreListing().getAllergens(), myAllergens)))
                .toList();

        BigDecimal total = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(items, total);
    }
}
