package org.example.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 예전에 만들어진 DB 스키마를 현재 엔티티에 맞게 고친다 (기동 시 한 번, 이미 고쳐져 있으면 아무것도 안 함).
 *
 * Hibernate가 열거형(@Enumerated) 컬럼을 예전에는
 *  - MySQL: ENUM('A','B') 네이티브 타입
 *  - PostgreSQL: VARCHAR + CHECK (col IN ('A','B')) 제약
 * 으로 만들었다. 이러면 열거형에 값(주문 상태, 카테고리 등)을 추가했을 때 ddl-auto=update가 이를 고치지 않아
 * 새 값을 저장하는 순간 오류가 난다. 지금은 엔티티에 @JdbcTypeCode(VARCHAR)를 붙여 일반 문자열로 저장하고,
 * 이미 만들어진 DB는 여기서 VARCHAR로 바꾸거나 값 목록 제약을 지운다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacySchemaMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySchemaMigrator.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        }
        if (product.contains("mysql") || product.contains("mariadb")) {
            convertMySqlEnumColumns();
        } else if (product.contains("postgres")) {
            dropPostgresEnumChecks();
        }
        // H2(개발/테스트)는 매번 새로 만들어지므로 고칠 게 없다
    }

    private void convertMySqlEnumColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'");
        for (Map<String, Object> column : columns) {
            String table = (String) column.get("TABLE_NAME");
            String name = (String) column.get("COLUMN_NAME");
            String nullable = "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL";
            jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + name + "` VARCHAR(30) " + nullable);
            log.info("MySQL ENUM 컬럼을 VARCHAR로 변환: {}.{}", table, name);
        }
    }

    private void dropPostgresEnumChecks() {
        // Hibernate가 만든 열거형 제약은 "컬럼 = ANY (ARRAY['A','B'])" 모양이다 (NOT NULL 등 다른 제약은 건드리지 않음)
        List<Map<String, Object>> checks = jdbcTemplate.queryForList(
                "SELECT tc.table_name, tc.constraint_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.check_constraints cc "
                        + "  ON cc.constraint_name = tc.constraint_name AND cc.constraint_schema = tc.constraint_schema "
                        + "WHERE tc.constraint_type = 'CHECK' AND tc.table_schema = current_schema() "
                        + "  AND cc.check_clause LIKE '%ANY (ARRAY[%'");
        for (Map<String, Object> check : checks) {
            String table = (String) check.get("table_name");
            String name = (String) check.get("constraint_name");
            jdbcTemplate.execute("ALTER TABLE \"" + table + "\" DROP CONSTRAINT IF EXISTS \"" + name + "\"");
            log.info("PostgreSQL 열거형 CHECK 제약 제거: {}.{}", table, name);
        }
    }
}
