package com.techo.entry.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换完成状态的请求体。
 */
public record UpdateDoneRequest(
        @NotNull(message = "done 不能为空")
        Boolean done
) {
}
