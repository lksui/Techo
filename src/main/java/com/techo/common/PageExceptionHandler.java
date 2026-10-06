package com.techo.common;

import com.techo.entry.NotesController;
import com.techo.entry.TodoController;
import com.techo.entry.TrashController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * 页面（htmx）请求的异常处理。
 *
 * <p>和 {@link GlobalExceptionHandler} 分工明确：那个只管 JSON 接口，这个只管页面片段。
 * 分开是必须的 —— 如果共用一个 advice，页面出错时会返回 JSON，
 * 而 htmx 会把那段 JSON 当成 HTML 直接塞进列表里。
 *
 * <p>返回纯文本而不是 HTML：前端 {@code app.js} 会捕获失败响应，
 * 把这段文字原样显示成一条提示。返回 4xx 时 htmx 默认不替换页面内容，
 * 所以必须由前端补上这个反馈，否则用户会觉得「点了没反应」。
 */
@ControllerAdvice(assignableTypes = {TodoController.class, NotesController.class, TrashController.class})
public class PageExceptionHandler {

    /**
     * 显式处理 {@link ResponseStatusException}。
     *
     * <p>这个处理器**必须存在**：页面控制器用 {@code throw new ResponseStatusException(NO_CONTENT)}
     * 表达「空内容，什么都不做」，而下面那个 {@code @ExceptionHandler(Exception.class)} 兜底
     * 会把它一起吞掉、变成 500「服务器内部错误」。
     *
     * <p>Spring 自带的 ResponseStatusExceptionResolver 排在 ExceptionHandlerExceptionResolver
     * **之后**，所以一旦有了 catch-all，它就再也没有机会执行了。
     * 这个 bug 真实发生过（空输入提交返回 500），是靠测试抓出来的。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).build();
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<String> handleBiz(BizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body("服务器内部错误：" + ex.getMessage());
    }
}
