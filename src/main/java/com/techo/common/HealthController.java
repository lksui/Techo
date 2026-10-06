package com.techo.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。除了确认服务活着，还顺便验证 SQLite 的关键 PRAGMA 是否真的生效
 * —— journal_mode 必须是 wal，否则手机和电脑同时访问会发生读写阻塞。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("time", Instant.now().toString());
        result.put("javaVersion", System.getProperty("java.version"));
        try {
            result.put("sqliteVersion", jdbc.queryForObject("SELECT sqlite_version()", String.class));
            String journalMode = jdbc.queryForObject("PRAGMA journal_mode", String.class);
            result.put("journalMode", journalMode);
            result.put("journalModeOk", "wal".equalsIgnoreCase(journalMode));
            result.put("busyTimeout", jdbc.queryForObject("PRAGMA busy_timeout", Integer.class));
            result.put("foreignKeys", jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
            result.put("entryCount", jdbc.queryForObject("SELECT COUNT(*) FROM entry", Long.class));
        } catch (Exception e) {
            result.put("database", "ERROR: " + e.getMessage());
        }
        return result;
    }
}
