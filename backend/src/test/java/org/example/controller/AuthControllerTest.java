package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.auth.AuthResponse;
import org.example.dto.auth.FindUsernameResponse;
import org.example.dto.auth.LoginRequest;
import org.example.dto.auth.RegisterRequest;
import org.example.exception.DuplicateResourceException;
import org.example.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static RegisterRequest validRegisterRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("password123!");
        request.setName("홍길동");
        request.setNickname("길동이");
        request.setEmail("gildong@example.com");
        request.setPhone("010-1234-5678");
        request.setBirthDate(LocalDate.of(1998, 1, 1));
        request.setAgreeTerms(true);
        request.setEmailVerificationToken("verified-token");
        return request;
    }

    @Test
    void 회원가입_성공시_201과_토큰을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");

        when(authService.register(any())).thenReturn(new AuthResponse("test-jwt-token", 1L, "cleaneat_user", "길동이", "USER"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.username").value("cleaneat_user"));
    }

    @Test
    void 아이디가_중복되면_409를_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("existing_user");

        when(authService.register(any())).thenThrow(new DuplicateResourceException("이미 사용 중인 아이디입니다: existing_user"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void 이메일_인증을_하지_않으면_400을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");
        request.setEmailVerificationToken(null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호가_8자_미만이면_400을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");
        request.setPassword("ab1!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호에_특수문자가_없으면_400을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이메일_형식이_틀리면_400을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 개인정보_수집에_동의하지_않으면_400을_반환한다() throws Exception {
        RegisterRequest request = validRegisterRequest("cleaneat_user");
        request.setAgreeTerms(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_성공시_200과_토큰을_반환한다() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("cleaneat_user");
        request.setPassword("password123");

        when(authService.login(any())).thenReturn(new AuthResponse("test-jwt-token", 1L, "cleaneat_user", "cleaneat_user", "USER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"));
    }

    @Test
    void 아이디찾기_성공시_가려진_아이디를_반환한다() throws Exception {
        when(authService.findUsername(any()))
                .thenReturn(new FindUsernameResponse("cl**********r", LocalDate.of(2026, 1, 1)));

        mockMvc.perform(post("/api/auth/find-username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"email\":\"gildong@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedUsername").value("cl**********r"));
    }

    @Test
    void 비밀번호_재설정_확정시_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"token\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isNoContent());
    }
}
