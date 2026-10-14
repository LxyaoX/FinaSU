# FinaSU Lua Plugin API

This document is intended for plugin authors and describes the Lua interface, plugin manifest, and execution model currently implemented by `ksud`. The source code in the repository is authoritative; interfaces that are only planned should not be treated as implemented features.

## 1. Runtime Environment and Security Boundaries

- Currently uses Lua 5.5, with `mlua` as the binding library. The interpreter is built into `ksud` and does not depend on a Lua executable on the device.
- Plugins are installed at `/data/adb/plugins/<id>/`, and the default entry point is `main.lua`.
- The entry point must return a table; callbacks are functions in this table, not functions merely defined in the global environment.
- Callbacks receive no arguments; return values are not used as CLI output or exit codes. A normal return indicates success, and an uncaught Lua error indicates failure.
- Each ordinary invocation, as well as each round of the daemon, creates a new Lua state and reloads the entry point. Local variables and global variables cannot be used for persistence across rounds; use configuration or files instead.
- The Lua standard library is loaded using `mlua::StdLib::ALL_SAFE`. Here, "safe" refers to the binding library's loading option and **does not represent root privilege isolation or a filesystem sandbox**.
- The file API does not enforce that paths be restricted to the plugin directory, and `exec` can launch system programs. Plugins have the privileges of the `ksud` process in which they run; only install trusted plugins.
- Relative paths are resolved according to the process's current working directory, and it cannot be assumed that this is the plugin directory. Plugin-owned files should use `PLUGIN_DIR .. "/filename"`.

In the function signatures in this document, `string`, `boolean`, `integer`, and `table` are parameter or return value type descriptions, not Lua type annotation syntax. Types that do not meet the binding requirements usually trigger a Lua error.

## 2. Minimal Plugin

Place `plugin.json` and `main.lua` directly in the ZIP root directory, without an extra enclosing directory:

```text
hello-plugin.zip
├── plugin.json
└── main.lua
```

`plugin.json`:

```json
{
  "id": "hello_plugin",
  "name": "Hello Plugin",
  "author": "Your Name",
  "version": "1.0.0",
  "description": "A minimal Lua plugin",
  "entry": "main.lua",
  "callbacks": ["service", "action"],
  "has_action": true
}
```

`main.lua`:

```lua
return {
    service = function()
        info("service started: " .. PLUGIN_ID)
    end,
    action = function()
        local value = get_config("greeting")
        if value == "" then
            value = "Hello from Lua"
        end
        info(value)
        assert(write_file(PLUGIN_DIR .. "/hello.txt", value .. "\n"))
    end,
}
```

Verify in a root shell on the device (the following commands are not desktop commands):

```sh
/data/adb/ksud plugin install /data/local/tmp/hello-plugin.zip
/data/adb/ksud plugin run hello_plugin service
/data/adb/ksud plugin config --id hello_plugin set greeting 'Hello FinaSU'
/data/adb/ksud plugin action hello_plugin
/data/adb/ksud plugin log hello_plugin
```

## 3. Manifest Fields

| Field | Type | Meaning / Default |
| --- | --- | --- |
| `id` | string, required | Unique plugin ID, must match the installation directory name |
| `name`, `author`, `version`, `description`, `license` | string | Display information, default empty string |
| `descriptions` | object | Mapping from language identifiers to description text, default empty object |
| `min_version` | non-negative integer | Required minimum host version code, not version name; reject the manifest if higher than the current host |
| `depends` | string[] | Dependent plugin IDs, default empty array; cannot contain duplicates or depend on itself |
| `entry` | string | Lua entry file name, default `main.lua`; must be a single file name and cannot contain a directory |
| `callbacks` | string[] | Declared callback names, default empty array; `main` among them triggers automatic daemon startup during service |
| `config` | object[] | Display descriptions of configuration fields, default empty array, see below |
| `quick_action` | string or object | Quick action function name, or `{ "function": "function_name", "label": "text", "labels": {} }` |
| `has_action` | boolean | Default false; declaring the `action` callback or pointing the quick action to `action` also makes it true |

