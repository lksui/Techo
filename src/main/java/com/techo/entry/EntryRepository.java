package com.techo.entry;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 条目的数据访问层。只负责 SQL，不含任何业务判断。
 *
 * <p><b>删除是软删除</b>：把 {@code deleted_at} 写上时间戳，行还在表里。
 * 所有「正常」查询都带 {@code deleted_at IS NULL}，回收站相关的方法是单独的一组。
 *
 * <p><b>子目录复用同一张表</b>：{@code parent_id} 非 NULL 就是子目录。
 * 这样回收站、导出、时间戳、软删除全部自动共享，不用为子目录再写一套。
 * 顶层查询据此加上 {@code parent_id IS NULL}。
 */
@Repository
public class EntryRepository {

    private static final String COLUMNS =
            "id, type, title, body, done, done_at, created_at, updated_at,"
                    + " deleted_at, parent_id, sort_order";

    /** 正常查询统一附加的条件 */
    private static final String NOT_DELETED = " AND deleted_at IS NULL";

    /** 只要顶层条目（排除子目录） */
    private static final String TOP_LEVEL = " AND parent_id IS NULL";

    private final NamedParameterJdbcTemplate jdbc;

    public EntryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- 顶层查询

    public List<Entry> find(EntryType type, String keyword, StatusFilter status, int limit, int offset) {
        Filters filters = buildFilters(type, keyword, status);
        filters.params.addValue("limit", limit).addValue("offset", offset);
        String sql = "SELECT " + COLUMNS
                + " FROM entry" + filters.where + TOP_LEVEL + NOT_DELETED
                + " ORDER BY " + orderBy(status)
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, filters.params, ROW_MAPPER);
    }

    public long count(EntryType type, String keyword, StatusFilter status) {
        Filters filters = buildFilters(type, keyword, status);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entry" + filters.where + TOP_LEVEL + NOT_DELETED,
                filters.params, Long.class);
        return total == null ? 0L : total;
    }

    /** 按 id 查。子目录也能查到（子目录操作需要它）。 */
    public Optional<Entry> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM entry WHERE id = :id" + NOT_DELETED;
        return jdbc.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    /** 导出用：全部未删除条目（含子目录），按创建时间正序。 */
    public List<Entry> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM entry WHERE 1 = 1" + NOT_DELETED
                + " ORDER BY created_at ASC, id ASC";
        return jdbc.query(sql, new MapSqlParameterSource(), ROW_MAPPER);
    }

    /** 顶层条目数（不含子目录）。用于管理页统计和「没数据就不备份」的判断。 */
    public long countAll() {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entry WHERE 1 = 1" + TOP_LEVEL + NOT_DELETED,
                new MapSqlParameterSource(), Long.class);
        return total == null ? 0L : total;
    }

    // ---------------------------------------------------------------- 子目录

    public List<Entry> findSubtasks(long parentId) {
        String sql = "SELECT " + COLUMNS + " FROM entry WHERE parent_id = :pid" + NOT_DELETED
                + " ORDER BY sort_order ASC, id ASC";
        return jdbc.query(sql, new MapSqlParameterSource("pid", parentId), ROW_MAPPER);
    }

    /** 一次把多条待办的子目录全取出来，避免逐条查询造成 N+1。 */
    public List<Entry> findSubtasksByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + COLUMNS + " FROM entry WHERE parent_id IN (:ids)" + NOT_DELETED
                + " ORDER BY parent_id ASC, sort_order ASC, id ASC";
        return jdbc.query(sql, new MapSqlParameterSource("ids", parentIds), ROW_MAPPER);
    }

    public long countSubtasks(long parentId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entry WHERE parent_id = :pid" + NOT_DELETED,
                new MapSqlParameterSource("pid", parentId), Long.class);
        return n == null ? 0L : n;
    }

    public long countDoneSubtasks(long parentId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entry WHERE parent_id = :pid AND done = 1" + NOT_DELETED,
                new MapSqlParameterSource("pid", parentId), Long.class);
        return n == null ? 0L : n;
    }

    /** 附加到末尾时用的排序号。 */
    public int nextSortOrder(long parentId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM entry WHERE parent_id = :pid",
                new MapSqlParameterSource("pid", parentId), Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    public Integer sortOrderOf(long id) {
        List<Integer> values = jdbc.queryForList(
                "SELECT sort_order FROM entry WHERE id = :id",
                new MapSqlParameterSource("id", id), Integer.class);
        return values.stream().findFirst().orElse(null);
    }

    /** 要在某条之后插入时，先把它后面的统统往后挪一位。 */
    public int shiftSortOrdersAfter(long parentId, int fromOrder) {
        return jdbc.update(
                "UPDATE entry SET sort_order = sort_order + 1"
                        + " WHERE parent_id = :pid AND sort_order > :from",
                new MapSqlParameterSource().addValue("pid", parentId).addValue("from", fromOrder));
    }

    /** 父级被彻底删除时，把它的子目录一并清掉，避免留下孤儿数据。 */
    public int purgeSubtasks(long parentId) {
        return jdbc.update("DELETE FROM entry WHERE parent_id = :pid",
                new MapSqlParameterSource("pid", parentId));
    }

    // ---------------------------------------------------------------- 回收站

    /**
     * 回收站列表。左连接父级是为了给子目录显示「属于哪条待办」，
     * 用一条 SQL 拿完，不做 N+1。
     */
    public List<Entry> findDeleted(int limit, int offset) {
        String sql = "SELECT e.id, e.type, e.title, e.body, e.done, e.done_at, e.created_at,"
                + " e.updated_at, e.deleted_at, e.parent_id, e.sort_order,"
                + " p.body AS parent_body"
                + " FROM entry e LEFT JOIN entry p ON p.id = e.parent_id"
                + " WHERE e.deleted_at IS NOT NULL"
                + " ORDER BY e.deleted_at DESC, e.id DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql,
                new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset),
                TRASH_ROW_MAPPER);
    }

    public long countDeleted() {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM entry WHERE deleted_at IS NOT NULL",
                new MapSqlParameterSource(), Long.class);
        return total == null ? 0L : total;
    }

    /** 不区分是否已删除，供恢复 / 彻底删除使用。 */
    public Optional<Entry> findAnyById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM entry WHERE id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    // ---------------------------------------------------------------- 写入

    public Entry insert(Entry entry) {
        String sql = """
                INSERT INTO entry
                    (type, title, body, done, done_at, created_at, updated_at,
                     deleted_at, parent_id, sort_order)
                VALUES
                    (:type, :title, :body, :done, :doneAt, :createdAt, :updatedAt,
                     NULL, :parentId, :sortOrder)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", entry.getType().name())
                .addValue("title", entry.getTitle())
                .addValue("body", entry.getBody())
                .addValue("done", entry.isDone() ? 1 : 0)
                .addValue("doneAt", toMillis(entry.getDoneAt()))
                .addValue("createdAt", toMillis(entry.getCreatedAt()))
                .addValue("updatedAt", toMillis(entry.getUpdatedAt()))
                .addValue("parentId", entry.getParentId())
                .addValue("sortOrder", entry.getSortOrder());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入成功但未能取回自增主键");
        }
        entry.setId(key.longValue());
        return entry;
    }

    /** 切换完成状态。取消完成时 doneAt 传 null。 */
    public int updateDone(long id, boolean done, Instant doneAt, Instant updatedAt) {
        String sql = """
                UPDATE entry
                   SET done = :done, done_at = :doneAt, updated_at = :updatedAt
                 WHERE id = :id
                """ + NOT_DELETED;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("done", done ? 1 : 0)
                .addValue("doneAt", toMillis(doneAt))
                .addValue("updatedAt", toMillis(updatedAt));
        return jdbc.update(sql, params);
    }

    /** 修改内容。故意不动 created_at。 */
    public int updateContent(long id, String title, String body, Instant updatedAt) {
        String sql = """
                UPDATE entry
                   SET title = :title, body = :body, updated_at = :updatedAt
                 WHERE id = :id
                """ + NOT_DELETED;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", title)
                .addValue("body", body)
                .addValue("updatedAt", toMillis(updatedAt));
        return jdbc.update(sql, params);
    }

    /** 软删除：进回收站。已经删过的不会再覆盖时间。 */
    public int softDelete(long id, Instant now) {
        String sql = "UPDATE entry SET deleted_at = :now, updated_at = :now"
                + " WHERE id = :id AND deleted_at IS NULL";
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id).addValue("now", now.toEpochMilli()));
    }

    /** 从回收站恢复。 */
    public int restore(long id) {
        String sql = "UPDATE entry SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL";
        return jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    /** 彻底删除。只允许删回收站里的，避免误调用把正常数据抹掉。 */
    public int purge(long id) {
        String sql = "DELETE FROM entry WHERE id = :id AND deleted_at IS NOT NULL";
        return jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    public int purgeAllDeleted() {
        // 先把所有已删除条目的子目录清掉，再删条目本身，避免留下孤儿
        jdbc.update("DELETE FROM entry WHERE parent_id IN"
                + " (SELECT id FROM entry WHERE deleted_at IS NOT NULL)",
                new MapSqlParameterSource());
        return jdbc.update("DELETE FROM entry WHERE deleted_at IS NOT NULL",
                new MapSqlParameterSource());
    }

    // ---------------------------------------------------------------- 内部工具

    private record Filters(String where, MapSqlParameterSource params) {
    }

    private static Filters buildFilters(EntryType type, String keyword, StatusFilter status) {
        StringBuilder where = new StringBuilder(" WHERE type = :type");
        MapSqlParameterSource params = new MapSqlParameterSource("type", type.name());

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (title LIKE :kw ESCAPE '\\' OR body LIKE :kw ESCAPE '\\')");
            params.addValue("kw", "%" + escapeLike(keyword.trim()) + "%");
        }
        if (status == StatusFilter.OPEN) {
            where.append(" AND done = 0");
        } else if (status == StatusFilter.DONE) {
            where.append(" AND done = 1");
        }
        return new Filters(where.toString(), params);
    }

    private static String orderBy(StatusFilter status) {
        return status == StatusFilter.DONE
                ? "done_at DESC, id DESC"
                : "created_at DESC, id DESC";
    }

    /**
     * 转义 LIKE 的通配符。
     * 不转义的话，用户搜 {@code 50%} 会匹配到所有含 {@code 50} 的内容 —— 结果多到没人看得出来。
     */
    static String escapeLike(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Long toMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static Entry mapRow(ResultSet rs) throws SQLException {
        Entry entry = new Entry();
        entry.setId(rs.getLong("id"));
        entry.setType(EntryType.valueOf(rs.getString("type")));
        entry.setTitle(rs.getString("title"));
        entry.setBody(rs.getString("body"));
        entry.setDone(rs.getInt("done") == 1);
        entry.setDoneAt(readInstant(rs, "done_at"));
        entry.setCreatedAt(readInstant(rs, "created_at"));
        entry.setUpdatedAt(readInstant(rs, "updated_at"));
        entry.setDeletedAt(readInstant(rs, "deleted_at"));

        long parentId = rs.getLong("parent_id");
        entry.setParentId(rs.wasNull() ? null : parentId);
        entry.setSortOrder(rs.getInt("sort_order"));
        return entry;
    }

    private static final RowMapper<Entry> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    /** 回收站查询多带一列 parent_body，用来显示「属于哪条待办」。 */
    private static final RowMapper<Entry> TRASH_ROW_MAPPER = (rs, rowNum) -> {
        Entry entry = mapRow(rs);
        entry.setParentBody(rs.getString("parent_body"));
        return entry;
    };
}
