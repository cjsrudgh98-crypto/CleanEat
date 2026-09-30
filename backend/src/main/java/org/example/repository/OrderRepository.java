package org.example.repository;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByOrderedAtDesc(Long userId);
    List<Order> findByUserIdAndStatusInOrderByOrderedAtDesc(Long userId, Collection<OrderStatus> statuses);

    // 내 주문 목록 - 최근 것부터 한 페이지씩 (같은 시각이면 id로 순서 고정)
    Slice<Order> findByUserIdAndStatusInOrderByOrderedAtDescIdDesc(Long userId, Collection<OrderStatus> statuses,
                                                                     Pageable pageable);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    // 영수증 스캔용 - 주문 상품/판매 정보를 한 번에 불러온다 (트랜잭션 밖에서 쓰므로 지연 로딩 불가)
    @Query("select distinct o from Order o left join fetch o.items i left join fetch i.storeListing "
            + "where o.tossOrderId = :tossOrderId")
    Optional<Order> findByTossOrderId(@Param("tossOrderId") String tossOrderId);

    // 가상계좌 입금 반영 - 웹훅 재전송/주기 확인이 동시에 와도 한 번만 처리되도록 행을 잠근다
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.tossOrderId = :tossOrderId")
    Optional<Order> findForUpdateByTossOrderId(@Param("tossOrderId") String tossOrderId);

    // 관리자 반품 승인/거절 - 동시에 두 번 눌러도 환불이 한 번만 되도록 행을 잠근다
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForUpdateById(@Param("id") Long id);

    // 가상계좌 입금 확인 폴링 대상
    List<Order> findByStatus(OrderStatus status);

    // 발송 후 오래 지난 배송중 주문 - 자동 배송 완료 대상
    List<Order> findByStatusAndShippedAtBefore(OrderStatus status, java.time.LocalDateTime before);

    // 오래된 결제대기/결제실패 주문 정리 대상
    List<Order> findByStatusInAndOrderedAtBefore(Collection<OrderStatus> statuses, java.time.LocalDateTime before);

    // 관리자 주문 목록
    Slice<Order> findByStatusInOrderByOrderedAtDescIdDesc(Collection<OrderStatus> statuses, Pageable pageable);

    // 관리자 요약
    long countByStatusIn(Collection<OrderStatus> statuses);

    List<Order> findByStatusInAndPaidAtGreaterThanEqual(Collection<OrderStatus> statuses, java.time.LocalDateTime from);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    // 리뷰 작성 자격: 이 상품을 실제로 구매(결제 완료 이후 상태)한 적이 있는지
    @Query("select count(i) > 0 from OrderItem i where i.order.user.id = :userId "
            + "and i.storeListing.product.id = :productId and i.order.status in :statuses and i.cancelledAt is null")
    boolean hasPurchased(@Param("userId") Long userId, @Param("productId") Long productId,
                         @Param("statuses") Collection<OrderStatus> statuses);
}
