-- V1: CleanEat 초기 스키마 (mysql)
-- 엔티티 기준으로 Hibernate가 만든 CREATE 문에서 열거형 CHECK 제약(col in ('A','B'))만 뺐다.
-- 열거형 컬럼은 일반 문자열(VARCHAR)로 두어 주문 상태 등에 값을 추가해도 DB를 고칠 필요가 없게 한다
-- (예전 LegacySchemaMigrator가 기존 DB에서 지우던 것과 같은 이유).
-- 이미 테이블이 있는 DB는 이 파일을 실행하지 않고 V1으로 표시만 한다 (spring.flyway.baseline-on-migrate).
-- 스키마를 바꿀 때는 이 파일을 고치지 말고 V2__설명.sql 처럼 새 파일을 벤더별로 추가할 것.

create table allergens (
    id bigint not null auto_increment,
    name varchar(100) not null,
    description varchar(1000),
    primary key (id)
) engine=InnoDB;

create table cart_items (
    quantity integer not null,
    cart_id bigint not null,
    id bigint not null auto_increment,
    store_listing_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table carts (
    id bigint not null auto_increment,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table email_verifications (
    failed_attempts integer not null,
    created_at datetime(6),
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    verified_token_expires_at datetime(6),
    purpose varchar(20) not null,
    code_hash varchar(64) not null,
    verified_token_hash varchar(64),
    email varchar(100) not null,
    primary key (id)
) engine=InnoDB;

create table favorites (
    created_at datetime(6),
    id bigint not null auto_increment,
    store_listing_id bigint not null,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table ingredient_aliases (
    ingredient_id bigint not null,
    alias varchar(100)
) engine=InnoDB;

create table ingredients (
    enabled bit,
    id bigint not null auto_increment,
    risk_level varchar(20) not null,
    category varchar(30),
    name varchar(100) not null,
    description varchar(1000),
    primary key (id)
) engine=InnoDB;

create table order_items (
    quantity integer not null,
    unit_price decimal(10,0) not null,
    id bigint not null auto_increment,
    order_id bigint not null,
    store_listing_id bigint,
    product_name varchar(200) not null,
    primary key (id)
) engine=InnoDB;

create table orders (
    total_amount decimal(10,0) not null,
    cancelled_at datetime(6),
    delivered_at datetime(6),
    id bigint not null auto_increment,
    ordered_at datetime(6),
    paid_at datetime(6),
    preparing_at datetime(6),
    shipped_at datetime(6),
    user_id bigint not null,
    virtual_account_due_date datetime(6),
    payment_method varchar(20) not null,
    status varchar(20) not null,
    courier varchar(30),
    phone varchar(30) not null,
    virtual_account_bank varchar(30),
    recipient_name varchar(50) not null,
    tracking_number varchar(50),
    virtual_account_number varchar(50),
    toss_order_id varchar(64),
    order_name varchar(100),
    virtual_account_secret varchar(100),
    address varchar(200) not null,
    payment_key varchar(200),
    fail_reason varchar(300),
    request_note varchar(300),
    receipt_url varchar(500),
    primary key (id)
) engine=InnoDB;

create table product_ingredients (
    ingredient_id bigint not null,
    product_id bigint not null,
    primary key (ingredient_id, product_id)
) engine=InnoDB;

create table products (
    created_at datetime(6),
    id bigint not null auto_increment,
    barcode varchar(50) not null,
    name varchar(200) not null,
    raw_ingredients_text varchar(2000),
    primary key (id)
) engine=InnoDB;

create table reviews (
    rating integer not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    product_id bigint not null,
    updated_at datetime(6),
    user_id bigint not null,
    content varchar(500),
    primary key (id)
) engine=InnoDB;

create table scan_histories (
    id bigint not null auto_increment,
    product_id bigint,
    scanned_at datetime(6),
    user_id bigint not null,
    overall_risk varchar(20) not null,
    matched_allergens varchar(1000),
    matched_harmful_ingredients varchar(1000),
    raw_ingredients_text varchar(2000),
    primary key (id)
) engine=InnoDB;

create table store_listing_allergens (
    store_listing_id bigint not null,
    allergen varchar(30)
) engine=InnoDB;

create table store_listing_diets (
    store_listing_id bigint not null,
    diet_type varchar(20)
) engine=InnoDB;

create table store_listings (
    price decimal(10,0) not null,
    stock integer not null,
    id bigint not null auto_increment,
    product_id bigint not null,
    category varchar(20),
    description varchar(500),
    image_url varchar(500),
    primary key (id)
) engine=InnoDB;

create table user_profile_allergies (
    user_profile_id bigint not null,
    allergy_name varchar(255)
) engine=InnoDB;

create table user_profile_diets (
    user_profile_id bigint not null,
    diet_type varchar(20)
) engine=InnoDB;

create table user_profiles (
    id bigint not null auto_increment,
    user_id bigint not null,
    diet_type varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table users (
    birth_date date,
    token_version integer,
    created_at datetime(6),
    deleted_at datetime(6),
    id bigint not null auto_increment,
    password_reset_expires_at datetime(6),
    phone varchar(20),
    provider varchar(20),
    role varchar(20) not null,
    name varchar(50),
    nickname varchar(50) not null,
    username varchar(50) not null,
    password_reset_token_hash varchar(64),
    email varchar(100),
    provider_id varchar(100),
    password varchar(255) not null,
    primary key (id)
) engine=InnoDB;

alter table allergens 
   add constraint UK1tipxa3jfm7x5gcbqlmn6h7on unique (name);

alter table cart_items 
   add constraint uk_cart_items_cart_listing unique (cart_id, store_listing_id);

alter table carts 
   add constraint UK64t7ox312pqal3p7fg9o503c2 unique (user_id);

create index idx_email_verifications_email 
   on email_verifications (email, purpose);

alter table favorites 
   add constraint uk_favorites_user_listing unique (user_id, store_listing_id);

alter table ingredients 
   add constraint UKj6tsl15xx76y4kv41yxr4uxab unique (name);

alter table orders 
   add constraint UK9gx0ixtj8vl9dul8o9wt99edy unique (toss_order_id);

alter table products 
   add constraint UKqfr8vf85k3q1xinifvsl1eynf unique (barcode);

alter table reviews 
   add constraint uk_reviews_user_product unique (user_id, product_id);

alter table store_listings 
   add constraint UKcellgvplrere54mgk99u48beq unique (product_id);

alter table user_profiles 
   add constraint UKe5h89rk3ijvdmaiig4srogdc6 unique (user_id);

alter table users 
   add constraint uk_users_provider_id unique (provider, provider_id);

alter table users 
   add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table cart_items 
   add constraint FKpcttvuq4mxppo8sxggjtn5i2c 
   foreign key (cart_id) 
   references carts (id);

alter table cart_items 
   add constraint FKlo0g161m8lod3k9i243748h11 
   foreign key (store_listing_id) 
   references store_listings (id);

alter table carts 
   add constraint FKb5o626f86h46m4s7ms6ginnop 
   foreign key (user_id) 
   references users (id);

alter table favorites 
   add constraint FKnmihgdewastn7nnwcnvrp5g1p 
   foreign key (store_listing_id) 
   references store_listings (id);

alter table favorites 
   add constraint FKk7du8b8ewipawnnpg76d55fus 
   foreign key (user_id) 
   references users (id);

alter table ingredient_aliases 
   add constraint FK4m9kha18yqm9ojrsvm6jkugcb 
   foreign key (ingredient_id) 
   references ingredients (id);

alter table order_items 
   add constraint FKbioxgbv59vetrxe0ejfubep1w 
   foreign key (order_id) 
   references orders (id);

alter table order_items 
   add constraint FK7hxeb60joh2rvr6ijwcmiu7jf 
   foreign key (store_listing_id) 
   references store_listings (id);

alter table orders 
   add constraint FK32ql8ubntj5uh44ph9659tiih 
   foreign key (user_id) 
   references users (id);

alter table product_ingredients 
   add constraint FKgwkoppq4tgrpjn63yi9gpeg9p 
   foreign key (ingredient_id) 
   references ingredients (id);

alter table product_ingredients 
   add constraint FKa69i4fo6fys3gt2cbrxsrbn4 
   foreign key (product_id) 
   references products (id);

alter table reviews 
   add constraint FKpl51cejpw4gy5swfar8br9ngi 
   foreign key (product_id) 
   references products (id);

alter table reviews 
   add constraint FKcgy7qjc1r99dp117y9en6lxye 
   foreign key (user_id) 
   references users (id);

alter table scan_histories 
   add constraint FKkb12c22uqb8mdhe0nugw4ynpg 
   foreign key (product_id) 
   references products (id);

alter table scan_histories 
   add constraint FK406aovit7qx6rnes5s80f8dgf 
   foreign key (user_id) 
   references users (id);

alter table store_listing_allergens 
   add constraint FK97t67stqi25svv8tqcfish4mq 
   foreign key (store_listing_id) 
   references store_listings (id);

alter table store_listing_diets 
   add constraint FKhmpyr98mu9evrvqfcn7umc2ig 
   foreign key (store_listing_id) 
   references store_listings (id);

alter table store_listings 
   add constraint FKimd18pon9i79y04q0putiuiuf 
   foreign key (product_id) 
   references products (id);

alter table user_profile_allergies 
   add constraint FK1a9wmcyj2o1bwcl93nnj1vhc7 
   foreign key (user_profile_id) 
   references user_profiles (id);

alter table user_profile_diets 
   add constraint FKg1co65e3flfrdl1iv8p1ov5a2 
   foreign key (user_profile_id) 
   references user_profiles (id);

alter table user_profiles 
   add constraint FKjcad5nfve11khsnpwj1mv8frj 
   foreign key (user_id) 
   references users (id);
