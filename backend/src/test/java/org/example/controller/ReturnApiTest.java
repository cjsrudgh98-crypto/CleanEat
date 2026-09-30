package org.example.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 반품 API 권한/입력 검증 (업무 규칙은 OrderReturnTest) */
@SpringBootTest
@AutoConfigureMockMvc
class ReturnApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로그인하지_않으면_반품_신청은_401() throws Exception {
        mockMvc.perform(post("/api/orders/1/return").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"변심\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "someone")
    void 반품_사유가_비어_있으면_400() throws Exception {
        mockMvc.perform(post("/api/orders/1/return").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_회원은_반품_승인_거절을_할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/orders/1/return/approve")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/orders/1/return/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"거절\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 거절_사유가_없으면_400_없는_주문은_404() throws Exception {
        mockMvc.perform(post("/api/admin/orders/999999/return/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/orders/999999/return/approve")).andExpect(status().isNotFound());
    }
}
