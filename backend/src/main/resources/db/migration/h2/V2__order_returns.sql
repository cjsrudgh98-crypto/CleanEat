-- V2: 반품 신청 (주문 상태 RETURN_REQUESTED / RETURNED 추가 - 상태 컬럼은 varchar라 DB 변경 없음)
-- 기존 주문에는 모두 NULL (반품 이력 없음). 세 DB가 같은 문법이 되도록 컬럼마다 한 문장씩.
alter table orders add column return_reason varchar(300);
alter table orders add column return_requested_at timestamp(6);
alter table orders add column return_reject_reason varchar(300);
alter table orders add column return_rejected_at timestamp(6);
alter table orders add column returned_at timestamp(6);
alter table orders add column return_refund_bank varchar(2);
alter table orders add column return_refund_account varchar(30);
alter table orders add column return_refund_holder varchar(20);
