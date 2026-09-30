-- V3: 부분 취소 (주문 안의 상품 한 줄만 취소/환불). 기존 데이터는 NULL = 부분 취소 없음
alter table order_items add column cancelled_at timestamp(6);
alter table orders add column cancelled_amount numeric(10,0);
