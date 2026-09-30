package org.example.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 품절 상품 재입고 알림 신청 (회원 한 명이 상품 하나에 한 번). 재입고 메일을 보내면 지운다 (한 번만 알림).
 */
@Entity
@Table(name = "restock_alerts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_restock_alerts_user_listing", columnNames = {"user_id", "store_listing_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "store_listing_id", nullable = false)
    private StoreListing storeListing;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
