package com.techo.entry;

import com.techo.common.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 条目业务逻辑。
 *
 * <p>核心约定：<b>所有时间戳都由这里生成，外部无法传入</b>。
 * 前端时钟不准或被篡改时不会写出错误的日期，这是刻意的设计。
 */
@Service
public class EntryService {

    public static final int MAX_TITLE = 200;
    public static final int MAX_BODY = 20000;
    private static final int MAX_PAGE_SIZE = 100;

    private final EntryRepository repository;

    public EntryService(EntryRepository repository) {
        this.repository = repository;
    }

    // ---------------------------------------------------------------- 写

    public Entry create(EntryType type, String title, String body) {
        String cleanBody = normalize(body);
        if (cleanBody.isEmpty()) {
            throw BizException.badRequest("正文不能为空");
        }
        if (cleanBody.length() > MAX_BODY) {
            throw BizException.badRequest("正文不能超过 " + MAX_BODY + " 字");
        }

        String cleanTitle = normalize(title);
        if (type == EntryType.NOTE) {
            if (cleanTitle.isEmpty()) {
                throw BizException.badRequest("标题不能为空");
            }
            if (cleanTitle.length() > MAX_TITLE) {
                throw BizException.badRequest("标题不能超过 " + MAX_TITLE + " 字");
            }
        } else {
            // 待办恒无标题，传进来也丢掉，保证数据干净
            cleanTitle = null;
        }

        Instant now = Instant.now();
        Entry entry = new Entry();
        entry.setType(type);
        entry.setTitle(cleanTitle);
        entry.setBody(cleanBody);
        entry.setDone(false);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        return repository.insert(entry);
    }

    /** 勾选 / 取消勾选。完成时写入 doneAt，取消完成时清回 null。 */
    public Entry setDone(long id, boolean done) {
        Entry entry = get(id);
        Instant now = Instant.now();
        Instant doneAt = done ? now : null;
        repository.updateDone(id, done, doneAt, now);
        entry.setDone(done);
        entry.setDoneAt(doneAt);
        entry.setUpdatedAt(now);
        return entry;
    }

    public Entry toggle(long id) {
        return setDone(id, !get(id).isDone());
    }

    /** 修改内容。createdAt 保持不变。 */
    public Entry updateContent(long id, String title, String body) {
        Entry existing = get(id);

        String cleanBody = normalize(body);
        if (cleanBody.isEmpty()) {
            throw BizException.badRequest("正文不能为空");
        }
        if (cleanBody.length() > MAX_BODY) {
            throw BizException.badRequest("正文不能超过 " + MAX_BODY + " 字");
        }

        String cleanTitle = normalize(title);
        if (existing.getType() == EntryType.NOTE) {
            if (cleanTitle.isEmpty()) {
                throw BizException.badRequest("标题不能为空");
            }
            if (cleanTitle.length() > MAX_TITLE) {
                throw BizException.badRequest("标题不能超过 " + MAX_TITLE + " 字");
            }
        } else {
            // 待办没有标题，改内容时也不接受标题
            cleanTitle = null;
        }

        Instant now = Instant.now();
        repository.updateContent(id, cleanTitle, cleanBody, now);
        existing.setTitle(cleanTitle);
        existing.setBody(cleanBody);
        existing.setUpdatedAt(now);
        return existing;
    }

    /** 删除 = 移入回收站。不做物理删除，误删还能救回来。 */
    public void delete(long id) {
        if (repository.softDelete(id, Instant.now()) == 0) {
            throw BizException.notFound("条目不存在：id=" + id);
        }
    }

    // ---------------------------------------------------------------- 子目录

    public List<Entry> listSubtasks(long parentId) {
        return repository.findSubtasks(parentId);
    }

    /** 一次取出多条待办的子目录，按父级汇总。避免渲染列表时逐条查询。 */
    public Map<Long, SubtaskSummary> subtaskSummaries(List<Long> parentIds) {
        Map<Long, List<Entry>> grouped = repository.findSubtasksByParents(parentIds).stream()
                .collect(Collectors.groupingBy(Entry::getParentId));

        Map<Long, SubtaskSummary> result = new LinkedHashMap<>();
        for (Long parentId : parentIds) {
            // 即使没有子目录也放一个空汇总，模板里就不用到处判空
            List<Entry> items = grouped.getOrDefault(parentId, List.of());
            long done = items.stream().filter(Entry::isDone).count();
            result.put(parentId, new SubtaskSummary(items, done));
        }
        return result;
    }

    public long countSubtasks(long parentId) {
        return repository.countSubtasks(parentId);
    }

    public long countDoneSubtasks(long parentId) {
        return repository.countDoneSubtasks(parentId);
    }

