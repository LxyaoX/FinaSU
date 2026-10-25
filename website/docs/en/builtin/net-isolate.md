---
title: Network Isolation
description: Per-UID network isolation modes, exception addresses, blocked events and boundaries in FinaSU
createTime: 2026/10/25 00:00:00
permalink: /en/builtin/net-isolate/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Network Isolation

<Badge text="Kernel Feature" type="info" /> <Badge text="Rules Update Immediately" type="tip" /> <Badge text="Not a System Firewall" type="danger" />

==Network Isolation intercepts newly established external connections per app UID==. Rules take effect immediately after being written to the kernel, but connections that are already established are not actively torn down, and apps that only access the local loopback may also be affected by normal isolation.

::: important Confirm the Target First

It suits restricting a particular ordinary app or test sandbox, not replacing an Android firewall, VPN or system-level network policy. Do not isolate system components, VPN, DNS proxies or shared UIDs you do not yet understand.

:::

## Opening the Page

The entry is under **Settings → Features → Network Isolation**. The page is divided into:

1. Enable switch.
2. Global isolation mode.
3. Advanced exceptions.
4. Target apps and per-app policy.
5. Blocked events and clearing settings.

You cannot enable it without selecting any app. After enabling you can keep changing targets, modes and exceptions; changes are synchronized to the current kernel state and no reboot is needed just to save the configuration.

## Three Modes

::: table Modes and Behavior

| Mode | New TCP/UDP IP Connections | New Sockets | Loopback | Use Case |
| --- | --- | --- | --- | --- |
| **Normal isolation** | Blocks connections and sends to addresses that are not allowed | Allows creating sockets | Blocked by default | Fully isolate the target app's network |
| **Allow loopback** | Only allows `127.0.0.0/8` and `::1` | Allows creating sockets | Allowed | The app only talks to local services |
| **Strict offline** | Keeps intercepting connection paths | Refuses to create IPv4/IPv6 sockets | Not allowed | When a stronger block is needed and socket creation failure is acceptable |

:::

Normal and loopback modes mainly intercept new connection paths such as `connect`, `sendto`, `sendmsg` and `sendmmsg`. Strict mode additionally refuses to create `AF_INET` and `AF_INET6` sockets in the kernel, returning the request as a permission error.

::: warning Strict Offline Does Not Clear Existing Connections

Existing TCP connections may still send and receive data. It blocks new sockets and later new network paths, not an immediate interruption of all existing traffic.

:::

## Address Exceptions

The advanced settings contain two kinds of exceptions. They apply to the whole group of selected UIDs, not as a per-app "allowlist".

:::: field-group

::: field Allow loopback
@type `Address exception`
Only allows IPv4 `127.0.0.0/8` and IPv6 `::1`. A local proxy may forward loopback requests on to the public internet.
:::

::: field Allow private network
@type `Address exception`
Allows RFC1918, IPv6 ULA and IPv4/IPv6 link-local addresses, including routers and LAN devices.
:::

::: field Allow local discovery
@type `Address exception`
Only allows the fixed mDNS/SSDP multicast targets and does not automatically allow the whole private subnet. Some apps also need the private network allowed at the same time.
:::

::: field Strict mode conflict
@type `Mutually exclusive`
Strict offline cannot be enabled together with any allow exception. To change strict mode to normal/loopback, disable the corresponding exceptions first.
:::

::::

Specific targets include:

- Loopback: `127.0.0.0/8`, `::1`.
- Private network: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, IPv6 ULA `fc00::/7` and link-local `fe80::/10`.
- Local discovery: mDNS `224.0.0.251`, `ff02::fb`; SSDP `239.255.255.250`, `ff02::c`.

::: tip Risks of a Local Proxy

With loopback enabled, apps can connect to a local proxy. Whether the proxy then forwards traffic to the internet is up to the proxy; Network Isolation will not analyze the targets the proxy resolves for you.

:::

## Selecting Target Apps

The target list holds at most ==256 UIDs== and requires unique UIDs. The Manager only lists ordinary app UIDs and excludes the Manager itself and WebView Zygote.

### Shared UIDs

Apps sharing a UID on Android are isolated together. Selecting one app in the list also applies the same policy to other apps with the same UID.

### Manager Exemption

UID 0 is permanently exempt. The FinaSU Manager is always exempt, including the Manager identity inside a work profile, so that isolating the Manager cannot leave you unable to return to the page to turn the feature off.

### 32-bit Coverage

When the kernel supports it, Network Isolation also covers the compatibility system calls of 32-bit and 64-bit apps. The page shows whether the current kernel has this coverage; without it, interception for 32-bit apps is incomplete.

## Per-App Policy

When the kernel provides a per-UID policy interface, you can choose separately for different apps:

- **Normal isolation**
- **Allow loopback**
- **Strict offline**

"Allow private network" and "Allow local discovery" remain shared settings for the whole group. When changing an app's strict/loopback policy, the Manager checks whether it conflicts with these shared exceptions.

