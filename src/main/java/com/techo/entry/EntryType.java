package com.techo.entry;

/**
 * 条目类型。一张 entry 表用这个字段区分两个模块。
 */
public enum EntryType {
    /** 待办：只有正文，可勾选完成 */
    TODO,
    /** 记录：有标题 + 正文 */
    NOTE
}
