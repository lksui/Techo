package com.techo.entry;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 回收站。软删除的条目在这里，可以恢复或彻底删除。
 */
@Controller
@RequestMapping("/trash")
public class TrashController {

    private static final int PAGE_SIZE = 300;

    private final EntryService service;

    public TrashController(EntryService service) {
        this.service = service;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("items", service.listTrash(0, PAGE_SIZE));
        return "trash";
    }

    /** 恢复。返回 204，htmx 把这张卡片从回收站列表里移除。 */
    @PostMapping("/items/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable long id) {
        service.restore(id);
    }

    /** 彻底删除。返回 204。 */
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@PathVariable long id) {
        service.purge(id);
    }

    /**
     * 清空回收站。这里返回 200 + 空列表片段而不是 204，
     * 因为需要把整个列表换掉，而不只是移除一张卡片。
     */
    @DeleteMapping
    public String empty(Model model) {
        service.emptyTrash();
        model.addAttribute("items", java.util.List.of());
        return "fragments/trash-list :: list";
    }
}
