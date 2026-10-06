-- 手账条目表：待办（TODO）与记录（NOTE）共用一张表，用 type 区分
CREATE TABLE IF NOT EXISTS entry (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    type        TEXT    NOT NULL CHECK (type IN ('TODO', 'NOTE')),
    title       TEXT,                                    -- TODO 恒为 NULL；NOTE 为标题
    body        TEXT    NOT NULL DEFAULT '',             -- 待办的内容 / 记录的正文
    done        INTEGER NOT NULL DEFAULT 0
                        CHECK (done IN (0, 1)),           -- 0=未完成 1=已完成，仅 TODO 使用
    done_at     INTEGER,                                 -- 完成时刻（epoch millis, UTC）
    created_at  INTEGER NOT NULL,                        -- 创建时刻（epoch millis, UTC）
    updated_at  INTEGER NOT NULL                         -- 最后修改时刻（epoch millis, UTC）
);

-- 列表主查询：按模块 + 时间倒序
CREATE INDEX IF NOT EXISTS idx_entry_type_created
    ON entry (type, created_at DESC);

-- 待办筛选：按模块 + 完成状态 + 时间倒序
CREATE INDEX IF NOT EXISTS idx_entry_type_done
    ON entry (type, done, created_at DESC);
