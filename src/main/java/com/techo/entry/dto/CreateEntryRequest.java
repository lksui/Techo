package com.techo.entry.dto;

import com.techo.entry.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增条目的请求体。时间戳由服务端生成，这里接收不到，也不允许传。
 */
public record CreateEntryRequest(
        @NotNull(message = "type 不能为空")
        EntryType type,

        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @NotBlank(message = "正文不能为空")
        @Size(max = 20000, message = "正文不能超过 20000 字")
        String body
) {
}
