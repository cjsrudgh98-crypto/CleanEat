package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.DietType;
import org.example.dto.profile.UserProfileRequest;
import org.example.dto.profile.UserProfileResponse;
import org.example.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserProfileService userProfileService;

    @Test
    void 인증없이_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/1/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 인증된_사용자가_프로필을_조회한다() throws Exception {
        when(userProfileService.get(eq(1L), any())).thenReturn(
                new UserProfileResponse(1L, "cleaneat_user", "cleaneat_user", null, "user@example.com", List.of("밀", "계란"), List.of(DietType.VEGAN)));

        mockMvc.perform(get("/api/users/1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cleaneat_user"))
                .andExpect(jsonPath("$.allergies[0]").value("밀"))
                .andExpect(jsonPath("$.dietTypes[0]").value("VEGAN"));
    }

    @Test
    @WithMockUser(username = "cleaneat_user")
    void 인증된_사용자가_프로필을_수정한다() throws Exception {
        UserProfileRequest request = new UserProfileRequest();
        request.setAllergies(List.of("갑각류"));
        request.setDietTypes(List.of(DietType.KETO, DietType.GLUTEN_FREE));

        when(userProfileService.update(eq(1L), any(), any())).thenReturn(
                new UserProfileResponse(1L, "cleaneat_user", "cleaneat_user", null, "user@example.com", List.of("갑각류"), List.of(DietType.GLUTEN_FREE, DietType.KETO)));

        mockMvc.perform(put("/api/users/1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dietTypes.length()").value(2));
    }
}
