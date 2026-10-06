package com.techo.backup;

import com.techo.entry.EntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 定时生成数据库快照。默认每天凌晨 3:30，保留最近 30 份。
 *
 * <p>时间点选在凌晨是因为 VACUUM INTO 会完整读一遍数据库，
 * 放在白天可能和你正在输入的操作抢 SQLite 的写锁（虽然池子只有一条连接，会排队）。
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final SnapshotService snapshotService;
    private final EntryService entryService;

    public BackupScheduler(SnapshotService snapshotService, EntryService entryService) {
        this.snapshotService = snapshotService;
        this.entryService = entryService;
    }

    @Scheduled(cron = "${techo.backup.cron:0 30 3 * * *}")
    public void dailySnapshot() {
        try {
            // 一条数据都没有就跳过，免得留一堆空的快照文件
            if (entryService.countAll() == 0) {
                log.info("没有任何条目，跳过本次快照");
                return;
            }
            Path file = snapshotService.snapshot();
            snapshotService.prune();
            log.info("定时快照完成：{}", file.getFileName());
        } catch (Exception e) {
            // 定时任务抛异常会静默失败，必须打出来
            log.error("定时快照失败", e);
        }
    }
}
