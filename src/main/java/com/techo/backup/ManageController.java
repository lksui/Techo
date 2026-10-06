package com.techo.backup;

import com.techo.entry.EntryService;
import com.techo.entry.EntryType;
import com.techo.entry.StatusFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理页：备份、导出、回收站入口、数据统计。
 */
@Controller
public class ManageController {

    private static final Logger log = LoggerFactory.getLogger(ManageController.class);

    private final SnapshotService snapshots;
    private final ExportService exports;
    private final EntryService entries;
    private final boolean authEnabled;

    public ManageController(SnapshotService snapshots,
                            ExportService exports,
                            EntryService entries,
                            @Value("${techo.auth.enabled:true}") boolean authEnabled) {
        this.snapshots = snapshots;
        this.exports = exports;
        this.entries = entries;
        this.authEnabled = authEnabled;
    }

    @GetMapping("/manage")
    public String page(Model model) {
        model.addAttribute("totalCount", entries.countAll());
        model.addAttribute("todoCount", entries.count(EntryType.TODO, null, StatusFilter.ALL));
        model.addAttribute("noteCount", entries.count(EntryType.NOTE, null, StatusFilter.ALL));
        model.addAttribute("trashCount", entries.countTrash());
        model.addAttribute("authEnabled", authEnabled);
        fillBackupPanel(model);
        return "manage";
    }

    /**
     * 立即生成一份快照。
     *
     * <p>返回片段而不是 {@code redirect:/manage}：重定向在浏览器里没问题，
     * 但部分 HTTP 客户端在 302 时会保留 POST 方法，导致对只接受 GET 的 /manage 发 POST
     * 而拿到 405。用 htmx 就地替换可以完全避开这类客户端差异。
     */
    @PostMapping("/manage/backup")
    public String backupNow(Model model) {
        Path file = snapshots.snapshot();
        int removed = snapshots.prune();
        model.addAttribute("message", "已生成快照 " + file.getFileName()
                + (removed > 0 ? "，并清理了 " + removed + " 份旧快照" : ""));
        fillBackupPanel(model);
        return "fragments/backup-panel :: panel";
    }

    private void fillBackupPanel(Model model) {
        model.addAttribute("snapshots", snapshotViews());
        model.addAttribute("snapshotDir", snapshots.getDir().toAbsolutePath().toString());
        model.addAttribute("keep", snapshots.getKeep());
    }

    // ---------------------------------------------------------------- 导出

    @GetMapping("/api/export/json")
    public ResponseEntity<byte[]> exportJson() {
        return download(exports.toJsonl().getBytes(StandardCharsets.UTF_8),
                exports.jsonFileName(), "application/x-ndjson");
    }

    @GetMapping("/api/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown() {
        return download(exports.toMarkdown().getBytes(StandardCharsets.UTF_8),
                exports.markdownFileName(), "text/markdown");
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType + "; charset=UTF-8"))
                .body(body);
    }

    // ---------------------------------------------------------------- 视图模型

    private List<Map<String, Object>> snapshotViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (Path path : snapshots.list()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", path.getFileName().toString());
            view.put("display", SnapshotService.displayName(path));
            view.put("sizeKb", sizeKb(path));
            views.add(view);
        }
        return views;
    }

    private static long sizeKb(Path path) {
        try {
            return Math.max(1, Files.size(path) / 1024);
        } catch (IOException e) {
            log.warn("读取快照大小失败：{}", path, e);
            return 0;
        }
    }
}
