package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.dto.admin.SalesReportResponse;
import org.example.dto.admin.SalesReportResponse.Point;
import org.example.dto.admin.SalesReportResponse.TopProduct;
import org.example.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 기간별 매출: 최근 7일/30일(일별), 최근 12개월(월별).
 * 주문을 기간만큼 읽어서 서버에서 묶는다 - 주문이 아주 많아지면 DB 집계 쿼리로 바꿀 것.
 */
@Service
@RequiredArgsConstructor
public class SalesReportService {

    public static final int TOP_PRODUCTS = 5;

    // 결제가 끝나서 판매된 주문 (반품 신청 중 포함, 전체 취소/반품 완료 제외)
    private static final Set<OrderStatus> PURCHASED = EnumSet.copyOf(
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isPurchased).toList());

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SalesReportResponse report(String period) {
        LocalDate today = LocalDate.now(clock);
        boolean monthly = switch (period) {
            case "7d", "30d" -> false;
            case "12m" -> true;
            default -> throw new IllegalArgumentException("기간은 7d, 30d, 12m 중 하나여야 합니다: " + period);
        };
        LocalDate from = switch (period) {
            case "7d" -> today.minusDays(6);
            case "30d" -> today.minusDays(29);
            default -> YearMonth.from(today).minusMonths(11).atDay(1);
        };

        // 빈 칸도 0으로 보여주기 위해 먼저 모든 칸을 만든다
        Map<LocalDate, BigDecimal> sales = new LinkedHashMap<>();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = monthly ? d.plusMonths(1) : d.plusDays(1)) {
            sales.put(d, BigDecimal.ZERO);
            counts.put(d, 0L);
        }

        Map<String, TopProductAcc> products = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        long orders = 0;
        for (Order order : orderRepository.findByStatusInAndPaidAtGreaterThanEqual(PURCHASED, from.atStartOfDay())) {
            LocalDate paid = order.getPaidAt().toLocalDate();
            if (paid.isAfter(today)) continue;
            LocalDate bucket = monthly ? paid.withDayOfMonth(1) : paid;
            BigDecimal amount = order.netAmount();
            sales.merge(bucket, amount, BigDecimal::add);
            counts.merge(bucket, 1L, Long::sum);
            total = total.add(amount);
            orders++;
            for (OrderItem item : order.getItems()) {
                if (item.isCancelled()) continue;
                products.computeIfAbsent(item.getProductName(), TopProductAcc::new).add(item);
            }
        }

        List<Point> series = new ArrayList<>();
        sales.forEach((start, amount) -> series.add(new Point(label(start, monthly), start, amount, counts.get(start))));
        List<TopProduct> top = products.values().stream()
                .sorted(Comparator.comparing((TopProductAcc p) -> p.sales).reversed().thenComparing(p -> p.name))
                .limit(TOP_PRODUCTS)
                .map(p -> new TopProduct(p.name, p.quantity, p.sales))
                .toList();
        BigDecimal average = orders == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
        return new SalesReportResponse(period, monthly ? "month" : "day", from, today, total, orders, average, series, top);
    }

    private static String label(LocalDate start, boolean monthly) {
        return monthly
                ? String.format("%d.%02d", start.getYear(), start.getMonthValue())
                : start.getMonthValue() + "/" + start.getDayOfMonth();
    }

    private static final class TopProductAcc {
        private final String name;
        private long quantity;
        private BigDecimal sales = BigDecimal.ZERO;

        TopProductAcc(String name) {
            this.name = name;
        }

        void add(OrderItem item) {
            quantity += item.getQuantity();
            sales = sales.add(item.lineAmount());
        }
    }
}
