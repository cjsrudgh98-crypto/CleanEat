package org.example.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본(EAGER)이면 상품 목록처럼 식단/알레르기만 필요한 곳에서도 회원을 매번 같이 조회한다
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ElementCollection
    @CollectionTable(name = "user_profile_allergies", joinColumns = @JoinColumn(name = "user_profile_id"))
    @Column(name = "allergy_name")
    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    /**
     * 예전(식단 1개만 고르던 시절) 컬럼. 이미 만들어진 DB에 NOT NULL로 남아 있어서 매핑은 유지하고,
     * 새로 저장할 때는 항상 NONE으로 둔다. 여러 식단은 dietTypes에 저장한다.
     * 읽을 때는 getEffectiveDietTypes()를 쓸 것 - 예전에 저장된 값도 이어서 보여준다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "diet_type", nullable = false, length = 20)
    @Builder.Default
    private DietType dietType = DietType.NONE;

    // 선택한 식단들 (비어 있으면 "제한 없음"). 상품은 여기 있는 식단을 모두 만족해야 맞는 것으로 본다
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile_diets", joinColumns = @JoinColumn(name = "user_profile_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "diet_type", length = 20)
    @Builder.Default
    private Set<DietType> dietTypes = new HashSet<>();

    public Set<DietType> getEffectiveDietTypes() {
        if (!dietTypes.isEmpty()) return EnumSet.copyOf(dietTypes);
        if (dietType != null && dietType != DietType.NONE) return EnumSet.of(dietType);
        return EnumSet.noneOf(DietType.class);
    }

    public void replaceDietTypes(Set<DietType> selected) {
        dietTypes.clear();
        selected.stream().filter(d -> d != DietType.NONE).forEach(dietTypes::add);
        dietType = DietType.NONE;
    }
}
