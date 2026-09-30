package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.cart.AddCartItemRequest;
import org.example.dto.cart.CartResponse;
import org.example.dto.cart.UpdateCartItemRequest;
import org.example.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(cartService.addItem(authentication, request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItem(@PathVariable Long cartItemId,
                                                    @Valid @RequestBody UpdateCartItemRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(cartService.updateItemQuantity(authentication, cartItemId, request.getQuantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long cartItemId, Authentication authentication) {
        return ResponseEntity.ok(cartService.removeItem(authentication, cartItemId));
    }
}
