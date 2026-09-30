package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.dto.admin.SalesReportResponse;
import org.example.dto.admin.SalesReportResponse.Point;
import org.example.dto.admin.SalesReportResponse.TopProduct;
import org.example.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    // "오늘" = 2026-09-30 (서울)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 9, 30, 15, 0).atZone(SEOUL).toInstant(), SEOUL);

    @Mock private OrderRepository orderRepository;
    private SalesReportService service;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        service = new SalesReportService(orderRepository, CLOCK);
    }

    private Order order(String paidAt, Object... items) {
        Order order = Order.builder().id(nextId++).status(OrderStatus.PAID).paidAt(LocalDateTime.parse(paidAt))
                .totalAmount(BigDecimal.ZERO).build();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.length; i += 3) {
            OrderItem item = OrderItem.builder().id(nextId++).order(order).productName((String) items[i])
                    .unitPrice(new BigDecimal((Integer) items[i + 1])).quantity((Integer) items[i + 2]).build();
            order.getItems().add(item);
            total = total.add(item.lineAmount());
        }
        order.setTotalAmount(total);
        return order;
    }

    @Test
    void 최근_7일은_일별로_빈_날도_0으로_채운다() {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), eq(LocalDate.of(2026, 9, 24).atStartOfDay())))
                .thenReturn(List.of(
                        order("2026-09-24T09:00:00", "현미 과자", 4500, 2),
                        order("2026-09-30T11:00:00", "현미 과자", 4500, 1, "구운 아몬드", 3000, 1),
                        order("2026-09-30T14:00:00", "구운 아몬드", 3000, 1)));

        SalesReportResponse report = service.report("7d");

        assertThat(report.unit()).isEqualTo("day");
        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(report.series()).hasSize(7);
        assertThat(report.series()).extracting(Point::label).startsWith("9/24", "9/25").endsWith("9/30");
        assertThat(report.series().get(0).sales()).isEqualByComparingTo("9000");
        assertThat(report.series().get(1).sales()).isEqualByComparingTo("0");
        assertThat(report.series().get(6)).extracting(p -> p.sales().intValue(), Point::orders).containsExactly(10500, 2L);
        assertThat(report.totalSales()).isEqualByComparingTo("19500");
        assertThat(report.totalOrders()).isEqualTo(3);
        assertThat(report.averageOrderAmount()).isEqualByComparingTo("6500");
    }

    @Test
    void 부분_취소된_금액과_상품은_매출과_인기_상품에서_뺀다() {
        Order partial = order("2026-09-30T10:00:00", "현미 과자", 4500, 2, "구운 아몬드", 3000, 1);
        partial.getItems().get(1).setCancelledAt(LocalDateTime.parse("2026-09-30T12:00:00"));
        partial.setCancelledAmount(new BigDecimal(3000));
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), any())).thenReturn(List.of(partial));

        SalesReportResponse report = service.report("7d");

        assertThat(report.totalSales()).isEqualByComparingTo("9000");
        assertThat(report.topProducts()).extracting(TopProduct::productName).containsExactly("현미 과자");
    }

    @Test
    void 인기_상품은_매출_순으로_최대_5개() {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), any())).thenReturn(List.of(
                order("2026-09-29T10:00:00", "A", 1000, 1, "B", 2000, 3, "C", 500, 1),
                order("2026-09-30T10:00:00", "D", 9000, 1, "E", 100, 1, "F", 300, 1, "B", 2000, 1)));

        List<TopProduct> top = service.report("30d").topProducts();

        assertThat(top).extracting(TopProduct::productName, TopProduct::quantity, p -> p.sales().intValue())
                .containsExactly(tuple("D", 1L, 9000), tuple("B", 4L, 8000), tuple("A", 1L, 1000),
                        tuple("C", 1L, 500), tuple("F", 1L, 300));
    }

    @Test
    void 최근_12개월은_월별로_묶는다() {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), eq(LocalDate.of(2025, 10, 1).atStartOfDay())))
                .thenReturn(List.of(
                        order("2025-10-15T10:00:00", "A", 1000, 1),
                        order("2026-09-01T10:00:00", "A", 1000, 2),
                        order("2026-09-30T10:00:00", "A", 1000, 3)));

        SalesReportResponse report = service.report("12m");

        assertThat(report.unit()).isEqualTo("month");
        assertThat(report.series()).hasSize(12);
        assertThat(report.series()).extracting(Point::label).startsWith("2025.10").endsWith("2026.09");
        assertThat(report.series().get(0).sales()).isEqualByComparingTo("1000");
        assertThat(report.series().get(11)).extracting(p -> p.sales().intValue(), Point::orders).containsExactly(5000, 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 전체_취소와_반품_완료는_매출에_넣지_않는다() {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), any())).thenReturn(List.of());
        service.report("7d");

        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).findByStatusInAndPaidAtGreaterThanEqual(statuses.capture(), any());
        assertThat(statuses.getValue()).contains(OrderStatus.PAID, OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED)
                .doesNotContain(OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 주문이_없으면_모두_0이고_객단가도_0() {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqual(any(), any())).thenReturn(List.of());
        SalesReportResponse report = service.report("30d");
        assertThat(report.series()).hasSize(30);
        assertThat(report.totalSales()).isEqualByComparingTo("0");
        assertThat(report.averageOrderAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 없는_기간은_400() {
        assertThatThrownBy(() -> service.report("1y")).isInstanceOf(IllegalArgumentException.class);
    }
}
