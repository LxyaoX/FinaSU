---
title: Lua API Reference
description: The 18 global functions, return values, errors and resource limits of the FinaSU plugin runtime
createTime: 2026/10/25 00:00:00
permalink: /en/developer/lua-api/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Lua API Reference

<Badge text="Lua 5.5" type="tip" /> <Badge text="64 MiB memory" type="warning" /> <Badge text="Root privileges" type="danger" />

Every callback runs in a fresh Lua state. The host registers 2 global variables and 18 functions; plugins should rely only on these interfaces and must not treat native standard libraries such as `io` and `os` as a stable compatibility contract.

```lua
return {
  service = function()
    info("plugin=" .. PLUGIN_ID .. " dir=" .. PLUGIN_DIR)
    local result = exec("id")
    if not result.ok then
      warn("id failed: " .. result.code)
    end
  end,
}
```

## Global Variables

:::: table Read-only context

| Name | Type | Meaning |
| --- | --- | --- |
| `PLUGIN_ID` | string | Current plugin ID |
| `PLUGIN_DIR` | string | Absolute path of the current plugin install directory |

::::

Code, configuration and state files should be constructed from `PLUGIN_DIR`. When data must be kept across callbacks, write it into the plugin configuration or a state file you manage explicitly, because every invocation creates the Lua state anew.

## Logging

### `info(message)` / `warn(message)`

```lua
info("service started")
warn("optional feature is unavailable")
```

Both functions take one string and return nothing. Messages are written to the plugin `last_output.log`, and newlines are replaced with spaces so one log entry stays on one line.

The log file is capped at 64 KiB. Opening a log that already exceeds the limit truncates it first; do not treat the log as a long-term database.

## Properties

### `getprop(name)`

```lua
local sdk = getprop("ro.build.version.sdk")
```

Reads a system property and returns the string with trailing whitespace removed. The property name must be non-empty, at most 256 bytes and free of NUL.

The host invokes `/system/bin/getprop`. A Lua error is raised only on host errors such as the command failing to start; the current implementation does not check the exit status of `getprop`, so a missing property may return an empty string.

### `setprop(name, value)`

```lua
local ok = setprop("debug.example.enabled", "1")
```

Property name rules are the same as `getprop`; the value is at most 256 bytes and cannot contain NUL. Returns `true` on success, and `false` when the command exits non-zero or fails after starting.

::: warning Properties are not general-purpose configuration storage

Android properties have length, naming and permission limits. A plugin's own persistent data should use `get_config` / `set_config` or files inside `PLUGIN_DIR`.

:::

## Command Execution

### `exec(program, ...)`

```lua
local result = exec("/system/bin/sh", "-c", "printf 'hello\\n'")

if result.ok then
  info(result.stdout)
else
  warn("exit=" .. result.code .. " stderr=" .. result.stderr)
end
```

Starts the program directly; `program` plus arguments may total at most 256, and no argument may contain NUL. The child process stdin is empty.

Return value:

```lua
{
  ok = boolean,
  code = integer,
  stdout = string,
  stderr = string,
}
```

- `ok` tells whether the process ended with exit code 0.
- For a normal exit, `code` is the exit code.
- When killed by a signal, `ok=false` and `code=128+signal`.
- stdout and stderr each keep at most 4 MiB.
- When either output stream exceeds the limit, `[output truncated]` is appended and the child process is terminated.
- When the program cannot start, a Lua error is raised instead of returning a failure table.

::: danger Do not concatenate untrusted shell commands

`exec` does not accept a shell command string; when you need a shell, start `/system/bin/sh` explicitly. Even then, pass external input as separate arguments or escape it strictly, never by direct concatenation.

:::

## Filesystem

### `read_file(path)`

```lua
local text = read_file(PLUGIN_DIR .. "/data/state.json")
```

Reads at most 4 MiB of content. Non-UTF-8 bytes are converted lossily; a missing file, a read failure or exceeding the limit raises a Lua error.

### `write_file(path, content)`

```lua
local ok = write_file(PLUGIN_DIR .. "/data/state.txt", "ready\n")
```

Content is at most 4 MiB. Missing parent directories are created automatically; after a successful write the mode is `0644` and `true` is returned. A regular write failure returns `false`; content over the limit or a wrong argument type raises a Lua error.

### `sysctl(name, value)`

```lua
local ok = sysctl("vm.swappiness", "60")
```

`name` uses dotted form, for example `kernel.panic_on_oops`. It cannot be an absolute path or start with a dot, and each segment may only contain ASCII letters, digits, `_`, `-`. The value is at most 256 bytes and cannot contain NUL. Returns `true` on a successful write, otherwise `false`.

The actual path is always `/proc/sys/<segmented path>`.

### `chmod(path, mode)`

```lua
local ok = chmod(PLUGIN_DIR .. "/bin/helper", "0755")
```

`mode` can be an integer or an octal string. The range is `0` to `07777`. Lua has no octal literals, so always prefer the string form. Returns `true` on success.

### `mkdir(path, recursive)`

```lua
mkdir(PLUGIN_DIR .. "/data/cache", true)
```

`recursive` must be passed explicitly as a boolean. Returns `true` when the directory was created, and also `true` when the path already exists and is a directory; other failures return `false`.

### `rm(path)`

```lua
local removed = rm(PLUGIN_DIR .. "/data/old.txt")
```

First tries to remove a file, then tries to remove an empty directory. It does not delete recursively. Returns `true` on success and `false` otherwise.

### `list_dir(path)`

```lua
local entries = list_dir(PLUGIN_DIR .. "/data")
for _, name in ipairs(entries) do
  info(name)
end
```

