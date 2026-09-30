-- V4: 품절 상품 재입고 알림 신청 (회원 한 명이 상품 하나에 한 번, 메일 보내면 삭제)
create table restock_alerts (
    created_at datetime(6),
    id bigint not null auto_increment,
    store_listing_id bigint not null,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;
alter table restock_alerts add constraint uk_restock_alerts_user_listing unique (user_id, store_listing_id);
alter table restock_alerts add constraint fk_restock_alerts_listing foreign key (store_listing_id) references store_listings (id);
alter table restock_alerts add constraint fk_restock_alerts_user foreign key (user_id) references users (id);