    /**
     * 新增一条子目录。
     *
     * @param afterSubtaskId 为 null 表示加到末尾；否则插到这条子目录的下面
     */
    public Entry addSubtask(long parentId, String body, Long afterSubtaskId) {
        Entry parent = get(parentId);
        if (parent.isSubtask() || parent.getType() != EntryType.TODO) {
            throw BizException.badRequest("只能给顶层待办加子目录");
        }

        String cleanBody = normalize(body);
        if (cleanBody.isEmpty()) {
            throw BizException.badRequest("子目录内容不能为空");
        }
        if (cleanBody.length() > MAX_BODY) {
            throw BizException.badRequest("子目录内容不能超过 " + MAX_BODY + " 字");
        }

        int order;
        if (afterSubtaskId == null) {
            order = repository.nextSortOrder(parentId);
        } else {
            Entry after = get(afterSubtaskId);
            if (!Objects.equals(after.getParentId(), parentId)) {
                throw BizException.badRequest("指定的子目录不属于这条待办");
            }
            int afterOrder = after.getSortOrder();
            // 先把后面的整体后移一位，再把新的插进空位
            repository.shiftSortOrdersAfter(parentId, afterOrder);
            order = afterOrder + 1;
        }

        Instant now = Instant.now();
        Entry subtask = new Entry();
        subtask.setType(EntryType.TODO);
        subtask.setParentId(parentId);
        subtask.setSortOrder(order);
        subtask.setBody(cleanBody);
        subtask.setDone(false);
        subtask.setCreatedAt(now);
        subtask.setUpdatedAt(now);
        return repository.insert(subtask);
    }

    public Entry setSubtaskDone(long subtaskId, boolean done) {
        Entry subtask = requireSubtask(subtaskId);
        Instant now = Instant.now();
        Instant doneAt = done ? now : null;
        repository.updateDone(subtaskId, done, doneAt, now);
        subtask.setDone(done);
        subtask.setDoneAt(doneAt);
        subtask.setUpdatedAt(now);
        return subtask;
    }

    public Entry toggleSubtask(long subtaskId) {
        return setSubtaskDone(subtaskId, !requireSubtask(subtaskId).isDone());
    }

    /** 删除子目录 —— 同样是软删除，能在回收站里找回来。 */
    public void deleteSubtask(long subtaskId) {
        requireSubtask(subtaskId);
        repository.softDelete(subtaskId, Instant.now());
    }

    /** 修改子目录的文字。 */
    public Entry updateSubtask(long subtaskId, String body) {
        Entry subtask = requireSubtask(subtaskId);

        String cleanBody = normalize(body);
        if (cleanBody.isEmpty()) {
            throw BizException.badRequest("子目录内容不能为空");
        }
        if (cleanBody.length() > MAX_BODY) {
            throw BizException.badRequest("子目录内容不能超过 " + MAX_BODY + " 字");
        }

        Instant now = Instant.now();
        repository.updateContent(subtaskId, null, cleanBody, now);
        subtask.setBody(cleanBody);
        subtask.setUpdatedAt(now);
        return subtask;
    }

    private Entry requireSubtask(long id) {
        Entry entry = get(id);
        if (!entry.isSubtask()) {
            throw BizException.badRequest("这不是子目录：id=" + id);
        }
        return entry;
    }

    /**
     * 取子目录并校验。
     *
     * <p>存在的意义是让调用方**先校验再拆箱 parentId** ——
     * 父待办的 parent_id 是 null，直接拆箱会 NPE 变成 500 而不是 400。
     */
    public Entry getSubtask(long id) {
        return requireSubtask(id);
    }

    /** 取顶层条目。子目录传进来按「不存在」处理，避免被误当成待办操作。 */
    public Entry getTopLevel(long id) {
        Entry entry = get(id);
        if (entry.isSubtask()) {
            throw BizException.notFound("条目不存在：id=" + id);
        }
        return entry;
    }

    // ---------------------------------------------------------------- 回收站

    public List<Entry> listTrash(int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return repository.findDeleted(safeSize, safePage * safeSize);
    }

    public long countTrash() {
        return repository.countDeleted();
    }

    /** 从回收站恢复。 */
    public Entry restore(long id) {
        Entry entry = repository.findAnyById(id)
                .orElseThrow(() -> BizException.notFound("条目不存在：id=" + id));
        if (!entry.isDeleted()) {
            throw BizException.badRequest("这条不在回收站里");
        }
        repository.restore(id);
        entry.setDeletedAt(null);
        return entry;
    }

    /** 彻底删除，不可恢复。父级待办会连带清掉它的子目录，避免留下孤儿数据。 */
    public void purge(long id) {
        if (repository.purge(id) == 0) {
            throw BizException.notFound("回收站里没有这条：id=" + id);
        }
        repository.purgeSubtasks(id);
    }

    /** 清空回收站，返回清掉的条数。 */
    public int emptyTrash() {
        return repository.purgeAllDeleted();
    }

    // ---------------------------------------------------------------- 读

    public Entry get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> BizException.notFound("条目不存在：id=" + id));
    }

    public List<Entry> list(EntryType type, String keyword, StatusFilter status, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return repository.find(type, keyword, status, safeSize, safePage * safeSize);
    }

    public long count(EntryType type, String keyword, StatusFilter status) {
        return repository.count(type, keyword, status);
    }

    /** 导出用：全部未删除条目，按创建时间正序。 */
    public List<Entry> listAll() {
        return repository.findAll();
    }

    /** 全部未删除条目的数量（不分类型）。 */
    public long countAll() {
        return repository.countAll();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
