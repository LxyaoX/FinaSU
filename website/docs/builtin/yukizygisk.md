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