Name constraints:

- ID: 1–64 ASCII characters, first character is a letter or digit, the rest allow letters, digits, `-`, `_`, `.`.
- Entry: 1–128 ASCII characters, only letters, digits, `-`, `_`, `.` are allowed, and it cannot be `.` or `..`.
- Callback name: 1–64 ASCII characters, only letters, digits, and `_` are allowed. The implementation allows starting with a digit; such a name needs to be written as `["123callback"] = function() ... end` in a Lua table.
- Configuration key: 1–64 bytes, cannot contain `/` or `\`.

Example `config` field:

```json
{
  "key": "mode",
  "label": "Mode",
  "labels": { "zh-CN": "模式" },
  "type": "select",
  "default": "balanced",
  "options": ["balanced", "fast"]
}
```

Configuration types support `text` (default), `number`, `bool`, and `select`; `select` must have non-empty `options`, and configuration keys cannot be duplicated. The `default` in the manifest is a field description, and **`get_config` will not automatically fall back to this default value**. The Lua configuration interface always reads and writes strings, and does not validate values according to the types or options in the manifest.

The manifest's `callbacks` is not a whitelist for ordinary invocation: `plugin run` actually resolves the specified function from the table returned by the entry point. Declaring `quick_action.function` does not change the behavior of `plugin action`; the latter always calls `action`.

## 4. Callbacks and Lifecycle

| Purpose | Lua Function Name | Behavior |
| --- | --- | --- |
| Boot post-fs-data | `post_fs_data` | Executed in dependency order during automatic scheduling |
| Boot service | `service` | Executed in dependency order during automatic scheduling |
| Boot boot-completed | `boot_completed` | Executed in dependency order during automatic scheduling |
| Resident task | `main` | After the manifest declares `main`, it is started as a daemon during service scheduling, waiting 1 second between rounds |
| Action button / CLI action | `action` | Called by `plugin action <id>` |
| Custom invocation | Custom function name | Called by `plugin run <id> <callback>` |

Automatic scheduling requires the plugin to be enabled, the manifest to be valid, dependencies to be satisfied, and the installed content to have been confirmed by the host. Confirm the current content via `plugin install` or `plugin enable`; after externally replacing plugin content, the original confirmation may become invalid. Missing optional boot callbacks are skipped.

- The execution limit for ordinary `run` / `action` is 300 seconds. Concurrent execution is not serialized per plugin; state lock contention may still return a busy error.
- The single-run limit for automatic boot callbacks is 120 seconds; if the scheduling has a shared deadline, the actually available time is also limited by the remaining budget.
- The daemon does not have the 300-second timeout of ordinary callbacks. Only one daemon with the same name for the same plugin can run, and different callbacks can be started separately.
- `enable` does not immediately re-trigger boot callbacks or automatically restart a stopped daemon.
- `disable` and `uninstall` first check enabled dependents, then stop registered running processes. Stopping first sends TERM, waits up to 4 seconds, then sends KILL to those still alive and waits. If stopping fails, the command reports an error and does not continue the normal disable or deletion process.
- Do not treat the stopping mechanism as a complete reclamation guarantee for arbitrary background programs; programs that detach themselves from the process group or child processes newly created after the stop scan are not within the scope of the complete guarantee.
- Updating a daemon that holds a lock will be rejected; disable first, update successfully, then enable again.

## 5. Global Variables

| Name | Type | Example |
| --- | --- | --- |
| `PLUGIN_ID` | string | `"hello_plugin"` |
| `PLUGIN_DIR` | string | `"/data/adb/plugins/hello_plugin"`, no trailing `/` |

## 6. Logging

### `info(message: string)` / `warn(message: string)` → no return value

Append `[info] message\n` or `[warn] message\n` to the current plugin's `last_output.log`. Newline characters `\n` in the message are replaced with spaces; the entire entry is first assembled into a buffer and then committed through a single append write, avoiding interleaving of multiple writes during formatting. Log writing is best-effort, and failures do not throw errors to Lua; this does not guarantee complete writes in the event of disk errors.

```lua
info("plugin loaded")
warn("fallback configuration is in use")
```

Logs do not have a strict rolling capacity limit: when the log is opened, if the existing size is **greater than** 64 KiB, it is truncated to zero; after opening, continuous writing may still exceed this value. `plugin log` reads at most the last 64 KiB, and the first line may be incomplete.

`plugin clear-log` deletes the log file rather than truncating an already opened file. A running process may still write to the deleted inode; to thoroughly clean the logs of a running plugin, first disable the plugin, then clean the logs, and finally enable / restart as needed.

## 7. System Properties and Commands

### `getprop(name: string)` → string

Executes `/system/bin/getprop <name>` and returns the stdout text with trailing whitespace removed. The property name cannot be empty, is at most 256 bytes, and cannot contain NUL. Failure to launch the program throws an error; it does not throw based on the command exit code.

### `setprop(name: string, value: string)` → boolean

Executes `/system/bin/setprop <name> <value>`; returns true if the command exits successfully, otherwise false. Property name rules are the same as above; the value is at most 256 bytes and cannot contain NUL. Launch failure or invalid arguments throw an error.

```lua
info("boot completed: " .. getprop("sys.boot_completed"))
```

### `exec(program: string, ...args: string)` → table

Launches the program directly, without going through a shell. Arguments are passed separately and do not automatically expand variables, pipes, redirection, or wildcards. When shell syntax is needed, explicitly call `/system/bin/sh`, and do not concatenate untrusted input.

Return structure:

```lua
{ ok = true, code = 0, stdout = "...", stderr = "..." }
```

- `ok`: normal exit with exit code 0.
- `code`: exit code; when terminated by a signal, `128 + signal number`.
- `stdout` / `stderr`: separately captured text; non-UTF-8 bytes are decoded using replacement characters.
- stdin is connected to the null device. At most 256 arguments including the program name, and each argument cannot contain NUL.
- Each output stream retains at most 4 MiB; when the capture boundary is reached, `\n[output truncated]\n` is appended, and the program may be terminated.
- The API has no independent command timeout; the outer timeout of ordinary callbacks still applies, and a command inside a daemon may block the entire round.
- Non-zero exit codes are returned via the table and do not directly throw; launch failures, wait failures, etc. throw errors.

```lua
local result = exec("/system/bin/id")
if result.ok then
    info(result.stdout)
