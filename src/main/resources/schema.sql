-- 手账条目表。
--
-- 待办（TODO）与记录（NOTE）共用这一张表，用 type 区分。
-- 另外待办的「子目录」也在这张表里：parent_id 非 NULL 即表示它是某条待办的子项。
-- 复用同一张表而不是另建一张，好处是回收站、导出、时间戳、软删除全部自动共享。
CREATE TABLE IF NOT EXISTS entry (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    type        TEXT    NOT NULL CHECK (type IN ('TODO', 'NOTE')),
    title       TEXT,                                    -- TODO 恒为 NULL；NOTE 为标题
    body        TEXT    NOT NULL DEFAULT '',             -- 待办的内容 / 记录的正文
    done        INTEGER NOT NULL DEFAULT 0
                        CHECK (done IN (0, 1)),           -- 0=未完成 1=已完成
    done_at     INTEGER,                                 -- 完成时刻（epoch millis, UTC）
    created_at  INTEGER NOT NULL,                        -- 创建时刻（epoch millis, UTC）
    updated_at  INTEGER NOT NULL,                        -- 最后修改时刻（epoch millis, UTC）
    deleted_at  INTEGER,                                 -- 非 NULL = 在回收站里（软删除）
    parent_id   INTEGER,                                 -- 非 NULL = 这是某条待办的子目录
    sort_order  INTEGER NOT NULL DEFAULT 0               -- 同一父级下子目录的排序
);

-- 列表主查询：按模块 + 时间倒序
CREATE INDEX IF NOT EXISTS idx_entry_type_created
    ON entry (type, created_at DESC);

-- 待办筛选：按模块 + 完成状态 + 时间倒序
CREATE INDEX IF NOT EXISTS idx_entry_type_done
    ON entry (type, done, created_at DESC);

-- ⚠️ 下面两个索引故意不写在这里，原因见 SchemaMigration 的类注释：
--   idx_entry_deleted  依赖 deleted_at 列
--   idx_entry_parent   依赖 parent_id / sort_order 列
-- 老数据库上没有这些列，在这里建索引会先于补列执行，直接让应用启动失败。

-- 应用设置。目前只用来存登录密码的 BCrypt 哈希
CREATE TABLE IF NOT EXISTS app_setting (
    key         TEXT    PRIMARY KEY,
    value       TEXT    NOT NULL,
    updated_at  INTEGER NOT NULL
);
