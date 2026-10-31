---
title: YukiZygisk
description: FinaSU 内置的 Zygisk 引擎、注入开关、守护进程状态与设置
createTime: 2026/10/26 00:00:00
permalink: /builtin/yukizygisk/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# YukiZygisk

<Badge text="Zygisk 注入" type="info" /> <Badge text="需要再刷一次内核模块" type="warning" />

YukiZygisk 是随 FinaSU 提供的 Zygisk 引擎。它和早期纯用户态实现不同：真正把模块映射进 zygote 的是内核特性 `feature/yukizygisk`，`ksud` 只负责安装载荷、开机拉起 `zygiskd` 守护进程，并把设置写进 `yzconfig.json`。

## 它由哪几部分组成

| 部分 | 位置 | 作用 |
| --- | --- | --- |
| 内核特性 | `kernelsu.ko` 内的 `feature/yukizygisk` | 拦截 zygote 的 exec、注册注入端点、执行注入 |
| 守护进程 | `/data/adb/ksu/bin/zygiskd64`（64 位设备另有 `zygiskd32`） | 扫描模块、维护端点、把设置和状态交给内核 |
| 注入库 | `/data/adb/ksu/lib/yukizygisk/lib*.so` | 进入应用进程的运行时 |
| 设置 | `/data/adb/ksu/yukizygisk/yzconfig.json` | 六个开关，守护进程启动时读取 |
| 诊断 | `/data/adb/ksu/yukizygisk/diagnostics/current/` | 每个启动周期的日志与证据 |
| 内置模块 | `/data/adb/ksu/lib/yukizygisk/builtin/playintegrityfix`，链接到 `/data/adb/modules/playintegrityfix` | 随 `ksud` 分发的 PlayIntegrityFix 载荷 |
| 检测标记 | `/data/adb/modules/yukizygisk/libzygisk.so`（符号链接） | 让用 `find -name libzygisk.so` 判断“有没有 Zygisk”的安装脚本认得出本引擎 |

::: warning 只更新管理器不会产生注入

注入发生在内核里。刷入新的管理器之后，还要刷入同一版本的 `kernelsu.ko`，否则页面会显示“内核模块版本过旧”。

:::

## 页面上的状态

| 状态 | 含义 | 怎么办 |
| --- | --- | --- |
| 运行中 · N 个守护进程 | 守护进程已就绪并注册了端点 | 无需操作，下方列出各 ABI 的 pid |
| 内核注入未开启 | 守护进程在跑，但内核的注入开关没打开 | 点该行重启引擎 |
| 内核模块版本过旧 | 当前 `kernelsu.ko` 不含注入运行时 | 刷入本版本的内核模块 |
| 引擎已退出 | 守护进程启动失败 | 页面显示它写下的最后一行日志 |
| 未运行 | 载荷已安装，但没有进程 | 点该行重试，或查看下方日志 |

一个守护进程会为每个已加载模块 `fork` 一个伴生进程，因此 `ps` 里看到多个 `zygiskd64` 是正常的：状态行只统计守护进程本身，一个 ABI 一个。

## 总开关

页面顶部是总开关，默认开启。关闭后守护进程会停止，并且下次开机不再启动——如果某个模块让系统无法使用，这是最直接的恢复手段。开关状态记录为 `/data/adb/ksu/yukizygisk/disabled` 是否存在，不需要写配置文件。

## 内置的 PlayIntegrityFix