else
    warn("id failed: " .. tostring(result.code) .. " " .. result.stderr)
end
```

## 8. Files and Directories

| Function | Return Value | Behavior and Errors |
| --- | --- | --- |
| `read_file(path: string)` | string | Reads at most 4 MiB, non-UTF-8 uses replacement characters; read failure or exceeding the limit throws an error |
| `write_file(path: string, content: string)` | boolean | Overwrite write, automatically creates parent directories, sets permissions to `0644` after writing; content over 4 MiB throws an error, creation / writing / setting permissions failure returns false |
| `chmod(path: string, mode: integer \| string)` | boolean | Sets permissions; invalid mode throws an error, system operation failure returns false |
| `mkdir(path: string, recursive: boolean)` | boolean | When true, creates recursively; also returns true if it already exists and is a directory, other operation failures return false; the second argument must be passed explicitly |
| `rm(path: string)` | boolean | Deletes a file or empty directory; does not recursively delete non-empty directories, failure / path does not exist returns false |
| `list_dir(path: string)` | table | Returns a 1-based array of names sorted by name, without full paths; inability to list the directory or exceeding the count limit throws an error |
| `file_exists(path: string)` | boolean | Determines whether the path exists, for files or directories; returns false when existence cannot be confirmed |

For `chmod`, passing the octal string `"0755"` is recommended. Integers are interpreted numerically, with a range of 0–4095 (the numeric value of `07777`); **the integer `755` is not the octal permission `0755`**.

`list_dir` collects at most 10,000 valid UTF-8 names; entries that fail to read and non-UTF-8 names are skipped. The file API has no transactional guarantees; for example, when `write_file` returns false, the file may already have been truncated or partially written.

```lua
local dir = PLUGIN_DIR .. "/data"
assert(mkdir(dir, true))
assert(write_file(dir .. "/note.txt", "hello\n"))
assert(chmod(dir .. "/note.txt", "0600"))
info(read_file(dir .. "/note.txt"))
for _, name in ipairs(list_dir(dir)) do
    info(name)
