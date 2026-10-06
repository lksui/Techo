package com.techo.entry;

/**
 * 列表筛选条件。字符串形式（all / open / done）与文档 §6.2 的接口约定一致。
 */
public enum StatusFilter {

    ALL,
    OPEN,
    DONE;

    /** 宽松解析：不认识的值一律当作 ALL，避免因为一个查询参数报 400。 */
    public static StatusFilter from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "open", "todo", "undone" -> OPEN;
            case "done", "completed" -> DONE;
            default -> ALL;
        };
    }

    public String code() {
        return name().toLowerCase();
    }
}