管理器自带一份 [PlayIntegrityFix](https://github.com/KOWX712/PlayIntegrityFix)。它不是一个需要安装的模块：载荷随 `ksud` 分发，每次开机写到 `/data/adb/ksu/lib/yukizygisk/builtin/playintegrityfix`，再由 `ksud` 链接到 `/data/adb/modules/playintegrityfix`。

之所以还要链过去：这个库在自己的进程里按绝对路径打开 `module.prop`、`pif.prop` 和 `classes.dex`，没有任何参数能改这些路径。链接是一个符号链接，因此：

- 模块列表里看不到它，也不需要单独管理；
- NoMount 的挂载计划不会把它当成模块；
- 开机的模块脚本阶段不会执行它的脚本 —— 那两段属性脚本由 `ksud` 自己在 post-fs-data 与 boot-completed 阶段调用。

开关与指纹在 **设置 → 功能 → Play Integrity**；指纹下面那行“导入配置”可以整份替换 `pif.prop`——上游用 WebUI 做同一件事，这里把文件本身当编辑框，导入前会检查里面有没有可用的 `FINGERPRINT`。启用/禁用写的是 `disable` 标记，Zygisk 在 zygote 启动时读取，所以改完要重启；`pif.prop` 里的伪装开关直接写回文件，同样重启生效。`pif.prop` 是你自己的身份，重装管理器或重新写入载荷都不会覆盖它。

如果设备上已经装了官方模块，`/data/adb/modules/playintegrityfix` 是一个真实目录，内置载荷会让位并在页面上说明原因。点“接管已安装的模块”会先运行它的 `uninstall.sh` 把属性还原，再用内置载荷替换，配置保留。

::: tip 为什么载荷里有一个 `update` 文件

这个库启动时会校验 `module.prop` 的 CRC32，不匹配就拒绝加载。`update` 是上游自己的逃生口：它看到这个文件就认为“模块刚更新过”，跳过校验，连那次唯一的写打开也一起跳过。载荷每次开机都由 `ksud` 重写，校验值不可能一直对齐，所以标记一直存在——真正会被写入的只有你在页面上改的 `pif.prop`。

:::

::: warning 没有 Zygisk 时安装器会拒绝运行

上游模块的安装脚本用 `find /data/adb/modules -name libzygisk.so` 判断有没有 Zygisk 实现。本引擎的库放在 `/data/adb/ksu/lib/yukizygisk/`，不在模块目录里，所以那条命令找不到东西，安装脚本会直接退出——这也是这些能力内置在管理器里的原因之一。为了同样的脚本在装**其它**模块时也能过这一关，`ksud` 会在 `/data/adb/modules/yukizygisk/` 放一个指向 `libzygisk64.so` 的 `libzygisk.so` 链接；该目录既没有 `module.prop` 也没有 `zygisk/<abi>.so`，因此不会被当成模块，zygiskd 也不会加载它。

:::

## 设置项

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| 使用 YukiLinker | 开 | 用内置链接器加载模块，而不是系统的 `dlopen` |
| 匿名内存 | 开 | 把运行时核心与模块映射到匿名内存 |
| 早期原生加载 | 关 | 在启动早期加载部分原生模块 |
| 模块崩溃保护 | 开 | 隔离导致系统框架崩溃的模块并重试启动 |
| 内核日志 | 关 | 把引擎日志同时写进内核日志 |
| 拒绝名单模式 | 关闭 | 对拒绝名单内应用的模块处理方式：关闭 / 强制 / 恢复 |

修改任一项都会写入 `yzconfig.json` 并立即重启引擎，因为守护进程只在启动时读这份文件。

## 日志与诊断

```sh
su -c '/data/adb/ksud yukizygisk status'
su -c 'tail -n 50 /data/adb/ksu/yukizygisk/diagnostics/current/logs/zygiskd64.log'
```

`status` 输出 `installed`、`supported`、`enabled`、`kernelSupported`、`featureEnabled`、`daemons` 与 `config`；页面上的每一行都来自这里。`lastError` 在没有守护进程时给出最后一条失败原因。

## 常见问题

::: collapse accordion

### 装了 APK、也开了开关，为什么还是不注入？

先看页面是否提示“内核模块版本过旧”。注入的一半在内核里，旧 `kernelsu.ko` 没有对应特性，守护进程会在启动时退出。刷入本版本的内核模块后再看状态。

### 为什么 `ps` 里有很多个 `zygiskd64`？

那是每个模块一个的伴生进程，不是多个守护进程。状态行按生命周期锁统计，一个 ABI 只会显示一个。

### 改了设置需要重启吗？

不需要手动重启设备：保存时引擎会自行重启守护进程。但如果内核模块换过版本，仍然需要重新刷入并重启。

:::
