# Techo

局域网自托管的「手账 + 待办」应用 —— 一个人用，装在自家电脑上，手机连同一个 WiFi 就能访问。

数据存在你自己的机器上（SQLite），不经过任何第三方服务器。

## 功能

**待办**
- 只有正文，勾选即划线完成
- 子目录（子任务）：点击待办本身展开/收起；每条子目录可勾选、改文字、删除，父待办上显示 `完成数/总数` 徽标
- 手机左划揭示操作按钮，电脑端按钮常驻行尾

**记录**
- 标题 + 正文，卡片式浏览

**通用**
- 创建/修改时间自动记录，按东八区显示
- 全局搜索（标题、正文）
- 回收站：删除不真删，可恢复；彻底删除前二次确认
- 每日自动备份快照（默认凌晨 3:30，保留最近 30 份），也可手动备份
- 一键导出：完整 Markdown（可读、可归档）或 JSONL（逐行 JSON，方便比对）
- 可添加到手机主屏（图标 + 全屏），像 App 一样用
- 登录密码（BCrypt，默认关闭 —— 单人使用开着反而添麻烦，需要时一个配置项就打开）

## 技术栈

| 组件 | 选择 | 理由 |
|---|---|---|
| Java / Spring Boot | 27 / 4.1.1 | 当前环境；Web、Thymeleaf、JDBC 一体 |
| 模板 | Thymeleaf + htmx 2.0.11 | 服务端渲染，htmx 本地托管，**无任何 CDN** |
| 数据库 | SQLite 3.50（xerial） | 单文件、零运维；WAL 模式，连接池=1 规避写锁 |
| 数据访问 | `JdbcTemplate` | 刻意不用 JPA —— 单表单机场景，简单直接 |
| JSON | Jackson 3（`tools.jackson`） | Boot 4 默认 |

## 快速开始

### 环境要求

- JDK 27（当前环境 `D:\Dev\jdk-27`）
- Maven 3.9+
- 不需要安装任何数据库 —— SQLite 内置

### 构建

```bash
mvn -B clean package
```

产物：`target/techo-1.0.0.jar`（约 37 MB）

### 运行

```bash
java -jar target/techo-1.0.0.jar
```

- 默认端口 `8080`，监听 `0.0.0.0`
- 手机浏览器访问 `http://<电脑的局域网IP>:8080`
- 也可以直接双击 `start.cmd`（带了内存调优参数：`-Xms32m -Xmx192m`，实测占用约 160 MB）

### 作为 Windows 服务（可选）

`service/` 目录里提供了 WinSW 包装：

1. 以管理员身份运行 `service/install-service.cmd`
2. 服务名 `Techo`，开机自启、后台运行

> 相关的手动步骤（防火墙放行 8080 端口等）见 `docs/技术方案.md` 第 9 节。

## 数据在哪

| 路径 | 内容 |
|---|---|
| `data/journal.db` | **全部手账数据**（SQLite，WAL 模式）。备份它等于备份一切 |
| `backup/` | 每日自动快照 + 手动备份 |
| `logs/techo.log` | 运行日志 |

⚠️ **备份时注意**：SQLite 的 WAL 模式下，新写入的数据先落在 `journal.db-wal` 文件里，只复制 `journal.db` 会丢最近的数据。稳妥做法是使用应用内置的备份/导出功能，或连同 `-wal`/`-shm` 一起复制。

## 配置

所有配置集中在 `src/main/resources/application.yml`：

| 配置项 | 默认 | 说明 |
|---|---|---|
| `server.port` | `8080` | 监听端口 |
| `spring.datasource.url` | 本机 `data/journal.db` | 绝对路径，换成其他机器要改 |
| `techo.auth.enabled` | `false` | 登录开关（已设过的密码哈希仍保留在库里，重开即恢复） |
| `techo.backup.cron` | `0 30 3 * * *` | 每日备份时刻 |
| `techo.backup.keep` | `30` | 快照保留份数 |

## 安全说明

- 默认**关闭登录**，同一局域网内任何设备打开 `http://本机IP:8080` 都能看到全部内容
- 现在真正的访问边界是**防火墙规则**（只允许内网网段进 8080），详见文档第 9 节
- 想重新要密码保护：把 `techo.auth.enabled` 改成 `true` 即可

## 项目结构

```
src/main/java/com/techo/
  ├── entry/    待办、记录、子目录、回收站（实体/仓储/服务/控制器）
  ├── auth/     登录（默认关闭，代码保留）
  ├── backup/   备份快照、定时任务、导出
  └── common/   异常处理、首页、健康检查、数据库结构迁移

src/main/resources/
  ├── templates/      Thymeleaf 页面与片段（含子目录交互模板）
  ├── static/         本地 CSS/JS/htmx，PWA 图标与 manifest
  ├── schema.sql      初始建表
  └── application.yml

service/     Windows 服务包装（WinSW）
docs/        技术方案.md —— 完整设计与各版本验证报告（约 1400 行）
```

## 文档

- `docs/技术方案.md`：从零开始的设计、版本演进、每一轮的实测报告（含踩坑记录）
- 运行时产生的 `data/`、`logs/`、`backup/`、`target/` **不入库** —— 个人数据留在本地

## License

个人项目，未选 License —— 仅自己使用/备份。打算公开或让别人用时再补。
