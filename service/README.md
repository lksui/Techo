# Techo 部署说明

让 Techo 脱离 IDEA 独立运行：开机自启、崩溃自动重启、手机随时能访问。

---

## 一、先构建

服务跑的是打好的 jar，所以先构建一次：

```cmd
cd /d D:\DSHWorkSpace\Techo
mvn clean package
```

产物：`target\techo-1.0.0.jar`

> 以后每次改了代码，都要重新执行这一步，然后重启服务（或在服务里点重启）。

---

## 二、安装为 Windows 服务

**右键 `install-service.cmd` → 以管理员身份运行**

脚本会自动完成：检查管理员权限 → 检查 jar 是否存在 → 注册服务 → 启动 → 显示状态。

服务被设为**自动启动**，开机会自己跑起来，不需要登录桌面，也不需要开着 IDEA。

### 常用管理命令

在 `service` 目录下（需要管理员权限的用管理员窗口）：

| 操作 | 命令 |
|---|---|
| 查看状态 | `techo.exe status` |
| 启动 | `techo.exe start` |
| 停止 | `techo.exe stop` |
| 重启 | `techo.exe restart` |
| 卸载 | `techo.exe uninstall`（或运行 `uninstall-service.cmd`） |

也可以直接用 Windows 的「服务」管理器（`services.msc`）找到 **Techo 手账**。

### 崩溃会自动重启

配置里设了失败后依次等 10 秒、30 秒、60 秒重启，1 小时后计数器重置。
不会出现「崩了就一直疯狂重启刷爆日志」的情况。

---

## 三、放行防火墙

**管理员 PowerShell** 里执行：

```powershell
New-NetFirewallRule -DisplayName "Techo 8080" `
    -Direction Inbound -Protocol TCP -LocalPort 8080 `
    -Action Allow -Profile Any -RemoteAddress 192.168.1.0/24
```

`-RemoteAddress 192.168.1.0/24` 的含义是：**只允许家里这个网段的设备连进来**。

这样即使电脑以后连到别的网络（很多家用路由器默认也是 `192.168.1.x`，酒店、咖啡馆常见），
安全边界依然由网段控制，不依赖 Windows 对网络是「公用」还是「专用」的判定。

删除规则：`Remove-NetFirewallRule -DisplayName "Techo 8080"`

---

## 四、固定 IP（手机才能一直用同一个地址）

电脑现在的地址是 DHCP 分的，路由器重启后可能变。两种做法：

### 方案 A：在路由器里绑定（推荐）

打开路由器管理页（通常是 `http://192.168.1.1`），找到：

> **DHCP 保留** / **静态地址分配** / **IP-MAC 绑定** / **Address Reservation**

把本机 MAC 地址绑定到一个固定 IP（比如 `192.168.1.6`）。

**为什么推荐这个**：地址仍然由路由器统一管理，不会出现两台设备抢同一个 IP 的情况。
换路由器时也只需重新配一次。

### 方案 B：在 Windows 里设固定 IP（备选）

> ⚠️ **有风险**：配错了会直接断网，而且需要人到机器前面才能改回来。
> 如果这台电脑还连别的网络（公司、学校），**不要用这个方法**。

**管理员 PowerShell**：

```powershell
# 先看一眼当前配置，把网关和 DNS 抄下来
ipconfig /all

# 设为固定 IP（把网关、DNS 换成你自己的）
New-NetIPAddress -InterfaceAlias "以太网" -IPAddress 192.168.1.6 `
    -PrefixLength 24 -DefaultGateway 192.168.1.1

Set-DnsClientServerAddress -InterfaceAlias "以太网" -ServerAddresses 192.168.1.1, 223.5.5.5
```

改回自动获取：

```powershell
Remove-NetIPAddress -InterfaceAlias "以太网" -IPAddress 192.168.1.6 -Confirm:$false
Set-DhcpServerv4OptionValue  # 如果这条不可用，直接到网卡属性里把 IPv4 改回「自动获得」
```

---

## 五、确认一切正常

1. **本机**：浏览器打开 `http://localhost:8080` → 应该跳转到登录页
2. **手机**：连同一个 WiFi，打开 `http://192.168.1.6:8080` → 同样应该看到登录页
3. **重启电脑**，不要手动启动任何东西，再执行第 2 步

三条都通过，部署就完成了。

---

## 六、排错

| 现象 | 原因 | 处理 |
|---|---|---|
| 服务启动后立刻停止 | 多半是 jar 路径不对或 JDK 路径变了 | 看 `logs\service\techo.wrapper.log` |
| 手机连不上，本机正常 | 防火墙没放行 | 重做第三节，`Get-NetFirewallRule -DisplayName "Techo 8080"` 确认存在且已启用 |
| 手机 ping 得通但网页打不开 | 路由器开了 **AP 隔离**（访客网络常见） | 换到主网络，或在路由器关闭「AP 隔离 / 客户端隔离」 |
| IP 变了 | DHCP 重新分配 | 做第四节的静态 IP |
| 电脑睡眠后连不上 | 电源策略 | 「电源选项」里把睡眠设为**从不** |
| 忘记登录密码 | — | 停掉服务，用 SQLite 工具删掉 `app_setting` 表中 `key='auth.password-hash'` 那一行，重启后会重新进入设置密码流程 |

### 服务日志在哪

| 日志 | 位置 | 内容 |
|---|---|---|
| 应用日志 | `..\logs\techo.log` | Spring Boot 的业务日志，**主要看这个** |
| 服务包装器日志 | `..\logs\service\techo.wrapper.log` | 进程启动失败、退出码等 |

---

## 七、数据在哪（备份时认准这几个）

| 内容 | 路径 |
|---|---|
| 数据库 | `..\data\journal.db`（还有 `-wal`、`-shm` 两个配套文件） |
| 自动快照 | `..\backup\` |
| 日志 | `..\logs\` |

> ⚠️ **备份时不要只复制 `journal.db`。**
> WAL 模式下最新的写入在 `journal.db-wal` 里，单独复制主库会丢掉最近的改动。
> 用管理页的「立即备份」生成快照，或者把三个文件一起复制（需要先停服务）。
