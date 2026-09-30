package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.repository.OrderRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 찜하기 + 리뷰·별점 - 실제 DB로 API 흐름을 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FavoriteReviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StoreListingRepository storeListingRepository;

    private User buyer;
    private User other;
    private StoreListing listing;
    private Long productId;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder().username("rv_buyer").nickname("구매자").password("x").build());
        other = userRepository.save(User.builder().username("rv_other").nickname("다른사람").password("x").build());
        List<StoreListing> listings = storeListingRepository.findAll();
        listing = listings.get(0);
        productId = listing.getProduct().getId();
    }

    private static RequestPostProcessor as(User u) {
        return user(u.getUsername()).roles("USER");
    }

    private void purchase(User u, OrderStatus status) {
        Order order = Order.builder().user(u).status(status).totalAmount(BigDecimal.valueOf(4500))
                .recipientName("홍길동").phone("01012345678").address("주소").paymentMethod("카드").build();
        order.getItems().add(OrderItem.builder().order(order).storeListing(listing).productName("상품")
                .unitPrice(BigDecimal.valueOf(4500)).quantity(1).build());
        orderRepository.save(order);
    }

    private String review(Long productId, int rating, String content) {
        return "{\"productId\":" + productId + ",\"rating\":" + rating + ",\"content\":\"" + content + "\"}";
    }

    // ---------------- 찜하기 ----------------

    @Test
    void 찜하면_목록과_상품에_표시되고_해제하면_사라진다() throws Exception {
        mockMvc.perform(put("/api/favorites/" + productId).with(as(buyer))).andExpect(status().isNoContent());
        // 두 번 눌러도 하나
        mockMvc.perform(put("/api/favorites/" + productId).with(as(buyer))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/favorites").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].favorite").value(true));
        mockMvc.perform(get("/api/products/" + productId).with(as(buyer)))
                .andExpect(jsonPath("$.favorite").value(true));
        // 다른 사람에게는 찜으로 안 보인다
        mockMvc.perform(get("/api/products/" + productId).with(as(other)))
                .andExpect(jsonPath("$.favorite").value(false));

        mockMvc.perform(delete("/api/favorites/" + productId).with(as(buyer))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/favorites").with(as(buyer))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 비로그인은_찜할_수_없고_상품의_찜_여부는_null이다() throws Exception {
        mockMvc.perform(put("/api/favorites/" + productId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.favorite").doesNotExist());
    }

    @Test
    void 판매하지_않는_상품은_찜할_수_없다() throws Exception {
        mockMvc.perform(put("/api/favorites/999999").with(as(buyer))).andExpect(status().isNotFound());
    }

    // ---------------- 리뷰 ----------------

    @Test
    void 구매하지_않았으면_리뷰를_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/products/" + productId + "/reviews").with(as(buyer)))
                .andExpect(jsonPath("$.canWrite").value(false))
                .andExpect(jsonPath("$.writeBlockedReason").value("NOT_PURCHASED"));
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(productId, 5, "좋아요")))
                .andExpect(status().isBadRequest());
        // 결제 전/취소된 주문은 구매로 치지 않는다
        purchase(buyer, OrderStatus.CANCELLED);
        mockMvc.perform(get("/api/products/" + productId + "/reviews").with(as(buyer)))
                .andExpect(jsonPath("$.writeBlockedReason").value("NOT_PURCHASED"));
    }

    @Test
    void 구매자는_리뷰를_쓰고_고치고_지울_수_있고_별점이_상품에_반영된다() throws Exception {
        purchase(buyer, OrderStatus.DELIVERED);
        mockMvc.perform(get("/api/products/" + productId + "/reviews").with(as(buyer)))
                .andExpect(jsonPath("$.canWrite").value(true));

        String created = mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(productId, 5, "  맛있고 성분도 깔끔해요  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("맛있고 성분도 깔끔해요"))
                .andExpect(jsonPath("$.nickname").value("구매자"))
                .andReturn().getResponse().getContentAsString();
        long reviewId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // 상품당 하나
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(productId, 4, "또")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/products/" + productId + "/reviews").with(as(buyer)))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.ratingCounts[4]").value(1))
                .andExpect(jsonPath("$.myReview.id").value(reviewId))
                .andExpect(jsonPath("$.writeBlockedReason").value("ALREADY_WRITTEN"));
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        // 다른 사람은 고치거나 지울 수 없다
        mockMvc.perform(put("/api/reviews/" + reviewId).with(as(other)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(null, 1, "조작")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reviews/" + reviewId).with(as(other))).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/reviews/" + reviewId).with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(null, 3, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").exists());
        mockMvc.perform(get("/api/products").with(as(buyer)))
                .andExpect(jsonPath("$[?(@.productId == " + productId + ")].averageRating").value(3.0));

        mockMvc.perform(delete("/api/reviews/" + reviewId).with(as(buyer))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    @Test
    void 평균_별점은_소수점_한_자리() throws Exception {
        purchase(buyer, OrderStatus.PAID);
        purchase(other, OrderStatus.SHIPPING);
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 5, ""))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/reviews").with(as(other)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 4, ""))).andExpect(status().isCreated());
        User third = userRepository.save(User.builder().username("rv_third").nickname("셋째").password("x").build());
        purchase(third, OrderStatus.DELIVERED);
        mockMvc.perform(post("/api/reviews").with(as(third)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 4, ""))).andExpect(status().isCreated());

        // (5 + 4 + 4) / 3 = 4.333 -> 4.3
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.averageRating").value(4.3))
                .andExpect(jsonPath("$.ratingCounts[3]").value(2));
        mockMvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.averageRating").value(4.3));
    }

    @Test
    void 비로그인도_리뷰를_볼_수_있지만_쓸_수는_없다() throws Exception {
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canWrite").value(false))
                .andExpect(jsonPath("$.writeBlockedReason").value("LOGIN_REQUIRED"));
        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(review(productId, 5, "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 별점은_1에서_5점이고_리뷰는_500자까지() throws Exception {
        purchase(buyer, OrderStatus.DELIVERED);
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 6, ""))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 0, ""))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(review(productId, 5, "가".repeat(501)))).andExpect(status().isBadRequest());
    }

    @Test
    void 관리자는_부적절한_리뷰를_지울_수_있다() throws Exception {
        purchase(buyer, OrderStatus.DELIVERED);
        String created = mockMvc.perform(post("/api/reviews").with(as(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(review(productId, 1, "광고")))
                .andReturn().getResponse().getContentAsString();
        long reviewId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/admin/reviews/" + reviewId).with(as(other))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/reviews/" + reviewId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }
}
