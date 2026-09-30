package org.example.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(length = 1000)
    private String description;

    // 분류 (감미료, 착색료, 보존료 ...) - 성분 사전 화면에서 묶어 보여줄 때 사용
    @Column(length = 30)
    private String category;

    // 성분표에서 이 성분을 찾을 때 쓰는 다른 표기 (영어/프랑스어 이름, E-번호, "황색제4호" 같은 한국어 표기)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ingredient_aliases", joinColumns = @JoinColumn(name = "ingredient_id"))
    @Column(name = "alias", length = 100)
    @Builder.Default
    private Set<String> aliases = new HashSet<>();

    // false면 검사에서 찾지 않는다 (관리자가 "사용 중지"). 컬럼 추가 전 데이터는 null -> 사용 중으로 본다.
    // 삭제 대신 쓰는 이유: 기본 사전 성분은 지워도 서버 재시작 때 시드 파일에서 다시 들어오기 때문
    private Boolean enabled;

    public boolean isActive() {
        return !Boolean.FALSE.equals(enabled);
    }
}
