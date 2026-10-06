package com.techo.auth;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 应用设置的键值存储。目前只用来放登录密码的哈希。
 *
 * <p>放在数据库里而不是配置文件里，有两个好处：
 * 一是密文不落在明文配置文件里，
 * 二是「首次设置密码」可以做成网页流程，不需要用户手工生成哈希再填进 yml。
 */
@Repository
public class SettingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SettingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> get(String key) {
        List<String> values = jdbc.queryForList(
                "SELECT value FROM app_setting WHERE key = :key",
                new MapSqlParameterSource("key", key), String.class);
        return values.stream().findFirst();
    }

    /** 存在就更新，不存在就插入（SQLite 3.24+ 的 UPSERT）。 */
    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO app_setting (key, value, updated_at)
                VALUES (:key, :value, :now)
                ON CONFLICT(key) DO UPDATE SET value = :value, updated_at = :now
                """, new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", value)
                .addValue("now", Instant.now().toEpochMilli()));
    }

    public void delete(String key) {
        jdbc.update("DELETE FROM app_setting WHERE key = :key",
                new MapSqlParameterSource("key", key));
    }
}
