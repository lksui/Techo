package com.techo.backup;

import com.techo.entry.Entry;
import com.techo.entry.EntryService;
import com.techo.entry.EntryType;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据导出。
 *
 * <p>导出不只是为了备份 —— 更重要的是**数据不被锁死在这个程序里**。
 * 即使哪天不用 Techo 了，导出的 Markdown 也能直接翻阅，JSONL 也能被任何工具处理。
 */
@Service
public class ExportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZONE);

    private final EntryService service;
    private final ObjectMapper mapper;

    public ExportService(EntryService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- JSONL

    /**
     * 每行一条 JSON，而不是一个大 JSON 数组。
     * 这样用 git 之类的工具做差异对比时，看到的才是「新增了一行」而不是「整个文件都变了」。
     *
     * <p>子目录也是独立的行，靠 {@code parentId} 关联父级。
     */
    public String toJsonl() {
        StringBuilder out = new StringBuilder();
        try {
            for (Entry entry : service.listAll()) {
                out.append(mapper.writeValueAsString(toMap(entry))).append('\n');
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("导出 JSON 失败", e);
        }
        return out.toString();
    }

    private static Map<String, Object> toMap(Entry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("type", entry.getType().name());
        map.put("parentId", entry.getParentId());
        map.put("sortOrder", entry.getSortOrder());
        map.put("title", entry.getTitle());
        map.put("body", entry.getBody());
        map.put("done", entry.isDone());
        map.put("createdAt", entry.getCreatedAt() == null ? null : entry.getCreatedAt().toString());
        map.put("updatedAt", entry.getUpdatedAt() == null ? null : entry.getUpdatedAt().toString());
        map.put("doneAt", entry.getDoneAt() == null ? null : entry.getDoneAt().toString());
        return map;
    }

    // ---------------------------------------------------------------- Markdown

    /**
     * 按日期分节的可读文本。单文件即可通读，不需要这个程序也能看懂。
     * 子目录以嵌套列表的形式挂在父待办下面。
     */
    public String toMarkdown() {
        List<Entry> all = service.listAll();

        Map<Long, List<Entry>> subtasksByParent = all.stream()
                .filter(Entry::isSubtask)
                .collect(Collectors.groupingBy(Entry::getParentId));

        List<Entry> topLevel = all.stream().filter(e -> !e.isSubtask()).toList();
        int subtaskCount = all.size() - topLevel.size();

        StringBuilder out = new StringBuilder();
        out.append("# Techo 手账导出\n\n");
        out.append("导出时间：").append(STAMP.format(Instant.now()))
           .append("，共 ").append(topLevel.size()).append(" 条");
        if (subtaskCount > 0) {
            out.append("（含 ").append(subtaskCount).append(" 条子目录）");
        }
        out.append("\n");

        String currentDate = null;
        for (Entry entry : topLevel) {
            String date = DATE.format(entry.getCreatedAt());
            if (!date.equals(currentDate)) {
                currentDate = date;
                out.append("\n## ").append(date).append("\n\n");
            }

            if (entry.getType() == EntryType.TODO) {
                out.append("- [").append(entry.isDone() ? "x" : " ").append("] ")
                   .append(flatten(entry.getBody()))
                   .append("　`").append(TIME.format(entry.getCreatedAt())).append("`\n");

                List<Entry> subs = subtasksByParent.getOrDefault(entry.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(Entry::getSortOrder).thenComparing(Entry::getId))
                        .toList();
                for (Entry sub : subs) {
                    out.append("    - [").append(sub.isDone() ? "x" : " ").append("] ")
                       .append(flatten(sub.getBody())).append('\n');
                }
            } else {
                out.append("### ").append(flatten(entry.getTitle())).append("\n\n");
                out.append(entry.getBody()).append("\n\n");
                out.append("<sub>记录于 ").append(TIME.format(entry.getCreatedAt())).append("</sub>\n\n");
            }
        }
        return out.toString();
    }

    /** 待办正文可能包含换行，放进列表项里会把结构打乱，压成一行。 */
    private static String flatten(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").trim();
    }

    // ---------------------------------------------------------------- 文件名

    public String jsonFileName() {
        return "techo-export-" + FILE_STAMP.format(Instant.now()) + ".jsonl";
    }

    public String markdownFileName() {
        return "techo-export-" + FILE_STAMP.format(Instant.now()) + ".md";
    }
}
