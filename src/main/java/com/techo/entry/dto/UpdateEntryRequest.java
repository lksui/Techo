package com.techo.entry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改条目内容的请求体。
 *
 * <p>刻意不包含 {@code type} 和任何时间字段 —— 条目的类型不可变更，
 * 时间戳一律由服务端维护。
 */
public record UpdateEntryRequest(
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @NotBlank(message = "正文不能为空")
        @Size(max = 20000, message = "正文不能超过 20000 字")
        String body
) {
}