Returns a 1-based array sorted by name. At most 10,000 entries are accepted; above that an error is raised. Non-UTF-8 file names are skipped.

### `file_exists(path)`

```lua
if file_exists(PLUGIN_DIR .. "/disable-feature") then
  return
end
```

Returns `true` when the path exists, otherwise `false`.

::: danger These functions have no directory sandbox

Plugins run with the privileges of `ksud`. Paths can point outside the plugin directory; the API size limits are resource boundaries, not permission boundaries. Install only trusted plugins, and avoid letting network input directly determine high-privilege write paths.

:::

## JSON

### `json_decode(text)`

```lua
local value = json_decode('{"enabled":true,"items":[1,2]}')
info(value.items[1])
```

Accepts up to 4 MiB of JSON. Arrays become Lua 1-based tables, objects become string-keyed tables, and JSON `null` becomes `nil`. A parse failure raises an error.

### `json_encode(value)`

```lua
local text = json_encode({enabled = true, items = {"a", "b"}})
```

Returns a JSON string, at most 4 MiB, with nesting depth up to 32 and at most 10,000 nodes. An error is raised when:

- a table contains a reference cycle;
- a number is `NaN` or infinite;
- an object key is neither a string nor an integer;
- keys collide after conversion;
- a value type cannot be represented.

An empty table encodes as `{}`. It encodes as an array only when the keys are exactly the consecutive integers `1..n`; a sparse table or mixed keys encode as an object.

## Plugin Configuration

### `get_config(key)`

```lua
local mode = get_config("mode")
if mode == "" then
  mode = "balanced"
end
```

The key is at most 64 bytes and cannot contain `/` or `\`. A key that was never stored returns the empty string `""`, not `nil`. An error is raised when the config file is corrupted, over the limit or not a valid JSON object.

`config[].default` in `plugin.json` is only used for the initial display value in the Manager and is never injected into Lua by the host. The plugin must handle the empty-value fallback itself.

### `set_config(key, value)`

```lua
set_config("mode", "fast")
```

Key rules are the same as `get_config`. The value must be convertible to a string and is stored as a JSON string. Returns `true` on success; an error is raised when the whole configuration exceeds 4 MiB, the file is corrupted or the write fails.

The configuration is actually stored in `config.json` in the plugin directory and does not disappear when the Lua state ends.

## Resident Callbacks

### `start_daemon(callback, interval)`

```lua
return {
  service = function()
    local ok = start_daemon("tick", 5)
    if not ok then
      warn("tick daemon did not start")
    end
  end,

  tick = function()
    info("tick")
  end,
}
```

Starts the given callback of the same plugin as a separate process:

- `callback` must match the callback name rules, at most 64 bytes.
- An `interval` below 1 is treated as 1 second.
- Returns `true` when it loaded successfully and reported ready.
- Returns `false` when the plugin is disabled, startup failed, or it was not ready within 10 seconds.
- Only one daemon per plugin per callback is allowed.
- Every round creates a brand new Lua state; Lua globals are not kept between rounds.

The host environment has no Android process control capability, so `start_daemon` always returns `false`.

::: warning A daemon callback is responsible for returning promptly

Daemon rounds dispatched from boot stages have no per-round 120 second supervision timeout. Callbacks must finish quickly and return; for long-running work, start and manage an external process explicitly.

:::

## Error Handling Quick Reference

:::: table Failure modes

| API | Raises a Lua error | Returns `false` / empty |
| --- | --- | --- |
| `info` / `warn` | Lua argument type error | None |
| `getprop` | Invalid argument, program cannot start | Command exit code is not checked |
| `setprop` | Invalid argument, program cannot start | Command execution failed |
| `exec` | Invalid argument, program cannot start, output read failure | Process exited non-zero or by signal |
| `read_file` | Argument error, read failure, over 4 MiB | None |
| `write_file` | Content over limit, wrong argument type | Regular write failure |
| `sysctl` | Invalid name or value | Write failure |
| `chmod` | Invalid mode | Setting failed |
| `mkdir` | Wrong argument type | Creation failed |
| `rm` | Wrong argument type | Removal failed |
| `list_dir` | Read failure, over 10,000 entries | None |
| `file_exists` | Wrong argument type | Path does not exist |
| `json_decode` | Invalid JSON or over 4 MiB | None |
| `json_encode` | Cannot be represented safely or over the limit | None |
| `get_config` | Invalid key, corrupted or over-limit file | Returns `""` when not set |
| `set_config` | Invalid key, write failure or total size over limit | None |
| `start_daemon` | Wrong argument type | Not ready, disabled or unsupported by the host environment |

::::

::::: collapse accordion

:::: collapse Why `pcall` is still necessary
A Lua error terminates the current callback. To produce a readable diagnostic or continue cleaning up, wrap calls that may fail in `pcall` and log the error.
::::

:::: collapse Why Lua functions cannot be kept across rounds
Every callback is a brand new state. Store strings, JSON or other persistent data, then rebuild functions and tables in the next round.
::::

:::: collapse Why output is truncated
stdout and stderr are each limited to 4 MiB; over the limit the child process is terminated and a truncation marker is appended, so a plugin cannot exhaust memory or stall the host.
::::

:::: collapse Why configuration values are strings
The Manager stores only JSON strings. Numbers and booleans must be parsed explicitly by the plugin; `get_config("count")` yields a string, not a number.
::::

:::::

## Related Pages

- [Lua Plugin Package & Lifecycle](/en/developer/lua-plugin/): manifest, install, callbacks, dependencies and Manager behavior.
- [Module Configuration](/en/developer/module-config/): the binary configuration interface of standard modules, which differs from the plugin `config.json`.
