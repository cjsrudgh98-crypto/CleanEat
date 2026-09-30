package org.example.controller;

import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.domain.UserProfile;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserProfileRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 알레르기 성분이 든 상품을 장바구니에 담을 때 확인을 받는지 - 실제 DB로 확인 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllergyCartTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private StoreListingRepository storeListingRepository;

    private Long milkProductId;
    private Long walnutProductId;
    private Long plainProductId;

    @BeforeEach
    void setUp() {
        // 재고 넉넉한 상품 3개를 골라 알레르기 성분을 테스트용으로 정한다 (테스트 끝나면 롤백)
        List<StoreListing> listings = storeListingRepository.findAll().stream().filter(l -> l.getStock() >= 5).limit(3).toList();
        milkProductId = setAllergens(listings.get(0), "우유", "대두");
        walnutProductId = setAllergens(listings.get(1), "호두");
        plainProductId = setAllergens(listings.get(2));
    }

    private Long setAllergens(StoreListing listing, String... allergens) {
        listing.getAllergens().clear();
        listing.getAllergens().addAll(Set.of(allergens));
        storeListingRepository.saveAndFlush(listing);
        return listing.getProduct().getId();
    }

    private User userWithAllergies(String username, String... allergies) {
        User u = userRepository.save(User.builder().username(username).nickname(username).password("x").build());
        userProfileRepository.save(UserProfile.builder().user(u).allergies(new ArrayList<>(List.of(allergies))).build());
        return u;
    }

    private ResultActions add(User u, Long productId, boolean confirmed) throws Exception {
        return mockMvc.perform(post("/api/cart/items").with(user(u.getUsername()).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1,\"allergyConfirmed\":" + confirmed + "}"));
    }

    @Test
    void 내_알레르기_성분이_든_상품은_확인_없이는_담기지_않는다() throws Exception {
        User u = userWithAllergies("allergy_milk", "우유");

        add(u, milkProductId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLERGY_CONFIRM_REQUIRED"))
                .andExpect(jsonPath("$.productId").value(milkProductId))
                // 상품의 대두는 내 알레르기가 아니라 빠진다
                .andExpect(jsonPath("$.allergens.length()").value(1))
                .andExpect(jsonPath("$.allergens[0]").value("우유"));
        mockMvc.perform(get("/api/cart").with(user(u.getUsername()).roles("USER")))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 확인하면_담기고_장바구니에_경고가_표시된다() throws Exception {
        User u = userWithAllergies("allergy_ok", "우유");

        add(u, milkProductId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].allergyWarnings[0]").value("우유"));
        // 이미 담긴 상품을 또 담으면(수량 추가) 다시 묻지 않는다
        add(u, milkProductId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void 알레르기_성분이_없는_상품이나_알레르기_설정이_없으면_바로_담긴다() throws Exception {
        User milk = userWithAllergies("allergy_plain", "우유");
        add(milk, plainProductId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].allergyWarnings.length()").value(0));

        User none = userWithAllergies("allergy_none");
        add(none, milkProductId, false).andExpect(status().isOk());
    }

    @Test
    void 견과류로_설정하면_호두가_든_상품도_확인을_받는다() throws Exception {
        User u = userWithAllergies("allergy_nuts", "견과류");

        add(u, walnutProductId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.allergens[0]").value("호두"));
    }
}