end
assert(rm(dir .. "/note.txt"))
```

## 9. sysctl

### `sysctl(name: string, value: string)` → boolean

Maps a dot-separated name to a path under `/proc/sys/` and writes a string. For example, `"vm.swappiness"` corresponds to `/proc/sys/vm/swappiness`. Each segment may contain only ASCII letters, digits, `_`, `-`; empty segments are not allowed, and absolute paths cannot be used. The value is at most 256 bytes and cannot contain NUL.

Invalid names or values throw an error; write failure returns false. This interface has no read overload; reading can use `read_file`. The kernel may reject certain parameters, and before modifying system-level parameters you should confirm device support and keep a recovery plan.

## 10. JSON

### `json_decode(text: string)` → Lua value

The input is at most 4 MiB. Parsing failure throws an error. Conversion rules: object → string-keyed table, array → 1-based integer-keyed table, string / boolean / number → corresponding Lua type, null → nil.

**null is not preserved**: Lua tables cannot store nil values, so null fields in objects disappear, and null elements in arrays form holes; empty arrays and empty objects both become empty tables, and cannot be distinguished from the return value. Conversion of very large integers may also lose precision.

### `json_encode(value)` → string

Supports nil, boolean, finite numbers, string, and table. Functions, userdata and other types, circular references, non-finite numbers, and values exceeding limits throw errors.

- A non-empty table whose keys are exactly the consecutive integers `1..n` is encoded as an array.
- Other tables are encoded as objects; keys must be string or integer, and integer keys are converted to decimal strings. Duplicate key names after conversion cause an error.
- An empty table is encoded as `{}`, not `[]`. nil is encoded as `null`.
- Encoding traversal visits at most 10,000 value nodes, with a maximum depth of 32, and the final text is at most 4 MiB. These traversal limits are used for encode; decode uses input size limits and the JSON parser's own checks, and the two cannot be assumed to be completely symmetric based on this.

```lua
local text = json_encode({ enabled = true, names = { "one", "two" } })
local data = json_decode(text)
info(data.names[1])
```

## 11. Persistent Configuration

### `get_config(key: string)` → string

Reads from the current plugin's `config.json`. If the file or key does not exist, returns `""`; it does not fall back to the manifest's `default`. Existing non-string JSON values are returned as JSON text. Corrupted JSON, a non-object configuration file, an invalid key, read failure, or an oversized file throw an error.

### `set_config(key: string, value: string)` → true

Saves the value as a JSON string to the current plugin's `config.json`. It uses a configuration lock and temporary file replacement; a busy lock, invalid key, or write failure throws an error rather than returning false. The configuration file limit is 4 MiB.

Lua does not provide a function to delete configuration keys; the CLI provides `plugin config --id <id> delete <key>`. Do not assume that number / bool configuration is automatically converted to Lua number / boolean.

```lua
local count = tonumber(get_config("count")) or 0
assert(set_config("count", tostring(count + 1)))
local enabled = get_config("enabled") == "true"
```

## 12. Background Callbacks

### `start_daemon(callback: string, interval: integer)` → boolean

Starts an independent daemon for the current plugin. `callback` is resolved from the table returned by the entry point; `interval` is the number of seconds to wait after each round of the function returns. Values less than 1 are treated as 1 second, and it is **not a callback timeout, nor a fixed period**. If the callback does not return, the next round will not begin.

Returning true means the initial load and function resolution succeeded and a readiness notification was received; the readiness notification occurs before the first function call, and does not guarantee that the callback subsequently executes successfully or remains alive. Failure to become ready, an existing daemon with the same name, plugin disabled, and other cases may return false; errors such as invalid callback names throw Lua errors. In non-Android test environments, it returns false and logs a warning.

It is recommended that the callback perform a finite amount of work once and then return, leaving the daemon responsible for repeated invocation. Do not execute an infinite loop at the top level of the entry point, otherwise loading and the readiness handshake cannot complete.

```lua
return {
    action = function()
        if not start_daemon("tick", 5) then
            warn("tick daemon was not started")
        end
    end,
    tick = function()
        info("tick: " .. getprop("sys.boot_completed"))
    end,
}
```

This example can be started by `plugin action <id>`, without declaring `main`. Repeated startup will not create a second daemon with the same name; use `plugin disable <id>` to stop it, and after re-enabling it must be started again.

## 13. Error Handling and Limits Summary

Returning false and throwing an error are different contracts: use return values to check whether system operations succeeded; use `pcall` to catch argument errors, configuration errors, etc.

```lua
local ok, result = pcall(read_file, PLUGIN_DIR .. "/optional.txt")
if not ok then
    warn("optional file unavailable: " .. tostring(result))
