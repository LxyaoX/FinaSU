#pragma once

#include "zygiskd.hpp"

#include <sys/types.h>

#include <cstdint>

namespace zygiskd::logging {

void set_kernel_mirror(bool enabled);
void record_linker_offsets(const char *linker_path, const char *dlopen_symbol,
                           uint64_t dlopen_offset, const char *dlsym_symbol,
                           uint64_t dlsym_offset, int kernel_result);
void write(LogLevel level, LogSource source, pid_t pid, uid_t uid,
           const char *message);
[[gnu::format(printf, 3, 4)]] void writef(LogLevel level, LogSource source,
                                          const char *format, ...);

} // namespace zygiskd::logging
