package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.dto.admin.AdminSummaryResponse;
import org.example.repository.OrderRepository;
import org.example.repository.StoreListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminSummaryService {

    public static final int LOW_STOCK_THRESHOLD = 5;

    // 결제는 끝났고 이후 취소되지 않은 주문
    private static final Set<OrderStatus> PURCHASED = EnumSet.copyOf(
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isPurchased).toList());

    private final OrderRepository orderRepository;
    private final StoreListingRepository storeListingRepository;

    @Transactional(readOnly = true)
    public AdminSummaryResponse summary() {
        List<Order> today = orderRepository.findByStatusInAndPaidAtGreaterThanEqual(
                PURCHASED, LocalDate.now().atStartOfDay());
        BigDecimal todaySales = today.stream().map(Order::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminSummaryResponse(
                // PLACED(결제 연동 전 주문)도 배송 준비를 시작해야 하는 주문이다
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.PAID, OrderStatus.PLACED)),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.PREPARING)),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.SHIPPING)),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.AWAITING_DEPOSIT)),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.RETURN_REQUESTED)),
                storeListingRepository.countByStockBetween(1, LOW_STOCK_THRESHOLD),
                storeListingRepository.countByStockLessThanEqual(0),
                LOW_STOCK_THRESHOLD,
                todaySales,
                today.size());
    }
}
