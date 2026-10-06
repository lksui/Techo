package com.techo.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 数据库快照。
 *
 * <p><b>为什么是 VACUUM INTO 而不是复制文件：</b>
 * 应用开启的是 WAL 模式，最新的写入在 {@code journal.db-wal} 里。
 * 直接复制 {@code journal.db} 会丢掉最近的改动，甚至可能拿到不一致的半截数据。
 * {@code VACUUM INTO} 会生成一个完整、一致、且已整理过的独立数据库文件。
 *
 * <p><b>为什么不用 {@code .backup} 命令或 Online Backup API：</b>
 * 那些更适合「备份到已存在的目标」；这里每次生成一个新时间戳文件，
 * {@code VACUUM INTO} 一条 SQL 就够了，滚动保留旧快照也更直观。
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbc;
    private final Path dir;
    private final int keep;

    public SnapshotService(JdbcTemplate jdbc,
                           @Value("${techo.backup.dir}") String dir,
                           @Value("${techo.backup.keep:30}") int keep) {
        this.jdbc = jdbc;
        this.dir = Path.of(dir);
        this.keep = Math.max(1, keep);
    }

    /** 生成一份快照，返回文件路径。 */
    public Path snapshot() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建备份目录：" + dir, e);
        }

        Path target = dir.resolve("techo-" + STAMP.format(Instant.now()) + ".db");
        if (Files.exists(target)) {
            // 同一秒内重复触发。VACUUM INTO 要求目标不存在，直接返回已有的那份。
            return target;
        }

        String sqlPath = target.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
        jdbc.execute("VACUUM INTO '" + sqlPath + "'");
        log.info("已生成数据库快照：{}", target.getFileName());
        return target;
    }

    /** 按时间倒序列出所有快照。 */
    public List<Path> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".db"))
                    .sorted(Comparator.comparingLong(SnapshotService::lastModified).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("读取备份目录失败：{}", dir, e);
            return List.of();
        }
    }

    /** 只保留最近 keep 份，删掉多余的。返回删掉的份数。 */
    public int prune() {
        List<Path> all = new ArrayList<>(list());
        if (all.size() <= keep) {
            return 0;
        }
        int removed = 0;
        for (Path path : all.subList(keep, all.size())) {
            try {
                Files.deleteIfExists(path);
                removed++;
            } catch (IOException e) {
                log.warn("删除旧快照失败：{}", path, e);
            }
        }
        if (removed > 0) {
            log.info("清理旧快照 {} 份，保留最近 {} 份", removed, keep);
        }
        return removed;
    }

    public Path getDir() {
        return dir;
    }

    public int getKeep() {
        return keep;
    }

    public static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    public static String displayName(Path path) {
        String name = path.getFileName().toString();
        // techo-20260305-033000.db -> 2026-03-05 03:30
        if (name.length() >= 21) {
            return name.substring(6, 10) + "-" + name.substring(10, 12) + "-" + name.substring(12, 14)
                    + " " + name.substring(15, 17) + ":" + name.substring(17, 19);
        }
        return name;
    }
}