end
```

| Item | Current Limit |
| --- | --- |
| Single Lua state memory | 64 MiB, not the memory limit of the entire process / subprocess |
| Ordinary callback / automatic boot callback | 300 seconds / at most 120 seconds |
| `exec` captured stdout, stderr | 4 MiB per stream, plus truncation notice; not a limit on all resources of the program |
| Single file read/write / JSON input and output | 4 MiB |
| JSON encoding depth / value node count | 32 / 10,000 |
| `list_dir` name count | 10,000 |
| Property name / property value / sysctl value | 256 bytes |
| `exec` argument count (including program name) | 256 |
| Manifest / configuration file | 1 MiB / 4 MiB |
| Log truncation threshold when opened / CLI read tail | greater than 64 KiB / at most 64 KiB |
| ZIP entries / single-file decompression / total decompression size | 2,048 / 16 MiB / 64 MiB |
| ZIP compression ratio | maximum 200 |

## 14. CLI Quick Reference and Source Code

The following commands are all used in a device root shell; `<id>` and the like are placeholders:

```text
/data/adb/ksud plugin list
/data/adb/ksud plugin install <zip>
/data/adb/ksud plugin uninstall <id>
/data/adb/ksud plugin enable <id>
/data/adb/ksud plugin disable <id>
/data/adb/ksud plugin run <id> <callback>
/data/adb/ksud plugin action <id>
/data/adb/ksud plugin log <id>
/data/adb/ksud plugin clear-log <id>
/data/adb/ksud plugin config --id <id> list
/data/adb/ksud plugin config --id <id> get <key>
/data/adb/ksud plugin config --id <id> set <key> <value>
/data/adb/ksud plugin config --id <id> delete <key>
```

Log file: `/data/adb/plugins/<id>/last_output.log`; configuration file: `/data/adb/plugins/<id>/config.json`; disable marker: `/data/adb/plugins/<id>/disable`. Runtime locks and registration files belong to host state and should not be deleted by plugins themselves.

Implementation references:

- [Lua API bindings and resource limits](../../userspace/ksud/src/plugin/api.rs)
- [Entry loading and ordinary callback execution](../../userspace/ksud/src/plugin/runtime.rs)
- [daemon readiness and round execution](../../userspace/ksud/src/plugin/daemon.rs)
- [Process stopping and runtime registration](../../userspace/ksud/src/plugin/process.rs)
- [Manifest validation](../../userspace/ksud/src/plugin/manifest.rs)
- [Configuration persistence](../../userspace/ksud/src/plugin/config.rs)
- [Automatic callback scheduling](../../userspace/ksud/src/plugin/stage.rs)
- [CLI definitions](../../userspace/ksud/src/plugin/mod.rs)
- [Path and installation capacity limits](../../userspace/ksud/src/defs.rs)