package com.techo.entry;

import com.techo.common.ApiResponse;
import com.techo.entry.dto.CreateEntryRequest;
import com.techo.entry.dto.UpdateDoneRequest;
import com.techo.entry.dto.UpdateEntryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 接口，对应文档 §6.2。
 *
 * <p>页面本身不走这里（页面用 htmx 拿 HTML 片段），这个接口是给脚本、
 * 将来的导出功能以及任何外部客户端用的。
 */
@RestController
@RequestMapping("/api/entries")
public class EntryApiController {

    private final EntryService service;

    public EntryApiController(EntryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam EntryType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        StatusFilter filter = StatusFilter.from(status);
        List<Entry> items = service.list(type, q, filter, page, size);
        long total = service.count(type, q, filter);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", Math.max(page, 0));
        data.put("size", size);
        data.put("items", items);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<Entry> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<Entry> create(@Valid @RequestBody CreateEntryRequest request) {
        return ApiResponse.ok(service.create(request.type(), request.title(), request.body()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Entry> update(@PathVariable long id,
                                     @Valid @RequestBody UpdateEntryRequest request) {
        return ApiResponse.ok(service.updateContent(id, request.title(), request.body()));
    }

    @PatchMapping("/{id}/done")
    public ApiResponse<Entry> setDone(@PathVariable long id,
                                      @Valid @RequestBody UpdateDoneRequest request) {
        return ApiResponse.ok(service.setDone(id, request.done()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
