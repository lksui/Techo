package com.techo.entry;

import java.util.List;

/**
 * 一条待办的子目录汇总，供列表渲染使用。
 *
 * <p>写成带 getter 的普通类而不是 record：Thymeleaf 的表达式引擎对 record 访问器
 * 的支持在不同版本间有差异，getter 零风险（这个项目里 Entry 也是同样的理由）。
 */
public class SubtaskSummary {

    private final List<Entry> items;
    private final long done;

    public SubtaskSummary(List<Entry> items, long done) {
        this.items = items;
        this.done = done;
    }

    public static SubtaskSummary empty() {
        return new SubtaskSummary(List.of(), 0);
    }

    public List<Entry> getItems() {
        return items;
    }

    public long getDone() {
        return done;
    }

    public int getTotal() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** 列表里显示的进度，如 {@code 1/3}；没有子目录时为空串。 */
    public String getBadge() {
        return items.isEmpty() ? "" : done + "/" + items.size();
    }
}
