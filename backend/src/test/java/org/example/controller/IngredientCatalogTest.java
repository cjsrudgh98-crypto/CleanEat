package org.example.controller;

import org.example.config.DataSeeder;
import org.example.domain.Ingredient;
import org.example.dto.scan.IngredientMatchResponse;
import org.example.repository.IngredientRepository;
import org.example.service.IngredientRiskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 유해성분 사전 - 시드, 공개 목록, 관리자 추가/수정/사용 중지가 실제 검사에 반영되는지 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngredientCatalogTest {

    private static final String BROMATE = """
            {"name":"브롬산칼륨","riskLevel":"HIGH","category":"기타 첨가물",
             "description":"밀가루 개량제. IARC 2B군.","aliases":["potassium bromate","e924"," "]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngredientRiskService ingredientRiskService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DataSeeder dataSeeder;

    private List<String> harmfulIn(String... ingredients) {
        return ingredientRiskService.assess(List.of(ingredients)).getHarmfulIngredients().stream()
                .map(IngredientMatchResponse::getIngredientName).toList();
    }

    private long idOf(String name) {
        return ingredientRepository.findByNameIgnoreCase(name).map(Ingredient::getId).orElseThrow();
    }

    @Test
    void 공개_사전은_누구나_분류순으로_볼_수_있다() throws Exception {
        mockMvc.perform(get("/api/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(44))
                .andExpect(jsonPath("$[0].category").value("감미료"))
                .andExpect(jsonPath("$[?(@.name == '아스파탐')].aliases[0]").exists());
    }

    @Test
    void 확대된_사전으로_실제_검사에서_새_성분을_찾는다() {
        assertThat(harmfulIn("정제수", "이산화티타늄(착색료)", "E 320", "potassium sorbate"))
                .containsExactly("이산화티타늄", "부틸히드록시아니솔", "소르빈산류");
        assertThat(harmfulIn("아질산나트륨")).containsExactly("아질산나트륨");
    }

    @Test
    void 관리자가_추가한_성분은_다음_검사부터_찾는다() throws Exception {
        assertThat(harmfulIn("Potassium Bromate")).isEmpty();

        mockMvc.perform(post("/api/admin/ingredients").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BROMATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aliases.length()").value(2))
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(harmfulIn("Potassium Bromate")).containsExactly("브롬산칼륨");
        assertThat(ingredientRiskService.assess(List.of("E924")).getOverallRisk().name()).isEqualTo("HIGH");

        // 같은 이름은 또 넣을 수 없다
        mockMvc.perform(post("/api/admin/ingredients").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BROMATE))
                .andExpect(status().isConflict());
    }

    @Test
    void 사용_중지하면_검사와_공개_목록에서_빠지고_다시_켤_수_있다() throws Exception {
        long id = idOf("아스파탐");
        mockMvc.perform(patch("/api/admin/ingredients/" + id + "/enabled").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(harmfulIn("aspartame")).isEmpty();
        mockMvc.perform(get("/api/ingredients")).andExpect(jsonPath("$.length()").value(43));
        mockMvc.perform(get("/api/admin/ingredients").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.length()").value(44));

        mockMvc.perform(patch("/api/admin/ingredients/" + id + "/enabled").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        assertThat(harmfulIn("aspartame")).containsExactly("아스파탐");
    }

    @Test
    void 관리자가_고친_위험도와_별칭이_검사에_반영된다() throws Exception {
        long id = idOf("수크랄로스");
        mockMvc.perform(put("/api/admin/ingredients/" + id).with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수크랄로스\",\"riskLevel\":\"MEDIUM\",\"category\":\"감미료\",\"description\":\"수정\",\"aliases\":[\"splenda\"]}"))
                .andExpect(status().isOk());

        var result = ingredientRiskService.assess(List.of("Splenda"));
        assertThat(result.getHarmfulIngredients()).extracting(IngredientMatchResponse::getIngredientName).containsExactly("수크랄로스");
        assertThat(result.getOverallRisk().name()).isEqualTo("MEDIUM");
        // 별칭을 바꿨으므로 예전 별칭(e955)으로는 안 찾는다. 이름 자체로는 여전히 찾는다
        assertThat(harmfulIn("E955")).isEmpty();
        assertThat(harmfulIn("수크랄로스")).containsExactly("수크랄로스");
    }

    @Test
    void 일반_회원과_비로그인은_사전을_고칠_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/ingredients").with(user("member").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(BROMATE)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/ingredients")
                .contentType(MediaType.APPLICATION_JSON).content(BROMATE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/ingredients").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"riskLevel\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 시드는_다시_돌려도_관리자_수정을_덮어쓰지_않고_빈_별칭만_채운다() throws Exception {
        Ingredient aspartame = ingredientRepository.findByNameIgnoreCase("아스파탐").orElseThrow();
        aspartame.setDescription("관리자가 고친 설명");
        aspartame.setEnabled(false);
        // 예전 버전 DB처럼 별칭/분류가 없는 성분
        Ingredient legacy = ingredientRepository.findByNameIgnoreCase("타르색소").orElseThrow();
        legacy.getAliases().clear();
        legacy.setCategory(null);
        ingredientRepository.saveAndFlush(aspartame);
        ingredientRepository.saveAndFlush(legacy);

        dataSeeder.run();

        assertThat(ingredientRepository.count()).isEqualTo(44);
        Ingredient after = ingredientRepository.findByNameIgnoreCase("아스파탐").orElseThrow();
        assertThat(after.getDescription()).isEqualTo("관리자가 고친 설명");
        assertThat(after.isActive()).isFalse();
        Ingredient filled = ingredientRepository.findByNameIgnoreCase("타르색소").orElseThrow();
        assertThat(filled.getAliases()).contains("타르색소");
        assertThat(filled.getCategory()).isEqualTo("착색료");
    }
}
