package com.techo.entry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 手账条目。待办与记录共用这一个模型，靠 {@link EntryType} 区分。
 *
 * <p>时间字段一律是 {@link Instant}（UTC）。时区转换只发生在展示层，
 * 也就是下面两个 {@code getXxxDisplay()} 方法里。
 *
 * <p>写成 POJO 而不是 record，是为了让 Thymeleaf 能直接用 {@code ${entry.body}}
 * 这样的属性语法取值，避免 record 访问器在表达式引擎里的兼容性差异。
 */
public class Entry {

    /** 展示用时区。存储始终是 UTC，只有这里转换成北京时间。 */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter SHORT_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(DISPLAY_ZONE);

    private static final DateTimeFormatter FULL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DISPLAY_ZONE);

    private Long id;
    private EntryType type;
    private String title;
    private String body;
    private boolean done;
    private Instant doneAt;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非 null 表示这条已进回收站（软删除）。 */
    private Instant deletedAt;
    /** 非 null 表示这是某条待办的子目录。 */
    private Long parentId;
    /** 同一父级下子目录之间的排序。 */
    private int sortOrder;
    /**
     * 非持久化字段：只有回收站列表会填它，用来显示「属于哪条待办」。
     * 放在实体上是为了让模板保持简单，不必为回收站单独做一层视图模型。
     */
    private String parentBody;

    // ---------------------------------------------------------------- 展示用派生属性

    /** 列表里显示的短时间，如 {@code 03-05 10:15} */
    public String getCreatedAtDisplay() {
        return createdAt == null ? "" : SHORT_FORMAT.format(createdAt);
    }

    /** 详情里显示的完整时间，如 {@code 2026-03-05 10:15} */
    public String getCreatedAtDisplayFull() {
        return createdAt == null ? "" : FULL_FORMAT.format(createdAt);
    }

    public boolean isTodo() {
        return type == EntryType.TODO;
    }

    public boolean isNote() {
        return type == EntryType.NOTE;
    }

    // ---------------------------------------------------------------- 普通读写方法

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 是否是子目录（而不是顶层待办）。 */
    public boolean isSubtask() {
        return parentId != null;
    }

    /** 回收站里显示的删除时间 */
    public String getDeletedAtDisplay() {
        return deletedAt == null ? "" : SHORT_FORMAT.format(deletedAt);
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getParentBody() {
        return parentBody;
    }

    public void setParentBody(String parentBody) {
        this.parentBody = parentBody;
    }
}
