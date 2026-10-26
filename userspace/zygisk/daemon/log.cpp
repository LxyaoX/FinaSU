#include "log.hpp"

#include "defs.hpp"

#include <ctime>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstring>

namespace zygiskd::logging {
namespace {

constexpr off_t kMaxLogSize = static_cast<off_t>(1024) * 1024;
constexpr uint64_t kRateTickMilliseconds = 250;
constexpr uint16_t kRateBurst = 256;
constexpr uint64_t kPriorityRateTickMilliseconds = 1000;
constexpr uint16_t kPriorityRateBurst = 64;
constexpr uint16_t kDaemonRateBurst = 128;
constexpr uint16_t kDaemonPriorityRateBurst = 32;
constexpr size_t kKernelRecordSize = 960;
constexpr char kBootIdPath[] = "/proc/sys/kernel/random/boot_id";
constexpr char kCurrentBootIdPath[] =
    "/data/adb/ksu/yukizygisk/diagnostics/current/boot_id";
#if defined(__LP64__)
constexpr const char *kLogPath = ksud::YUKIZYGISK_LOG64_PATH;
constexpr const char *kRolledLogPath = ksud::YUKIZYGISK_ROLLED_LOG64_PATH;
constexpr const char *kLinkerStatePath = ksud::YUKIZYGISK_LINKER64_PATH;
constexpr char kAbi[] = "arm64-v8a";
#else
constexpr const char *kLogPath = ksud::YUKIZYGISK_LOG32_PATH;
constexpr const char *kRolledLogPath = ksud::YUKIZYGISK_ROLLED_LOG32_PATH;
constexpr const char *kLinkerStatePath = ksud::YUKIZYGISK_LINKER32_PATH;
constexpr char kAbi[] = "armeabi-v7a";
#endif
std::atomic_bool g_kernel_mirror{false};
std::atomic<uint64_t> g_runtime_rate_state{kRateBurst};
std::atomic<uint64_t> g_runtime_priority_rate_state{kPriorityRateBurst};
std::atomic<uint64_t> g_daemon_rate_state{kDaemonRateBurst};
std::atomic<uint64_t> g_daemon_priority_rate_state{kDaemonPriorityRateBurst};
std::atomic<uint32_t> g_runtime_dropped{0};
std::atomic<uint32_t> g_daemon_dropped{0};
std::atomic_int g_kernel_fd{-1};
std::atomic_int g_generation_state{-1};

bool take_rate_token(std::atomic<uint64_t> &state, uint16_t burst,
                     uint64_t tick_milliseconds) {
  timespec now{};
  if (clock_gettime(CLOCK_MONOTONIC, &now) != 0)
    return true;
  const uint64_t milliseconds = (static_cast<uint64_t>(now.tv_sec) * 1000) +
                                (static_cast<uint64_t>(now.tv_nsec) / 1000000);
  const uint64_t tick = milliseconds / tick_milliseconds;

  uint64_t old = state.load(std::memory_order_relaxed);
  for (;;) {
    const uint64_t old_tick = old >> 16;
    uint64_t tokens = old & 0xffff;
    if (tick > old_tick)
      tokens = std::min<uint64_t>(burst, tokens + tick - old_tick);
    if (tokens == 0)
      return false;

    const uint64_t next = (tick << 16) | (tokens - 1);
    if (state.compare_exchange_weak(old, next, std::memory_order_relaxed))
      return true;
  }
}

bool should_write(LogLevel level, LogSource source) {
  auto &rate_state =
      source == LogSource::Daemon ? g_daemon_rate_state : g_runtime_rate_state;
  const uint16_t rate_burst =
      source == LogSource::Daemon ? kDaemonRateBurst : kRateBurst;
  if (take_rate_token(rate_state, rate_burst, kRateTickMilliseconds))
    return true;

  auto &priority_rate_state = source == LogSource::Daemon
                                  ? g_daemon_priority_rate_state
                                  : g_runtime_priority_rate_state;
  const uint16_t priority_rate_burst = source == LogSource::Daemon
                                           ? kDaemonPriorityRateBurst
                                           : kPriorityRateBurst;
  return level >= LogLevel::Warning &&
         take_rate_token(priority_rate_state, priority_rate_burst,
                         kPriorityRateTickMilliseconds);
}

int kernel_log_fd() {
  int fd = g_kernel_fd.load(std::memory_order_relaxed);
  if (fd >= 0)
    return fd;

  const int opened = open("/dev/kmsg", O_WRONLY | O_CLOEXEC);
  if (opened < 0)
    return -1;
  if (g_kernel_fd.compare_exchange_strong(fd, opened,
                                          std::memory_order_relaxed))
    return opened;
  close(opened);
  return fd;
}

std::atomic<uint32_t> &dropped_counter(LogSource source) {
  return source == LogSource::Daemon ? g_daemon_dropped : g_runtime_dropped;
}

char level_char(LogLevel level) {
  switch (level) {
  case LogLevel::Debug:
    return 'D';
  case LogLevel::Info:
    return 'I';
  case LogLevel::Warning:
    return 'W';
  case LogLevel::Error:
    return 'E';
  }
  return '?';
}

const char *source_name(LogSource source) {
  switch (source) {
  case LogSource::Daemon:
    return "daemon";
  case LogSource::Zygisk:
    return "zygisk";
  case LogSource::Native:
    return "native";
  case LogSource::Linker:
    return "linker";
  }
  return "unknown";
}

int kernel_priority(LogLevel level) {
  switch (level) {
  case LogLevel::Debug:
    return 7;
  case LogLevel::Info:
    return 6;
  case LogLevel::Warning:
    return 4;
  case LogLevel::Error:
    return 3;
  }
  return 6;
}

void sanitize(char *message) {
  for (char *p = message; *p != '\0'; ++p) {
    if (*p == '\n' || *p == '\r')
      *p = ' ';
  }
}

bool write_all(int fd, const char *data, size_t size) {
  while (size > 0) {
    const ssize_t written = ::write(fd, data, size);
    if (written < 0 && errno == EINTR)
      continue;
    if (written <= 0)
      return false;
    data += written;
    size -= static_cast<size_t>(written);
  }
  return true;
}

bool read_boot_id(const char *path, std::array<char, 64> &value,
                  size_t &length) {
  const int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
  if (fd < 0)
    return false;
  struct stat status{};
  if (fstat(fd, &status) != 0 || !S_ISREG(status.st_mode) ||
      status.st_uid != 0) {
    close(fd);
    return false;
  }

  length = 0;
  for (;;) {
    const ssize_t count =
        ::read(fd, value.data() + length, value.size() - length);
    if (count < 0 && errno == EINTR)
      continue;
    if (count < 0 || (count > 0 && length == value.size())) {
      close(fd);
      return false;
    }
    if (count == 0)
      break;
    length += static_cast<size_t>(count);
    if (length == value.size()) {
      close(fd);
      return false;
    }
  }
  close(fd);
  while (length > 0 && static_cast<unsigned char>(value[length - 1]) <= ' ')
    --length;
  return length > 0;
}

bool current_generation_valid() {
  const int cached = g_generation_state.load(std::memory_order_relaxed);
  if (cached >= 0)
    return cached != 0;

  std::array<char, 64> current{};
  std::array<char, 64> stored{};
  size_t current_length = 0;
  size_t stored_length = 0;
  const bool valid = read_boot_id(kBootIdPath, current, current_length) &&
                     read_boot_id(kCurrentBootIdPath, stored, stored_length) &&
                     current_length == stored_length &&
                     memcmp(current.data(), stored.data(), current_length) == 0;
  g_generation_state.store(valid ? 1 : 0, std::memory_order_relaxed);
  return valid;
}

void sync_parent_directory(const char *path) {
  const char *slash = strrchr(path, '/');
  if (slash == nullptr || slash == path)
    return;
  std::array<char, 256> directory{};
  const size_t length = static_cast<size_t>(slash - path);
  if (length >= directory.size())
    return;
  memcpy(directory.data(), path, length);
  const int fd =
      open(directory.data(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
  if (fd >= 0) {
    (void)fsync(fd);
    close(fd);
  }
}

bool write_diagnostic_file(const char *path, const char *data, size_t size) {
  std::array<char, 256> temporary{};
  const int length =
      snprintf(temporary.data(), temporary.size(), "%s.tmp.%d", path, getpid());
  if (length <= 0 || static_cast<size_t>(length) >= temporary.size())
    return false;

  (void)unlink(temporary.data());
  const int fd =
      open(temporary.data(),
           O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
  if (fd < 0)
    return false;

  struct stat status{};
  bool ok = fstat(fd, &status) == 0 && S_ISREG(status.st_mode) &&
            status.st_uid == 0 && write_all(fd, data, size) && fsync(fd) == 0 &&
            fchmod(fd, 0600) == 0;
  close(fd);
  if (ok)
    ok = rename(temporary.data(), path) == 0;
  if (ok)
    sync_parent_directory(path);
  if (!ok)
    (void)unlink(temporary.data());
  return ok;
}

int open_log_file() {
  if (!current_generation_valid())
    return -1;
  const int fd =
      open(kLogPath,
           O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK,
           0600);
  if (fd < 0)
    return -1;

  struct stat st{};
  if (flock(fd, LOCK_EX) != 0 || fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) ||
      st.st_uid != 0) {
    close(fd);
    return -1;
  }
  if (fchmod(fd, 0600) != 0) {
    (void)flock(fd, LOCK_UN);
    close(fd);
    return -1;
  }
  return fd;
}

void close_log_file(int fd) {
  (void)flock(fd, LOCK_UN);
  close(fd);
}

void write_file(const char *record, size_t length) {
  int fd = open_log_file();
  if (fd < 0)
    return;

  struct stat st{};
  if (fstat(fd, &st) == 0 &&
      st.st_size >= kMaxLogSize - static_cast<off_t>(length)) {
    if (rename(kLogPath, kRolledLogPath) == 0) {
      sync_parent_directory(kLogPath);
      close_log_file(fd);
      fd = open_log_file();
      if (fd < 0)
        return;
    } else {
      (void)ftruncate(fd, 0);
    }
  }
  (void)write_all(fd, record, length);
  close_log_file(fd);
}

void write_kernel(LogLevel level, LogSource source, pid_t pid, uid_t uid,
                  const char *message) {
  if (!g_kernel_mirror.load(std::memory_order_relaxed))
    return;

  std::array<char, kKernelRecordSize> record{};
  const int length =
      snprintf(record.data(), record.size(), "<%d>yukizygisk: %s %s[%d:%u]: %s",
               kernel_priority(level), kDaemonLabel, source_name(source), pid,
               static_cast<unsigned int>(uid), message);
  if (length <= 0)
    return;

  if (static_cast<size_t>(length) >= record.size()) {
    record[record.size() - 4] = '.';
    record[record.size() - 3] = '.';
    record[record.size() - 2] = '.';
  }

  const int fd = kernel_log_fd();
  if (fd < 0)
    return;
  const size_t size = std::min(static_cast<size_t>(length), record.size() - 1);
  (void)write_all(fd, record.data(), size);
}

void emit(LogLevel level, LogSource source, pid_t pid, uid_t uid,
          const char *message) {
  std::array<char, kLogMessageMax + 1> clean{};
  (void)snprintf(clean.data(), clean.size(), "%s", message);
  sanitize(clean.data());

  timespec now{};
  (void)clock_gettime(CLOCK_REALTIME, &now);
  tm local{};
  (void)localtime_r(&now.tv_sec, &local);

  std::array<char, 1400> record{};
  const int length =
      snprintf(record.data(), record.size(),
               "%04d-%02d-%02d %02d:%02d:%02d.%03ld %c/%s %s[%d:%u]: %s\n",
               local.tm_year + 1900, local.tm_mon + 1, local.tm_mday,
               local.tm_hour, local.tm_min, local.tm_sec, now.tv_nsec / 1000000,
               level_char(level), kDaemonLabel, source_name(source), pid,
               static_cast<unsigned int>(uid), clean.data());
  if (length > 0) {
    const size_t size =
        std::min(static_cast<size_t>(length), record.size() - 1);
    write_file(record.data(), size);
  }
  write_kernel(level, source, pid, uid, clean.data());
}

} // namespace

void set_kernel_mirror(bool enabled) {
  g_kernel_mirror.store(enabled, std::memory_order_relaxed);
}

void record_linker_offsets(const char *linker_path, const char *dlopen_symbol,
                           uint64_t dlopen_offset, const char *dlsym_symbol,
                           uint64_t dlsym_offset, int kernel_result) {
  const int saved_errno = errno;
  if (!current_generation_valid()) {
    errno = saved_errno;
    return;
  }
  std::array<char, 1024> state{};
  const int length = snprintf(
      state.data(), state.size(),
      "{\n"
      "  \"abi\": \"%s\",\n"
      "  \"linker\": \"%s\",\n"
      "  \"dlopen_symbol\": \"%s\",\n"
      "  \"dlopen_offset\": \"0x%llx\",\n"
      "  \"dlsym_symbol\": \"%s\",\n"
      "  \"dlsym_offset\": \"0x%llx\",\n"
      "  \"kernel_result\": %d,\n"
      "  \"updated_at_unix\": %lld\n"
      "}\n",
      kAbi, linker_path ? linker_path : "", dlopen_symbol ? dlopen_symbol : "",
      static_cast<unsigned long long>(dlopen_offset),
      dlsym_symbol ? dlsym_symbol : "",
      static_cast<unsigned long long>(dlsym_offset), kernel_result,
      static_cast<long long>(time(nullptr)));
  if (length > 0 && static_cast<size_t>(length) < state.size() &&
      write_diagnostic_file(kLinkerStatePath, state.data(),
                            static_cast<size_t>(length))) {
    constexpr char kEvidence[] = "linker-offsets\n";
    (void)write_diagnostic_file(ksud::YUKIZYGISK_DIAGNOSTIC_EVIDENCE_PATH,
                                kEvidence, sizeof(kEvidence) - 1);
  }
  errno = saved_errno;
}

void write(LogLevel level, LogSource source, pid_t pid, uid_t uid,
           const char *message) {
  const int saved_errno = errno;
  if (message == nullptr) {
    errno = saved_errno;
    return;
  }
  auto &dropped = dropped_counter(source);
  if (!should_write(level, source)) {
    (void)dropped.fetch_add(1, std::memory_order_relaxed);
    errno = saved_errno;
    return;
  }

  const uint32_t dropped_count = dropped.exchange(0, std::memory_order_relaxed);
  if (dropped_count > 0) {
    std::array<char, 80> summary{};
    (void)snprintf(summary.data(), summary.size(),
                   "rate limit dropped %u messages", dropped_count);
    emit(LogLevel::Warning, source, pid, uid, summary.data());
  }
  emit(level, source, pid, uid, message);
  errno = saved_errno;
}

void writef(LogLevel level, LogSource source, const char *format, ...) {
  const int saved_errno = errno;
  std::array<char, kLogMessageMax + 1> message{};
  va_list args;
  va_start(args, format);
  const int length = vsnprintf(message.data(), message.size(), format, args);
  va_end(args);
  if (length < 0) {
    errno = saved_errno;
    return;
  }

  write(level, source, getpid(), getuid(), message.data());
  errno = saved_errno;
}

} // namespace zygiskd::logging
