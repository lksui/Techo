package com.techo.entry;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 记录模块（标题 + 正文）的页面与 htmx 片段。
 *
 * <p>和待办模块的区别在于「编辑」：记录内容通常较长，写完还会改，
 * 所以卡片和编辑表单是同一张卡片的两个状态，用 htmx 就地切换，
 * 不跳页、不弹窗。
 */
@Controller
@RequestMapping("/notes")
public class NotesController {

    private static final int PAGE_SIZE = 200;

    private final EntryService service;

    public NotesController(EntryService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- 整页

    @GetMapping
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("items", service.list(EntryType.NOTE, q, StatusFilter.ALL, 0, PAGE_SIZE));
        model.addAttribute("q", q);
        model.addAttribute("total", service.count(EntryType.NOTE, null, StatusFilter.ALL));
        return "notes";
    }

    /** htmx：搜索后只换列表片段，不打断搜索框焦点。 */
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("items", service.list(EntryType.NOTE, q, StatusFilter.ALL, 0, PAGE_SIZE));
        model.addAttribute("q", q);
        return "fragments/note-list :: list";
    }

    // ---------------------------------------------------------------- htmx 片段

    /** 新增一条记录，返回卡片片段。 */
    @PostMapping("/items")
    public String add(@RequestParam String title,
                      @RequestParam String body,
                      Model model) {
        model.addAttribute("item", service.create(EntryType.NOTE, title, body));
        return "fragments/note-card :: card";
    }

    /** 取消编辑：把编辑表单换回卡片。 */
    @GetMapping("/items/{id}")
    public String card(@PathVariable long id, Model model) {
        model.addAttribute("item", service.get(id));
        return "fragments/note-card :: card";
    }

    /** 进入编辑：把卡片换成编辑表单。 */
    @GetMapping("/items/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("item", service.get(id));
        return "fragments/note-card :: edit";
    }

    /** 保存编辑，返回更新后的卡片。 */
    @PutMapping("/items/{id}")
    public String update(@PathVariable long id,
                         @RequestParam String title,
                         @RequestParam String body,
                         Model model) {
        model.addAttribute("item", service.updateContent(id, title, body));
        return "fragments/note-card :: card";
    }

    /** 删除。返回 204，htmx 把这张卡片移除。 */
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