::: warning Older Kernels Can Only Use a Uniform Policy

When the kernel does not support per-app policies, the related entries are unavailable. All target UIDs then use the same set of global mode and exceptions; a mixed configuration cannot be forced.

:::

## Enabling and Updating

:::: steps

1. **Select target apps first**

   Start with a non-critical app and confirm that no critical background service depends on that UID.

2. **Choose the isolation mode**

   Consider "Allow loopback" first when local communication is needed; use "Strict offline" only when you explicitly want to block socket creation.

3. **Add exceptions as needed**

   Enable LAN device access, mDNS or SSDP discovery at the smallest scope. Do not allow the whole private network long-term just to exclude one address.

4. **Enable and verify**

   Have the target app make one known request, then look at "recently blocked". A successful rule update does not require a device reboot.

5. **Restart the target app**

   If the app still uses old connections or caches, fully close and reopen it. Existing connections do not disappear automatically because of a rule update.

::::

## Recently Blocked

The kernel keeps an event ring with a capacity of 64 entries, recording the most recent interception sites:

- Time.
- UID.
- Operation: `connect`, `sendto`, `sendmsg`, `sendmmsg` or `socket`.
- Address family and address.
- Port.

The capacity is limited, older records are overwritten, and the page shows how many were overwritten. Clearing events does not change the isolation configuration and does not turn isolation off.

::: table Interpreting an Event

| Event | Possible Cause | Action |
| --- | --- | --- |
| Public IP + normal mode | The app tried to reach the internet | This is the expected block |
| `127.0.0.1` + normal mode | A local proxy, WebView or service call was blocked | Decide whether loopback should be allowed |
| Private address blocked | The app accesses a router or LAN device | Allow the private network only when genuinely needed |
| Multicast address blocked | mDNS/SSDP discovery fails | Check whether the local discovery exception is needed |
| `socket` event | Strict mode prevented creating an IP socket | Switch to normal mode if the app cannot tolerate it |

:::

## Boundaries and Risks

- It only constrains traffic from selected UIDs that passes through the hooked system call paths.
- It does not replace a VPN, firewall, DNS filtering or system-level access control.
- It does not guarantee that existing connections are closed immediately.
- It does not guarantee blocking network access performed indirectly through other processes, system services or privileged proxies.
- Loopback and private network exceptions widen what is reachable; enable only the necessary items.
- Isolating system UIDs may break device functionality; the Manager excludes some dangerous targets, but it does not judge every business dependency.
- Rules existing does not mean the target app has reconnected; verify against actual requests and app behavior.

## State and Troubleshooting

:::: collapse accordion

### The page says the current kernel is not supported

The current kernel does not provide the Network Isolation feature, or the related system call hooks cannot be installed. Update to a kernel that includes the feature; this is not something ordinary settings can add.

### The mode button is grayed out

The corresponding address exception or strict socket capability is not reported as supported by the kernel. Older kernels may also lack per-app policies or 32-bit compatibility calls.

### The app still has network access after enabling

First confirm the app UID is correct, whether it shares a UID, whether the app uses existing connections, and whether the current kernel covers its ABI. Check the blocked events; no event does not prove traffic passed, since it may also have used an uncovered path.

### Loopback is allowed but the app still cannot connect

It may depend on a local proxy that connects upstream; it may also need the private network or local discovery at the same time. Do not disable isolation outright; confirm the target addresses from the events first.

### Strict mode cannot be enabled together with exceptions

This is a design limit. Strict mode refuses to create IP sockets, so address-based allow exceptions can no longer be evaluated. Disable strict mode first, then choose normal or loopback.

### It still blocks after clearing records

"Clear records" only empties diagnostic events and does not modify rules. Turn off the main switch to stop isolation; use "Clear settings" to delete targets and configuration.

::::

## Configuration and Diagnostics Locations

```file-tree title="Network Isolation locations"
/data/adb/ksu
├── netisolate.json             # UIDs, modes, exceptions and enable state
└── .netisolate.lock            # configuration transaction lock
```

::: code-tabs

@tab Manager

```text
Settings → Features → Network Isolation
Check kernel support state first, then configure targets and exceptions
```

@tab Status

```sh
/data/adb/ksud netisolate status
```

@tab Events

```sh
/data/adb/ksud netisolate events
```

:::

The status and events commands only read current kernel information. Do not edit `netisolate.json` directly, otherwise the on-disk configuration may differ from the kernel rules in force.

## Recommended Strategy

1. **Start with normal isolation.** Allow loopback only when the app explicitly needs local services.
2. **Keep exceptions as small as possible.** Enable the private network and local discovery only when the corresponding function fails.
3. **Use strict mode only for apps that definitely do not need sockets.** It makes no promise about cutting existing connections.
4. **Change one UID at a time.** This makes it easier to tell from the blocked events which rule caused the change.
5. **Treat the log as diagnostics, not firewall policy.** The event ring has limited capacity and cannot replace continuous auditing.
