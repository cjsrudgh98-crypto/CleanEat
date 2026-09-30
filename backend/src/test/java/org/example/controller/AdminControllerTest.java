package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.User;
import org.example.repository.OrderRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

    private static final String NEW_PRODUCT = """
            {"name":"테스트 현미칩","barcode":"TEST-0001","price":3500,"stock":10,"category":"SNACK",
             "description":"설명","allergens":["밀"," "],"diets":["VEGAN"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long paidOrderId() {
        User buyer = userRepository.save(User.builder().username("buyer_" + System.nanoTime()).nickname("구매자")
                .password("x").email("buyer@example.com").build());
        Order order = orderRepository.save(Order.builder().user(buyer).status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(4500)).recipientName("홍길동").phone("01012345678").address("주소")
                .paymentMethod("카드").orderName("현미칩").paidAt(LocalDateTime.now()).build());
        return order.getId();
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/admin/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원은_403과_메시지를_받는다() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_요약과_상품_목록을_본다() throws Exception {
        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(5));
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].barcode").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 상품을_등록하고_재고를_증감한다() throws Exception {
        String body = mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.allergens.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long productId = Long.parseLong(body.replaceAll(".*\"productId\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/admin/products/" + productId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));

        mockMvc.perform(post("/api/admin/products/" + productId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-8}"))
                .andExpect(status().isBadRequest());

        // 수정 요청의 stock은 무시된다 (재고는 증감 API로만)
        mockMvc.perform(put("/api/admin/products/" + productId).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PRODUCT.replace("3500", "3900").replace("\"stock\":10", "\"stock\":999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(3900))
                .andExpect(jsonPath("$.stock").value(7));

        // 공개 상품 목록에도 바로 나온다
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트 현미칩"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 이미_판매중인_바코드는_409() throws Exception {
        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 없는_주문의_상태는_바꿀_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/orders/999999/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 배송_단계를_차례로_진행하고_운송장을_고친다() throws Exception {
        long id = paidOrderId();
        String base = "/api/admin/orders/" + id;

        mockMvc.perform(post(base + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.preparingAt").exists())
                .andExpect(jsonPath("$.cancelable").value(false));

        // 운송장 없이 발송 불가
        mockMvc.perform(post(base + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPING\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(base + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPING\",\"courier\":\"CJ대한통운\",\"trackingNumber\":\"6123-4567-8901\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPING"))
                .andExpect(jsonPath("$.trackingUrl").value("https://trace.cjlogistics.com/next/tracking.html?wblNo=612345678901"));

        mockMvc.perform(put(base + "/tracking").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courier\":\"한진택배\",\"trackingNumber\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courier").value("한진택배"))
                .andExpect(jsonPath("$.trackingNumber").value("999"));

        // 발송 후에는 관리자도 취소 불가 (반품으로 처리)
        mockMvc.perform(post(base + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(base + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredAt").exists());

        mockMvc.perform(get("/api/admin/orders").param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + id + ")].status").value("DELIVERED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 택배사_목록을_준다() throws Exception {
        mockMvc.perform(get("/api/admin/couriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("CJ대한통운"));
    }
}
