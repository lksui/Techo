package com.techo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 轻量级结构迁移。
 *
 * <p>{@code schema.sql} 里用的是 {@code CREATE TABLE IF NOT EXISTS} —— 对**已经存在**的表
 * 不会做任何改动。所以给老数据库补列必须单独处理，否则升级后应用会直接因为缺列而报错。
 *
 * <p><b>为什么索引也放在这里，而不是 {@code schema.sql}：</b>
 * {@code schema.sql} 由 Spring 在启动早期执行，这个迁移器是 {@code ApplicationRunner}，
 * 跑得更晚。如果在 schema.sql 里给一个「老库还没有的列」建索引，执行顺序就是
 * 「先建索引 → 后补列」，索引创建失败，**整个应用启动不了**。
 *
 * <p>这个坑只在用老数据库启动时才会暴露，用新建的空库测永远发现不了。
 *
 * <p>没有引入 Flyway / Liquibase：单人应用、就一张表，一个「缺什么补什么」的循环
 * 比引入一套迁移框架更省心。真到了结构复杂到需要版本管理的那天再换不迟。
 */
@Component
@Order(0)
public class SchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 顺序很重要：先补列，再建依赖这些列的索引。
        addColumnIfMissing("entry", "deleted_at", "INTEGER");
        addColumnIfMissing("entry", "parent_id", "INTEGER");
        addColumnIfMissing("entry", "sort_order", "INTEGER NOT NULL DEFAULT 0");

        createIndexIfMissing("idx_entry_deleted",
                "CREATE INDEX idx_entry_deleted ON entry (deleted_at)");
        createIndexIfMissing("idx_entry_parent",
                "CREATE INDEX idx_entry_parent ON entry (parent_id, sort_order)");
    }

    private void addColumnIfMissing(String table, String column, String type) {
        // pragma_table_info 是 SQLite 3.16+ 的表值函数，可以直接当表查
        List<String> columns = jdbc.queryForList(
                "SELECT name FROM pragma_table_info('" + table + "')", String.class);

        if (columns.contains(column)) {
            return;
        }
        jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        log.info("结构迁移：为表 {} 添加列 {} {}", table, column, type);
    }

    private void createIndexIfMissing(String indexName, String ddl) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                Integer.class, indexName);

        if (existing != null && existing > 0) {
            return;
        }
        jdbc.execute(ddl);
        log.info("结构迁移：创建索引 {}", indexName);
    }
}
