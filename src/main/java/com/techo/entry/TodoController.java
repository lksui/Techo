package com.techo.entry;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 待办页面的渲染与 htmx 片段。
 *
 * <p>设计要点：整页由服务端渲染（关闭 JS 也能看能读），
 * htmx 只负责把「新增 / 勾选 / 编辑 / 删除 / 子目录」的结果局部换掉，不整页刷新。
 */
@Controller
@RequestMapping("/todo")
public class TodoController {

    /**
     * 单页最多渲染多少条。分页还没做，先用一个足够大的上限，
     * 避免待办多起来以后一次查出几千条。
     */
    private static final int PAGE_SIZE = 300;

    private final EntryService service;

    public TodoController(EntryService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- 整页

    @GetMapping
    public String page(@RequestParam(defaultValue = "all") String status,
                       @RequestParam(required = false) String q,
                       Model model) {
        StatusFilter filter = StatusFilter.from(status);
        List<Entry> items = service.list(EntryType.TODO, q, filter, 0, PAGE_SIZE);

        model.addAttribute("items", items);
        model.addAttribute("subtaskMap", service.subtaskSummaries(idsOf(items)));
        model.addAttribute("status", filter.code());
        model.addAttribute("q", q);
        model.addAttribute("total", service.count(EntryType.TODO, null, StatusFilter.ALL));
        model.addAttribute("openTotal", service.count(EntryType.TODO, null, StatusFilter.OPEN));
        model.addAttribute("trashCount", service.countTrash());
        return "todo";
    }

    /**
     * htmx：搜索或切换筛选后只换列表片段。
     * 单独一个端点而不是复用整页，是为了不打断正在输入的搜索框焦点。
     */
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "all") String status,
                       @RequestParam(required = false) String q,
                       Model model) {
        StatusFilter filter = StatusFilter.from(status);
        List<Entry> items = service.list(EntryType.TODO, q, filter, 0, PAGE_SIZE);

        model.addAttribute("items", items);
        model.addAttribute("subtaskMap", service.subtaskSummaries(idsOf(items)));
        model.addAttribute("q", q);
        return "fragments/todo-list :: list";
    }

    // ---------------------------------------------------------------- 待办片段

    /** 新增一条待办，返回单条片段，由 htmx 插到列表顶部。 */
    @PostMapping("/items")
    public String add(@RequestParam(required = false) String body, Model model) {
        if (body == null || body.isBlank()) {
            // 空内容不做事。表单上有 required，正常走不到这里，这是兜底。
            throw new ResponseStatusException(HttpStatus.NO_CONTENT);
        }
        Entry entry = service.create(EntryType.TODO, null, body);
        model.addAttribute("item", entry);
        model.addAttribute("summary", SubtaskSummary.empty());
        return "fragments/todo-item :: item";
    }

    /** 切换完成状态，返回更新后的整条片段。 */
    @PatchMapping("/items/{id}/toggle")
    public String toggle(@PathVariable long id, Model model) {
        Entry entry = service.setDone(id, !service.getTopLevel(id).isDone());
        fillItemModel(model, entry);
        return "fragments/todo-item :: item";
    }

    /** 进入编辑：把待办行换成编辑表单。 */
    @GetMapping("/items/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("item", service.getTopLevel(id));
        return "fragments/todo-item :: edit";
    }

    /** 取消编辑：换回普通行。 */
    @GetMapping("/items/{id}")
    public String item(@PathVariable long id, Model model) {
        fillItemModel(model, service.getTopLevel(id));
        return "fragments/todo-item :: item";
    }

    /** 保存编辑。待办没有标题，只改正文。 */
    @PutMapping("/items/{id}")
    public String update(@PathVariable long id,
                         @RequestParam String body,
                         Model model) {
        fillItemModel(model, service.updateContent(id, null, body));
        return "fragments/todo-item :: item";
    }

    /** 删除。返回 204，htmx 会把这一行移除。 */
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    // ---------------------------------------------------------------- 子目录片段

    /**
     * 新增子目录。
     *
     * @param after 为 null 表示加到末尾；否则插到这条子目录的下面
     */
    @PostMapping("/items/{id}/subtasks")
    public String addSubtask(@PathVariable long id,
                             @RequestParam(required = false) String body,
                             @RequestParam(required = false) Long after,
                             Model model) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT);
        }
        Entry subtask = service.addSubtask(id, body, after);
        fillSubtaskModel(model, subtask);
        return "fragments/subtask-item :: subtaskAndBadge";
    }

    /** 勾选 / 取消勾选子目录。 */
    @PatchMapping("/subtasks/{id}/toggle")
    public String toggleSubtask(@PathVariable long id, Model model) {
        Entry subtask = service.toggleSubtask(id);
        fillSubtaskModel(model, subtask);
        return "fragments/subtask-item :: subtaskAndBadge";
    }

    /**
     * 修改子目录文字。
     *
     * <p>只返回子目录本体，不带计数徽标 —— 改文字不影响已完成的数量。
     */
    @PutMapping("/subtasks/{id}")
    public String updateSubtask(@PathVariable long id,
                                @RequestParam String body,
                                Model model) {
        model.addAttribute("sub", service.updateSubtask(id, body));
        return "fragments/subtask-item :: subtask";
    }

    /**
     * 删除子目录。
     *
     * <p>返回「计数徽标」这一个片段，配合按钮上的 {@code hx-swap="delete"}：
     * htmx 会先把响应里的 out-of-band 徽标换掉，再删除目标元素本身。
     */
    @DeleteMapping("/subtasks/{id}")
    public String deleteSubtask(@PathVariable long id, Model model) {
        // 先校验再拆箱 parentId —— 父待办的 parent_id 是 null，
        // 直接 getParentId() 会 NPE 变成 500 而不是 400
        Entry subtask = service.getSubtask(id);
        long parentId = subtask.getParentId();
        service.deleteSubtask(id);
        model.addAttribute("parentId", parentId);
        model.addAttribute("total", service.countSubtasks(parentId));
        model.addAttribute("done", service.countDoneSubtasks(parentId));
        return "fragments/subtask-item :: badge";
    }

    // ---------------------------------------------------------------- 内部

    /** 一次刷新「整条待办 + 它的子目录汇总」所需的模型。 */
    private void fillItemModel(Model model, Entry entry) {
        model.addAttribute("item", entry);
        model.addAttribute("summary", service.subtaskSummaries(List.of(entry.getId()))
                .getOrDefault(entry.getId(), SubtaskSummary.empty()));
    }

    /** 一次刷新「单条子目录 + 父级计数徽标」所需的模型。 */
    private void fillSubtaskModel(Model model, Entry subtask) {
        long parentId = subtask.getParentId();
        model.addAttribute("sub", subtask);
        model.addAttribute("parentId", parentId);
        model.addAttribute("total", service.countSubtasks(parentId));
        model.addAttribute("done", service.countDoneSubtasks(parentId));
    }

    private static List<Long> idsOf(List<Entry> items) {
        return items.stream().map(Entry::getId).toList();
    }
}
