#define _GNU_SOURCE
#include "uapi/yukizygisk.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/reboot.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

static void finish(int passed, const char *reason, int line) {
  printf("ENDPOINT_GUEST_%s %s line=%d errno=%d\n", passed ? "PASS" : "FAIL",
         reason, line, errno);
  fflush(stdout);
  reboot(RB_POWER_OFF);
  for (;;)
    pause();
}

#define CHECK(expression)                                                      \
  do {                                                                         \
    if (!(expression))                                                         \
      finish(0, #expression, __LINE__);                                        \
  } while (0)

static int control(void) {
  FILE *file = fopen("/proc/misc", "r");
  unsigned minor = 0, value;
  char name[256];

  CHECK(file);
  while (fscanf(file, "%u %255s", &value, name) == 2)
    if (!strcmp(name, "endpoint_probe"))
      minor = value;
  fclose(file);
  CHECK(minor);
  unlink("/dev/endpoint");
  CHECK(!mknod("/dev/endpoint", S_IFCHR | 0600, makedev(10, minor)));
  return open("/dev/endpoint", O_RDWR | O_CLOEXEC);
}

static int listener(unsigned sequence) {
  struct sockaddr_un address = {.sun_family = AF_UNIX};
  int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
  char name[33];

  CHECK(fd >= 0);
  snprintf(name, sizeof(name), "%032x", sequence);
  memcpy(address.sun_path + 1, name, 32);
  CHECK(!bind(fd, (struct sockaddr *)&address,
              offsetof(struct sockaddr_un, sun_path) + 33));
  CHECK(!listen(fd, 8));
  return fd;
}

static struct yz_endpoint_register_cmd publish(int ctl, unsigned abi, int fd) {
  struct yz_endpoint_register_cmd command = {.abi = abi, .listener_fd = fd};

  CHECK(!ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &command));
  CHECK(command.generation);
  return command;
}

static void wait_child(pid_t child) {
  int status;

  CHECK(waitpid(child, &status, 0) == child);
  CHECK(WIFEXITED(status) && !WEXITSTATUS(status));
}

static unsigned fd_count(void) {
  DIR *directory = opendir("/proc/self/fd");
  struct dirent *entry;
  unsigned count = 0;

  CHECK(directory);
  while ((entry = readdir(directory)))
    if (entry->d_name[0] != '.')
      ++count;
  closedir(directory);
  return count;
}

static void reuse_pid(int ctl, struct yz_endpoint_state *state) {
  int ready[2], resume[2];
  CHECK(!pipe(ready) && !pipe(resume));
  FILE *pid_max = fopen("/proc/sys/kernel/pid_max", "w");
  CHECK(pid_max && fprintf(pid_max, "320") > 0 && !fclose(pid_max));
  pid_t child;
  for (;;) {
    child = fork();
    CHECK(child >= 0);
    if (!child) {
      if (getpid() < 300)
        _exit(0);
      break;
    }
    if (child >= 300)
      break;
    wait_child(child);
  }
  if (!child) {
    publish(ctl, 1, listener(300));
    CHECK(write(ready[1], "R", 1) == 1);
    char byte;
    CHECK(read(resume[0], &byte, 1) == 1);
    _exit(0);
  }
  char byte;
  CHECK(read(ready[0], &byte, 1) == 1);
  CHECK(state->available && state->pid == (unsigned)child);
  uint64_t old_generation = state->generation;
  uint64_t old_start = state->start_boottime;
  CHECK(!kill(child, SIGKILL));
  int status;
  CHECK(waitpid(child, &status, 0) == child && WIFSIGNALED(status));
  CHECK(!state->available);
  pid_t reused;
  for (unsigned attempt = 0;; ++attempt) {
    CHECK(attempt < 320);
    reused = fork();
    CHECK(reused >= 0);
    if (!reused) {
      if (getpid() != child)
        _exit(0);
      break;
    }
    if (reused == child)
      break;
    wait_child(reused);
  }
  if (!reused) {
    struct yz_endpoint_unregister_cmd stale = {1, 0, old_generation};
    CHECK(ioctl(ctl, KSU_IOCTL_YZ_UNREGISTER_ENDPOINT, &stale) == -1 &&
          errno == ESTALE);
    publish(ctl, 1, listener(301));
    CHECK(write(ready[1], "R", 1) == 1);
    CHECK(read(resume[0], &byte, 1) == 1);
    _exit(0);
  }
  CHECK(reused == child);
  CHECK(read(ready[0], &byte, 1) == 1);
  CHECK(state->available && state->pid == (unsigned)reused);
  CHECK(state->generation > old_generation &&
        state->start_boottime != old_start);
  CHECK(write(resume[1], "C", 1) == 1);
  wait_child(reused);
  CHECK(!state->available);
  for (unsigned i = 0; i < 2; ++i) {
    close(ready[i]);
    close(resume[i]);
  }
}

int main(int argc, char **argv) {
  if (argc == 4 && !strcmp(argv[1], "exec-child")) {
    char byte;
    if (write(atoi(argv[2]), "E", 1) != 1 || read(atoi(argv[3]), &byte, 1) != 1)
      return 1;
    return 0;
  }
  setbuf(stdout, NULL);
  CHECK(!mount("proc", "/proc", "proc", 0, NULL));
  CHECK(!mount("sysfs", "/sys", "sysfs", 0, NULL));
  int module = open("/probe.ko", O_RDONLY);
  CHECK(module >= 0);
  CHECK(!syscall(SYS_finit_module, module, "", 0));
  close(module);
  int ctl = control();
  CHECK(ctl >= 0);
  int pages[2];
  struct yz_endpoint_state *states[2];
  for (unsigned i = 0; i < 2; ++i) {
    struct yz_endpoint_fd_cmd query = {YZ_ENDPOINT_VERSION, i + 1, -1, 0};
    CHECK(!ioctl(ctl, KSU_IOCTL_YZ_GET_ENDPOINT_STATE_FD, &query));
    pages[i] = query.fd;
    CHECK((fcntl(query.fd, F_GETFL) & O_ACCMODE) == O_RDONLY);
    states[i] = mmap(NULL, getpagesize(), PROT_READ, MAP_SHARED, query.fd, 0);
    CHECK(states[i] != MAP_FAILED);
    CHECK(states[i]->version == YZ_ENDPOINT_VERSION && states[i]->size == 80);
    CHECK(!states[i]->available && states[i]->abi == i + 1);
    CHECK(mprotect(states[i], getpagesize(), PROT_READ | PROT_WRITE) == -1);
    CHECK(mmap(NULL, getpagesize(), PROT_READ | PROT_WRITE, MAP_SHARED,
               query.fd, 0) == MAP_FAILED);
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", query.fd);
    int reopened = open(path, O_RDWR | O_CLOEXEC);
    if (reopened >= 0) {
      char byte = 0;
      CHECK(pwrite(reopened, &byte, 1, 0) == -1);
      CHECK(ftruncate(reopened, 0) == -1);
      CHECK(mmap(NULL, getpagesize(), PROT_WRITE, MAP_SHARED, reopened, 0) ==
            MAP_FAILED);
      close(reopened);
    }
    struct yz_endpoint_state *private = mmap(
        NULL, getpagesize(), PROT_READ | PROT_WRITE, MAP_PRIVATE, query.fd, 0);
    CHECK(private != MAP_FAILED);
    private->available = 9;
    CHECK(!states[i]->available);
    munmap(private, getpagesize());
  }
  struct yz_endpoint_register_cmd invalid = {.abi = 0, .listener_fd = pages[0]};
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &invalid) == -1 &&
        errno == EINVAL);
  invalid.abi = 1;
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &invalid) == -1 &&
        errno == ENOTSOCK);
  int unbound = socket(AF_UNIX, SOCK_STREAM, 0);
  CHECK(unbound >= 0);
  invalid.listener_fd = unbound;
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &invalid) == -1 &&
        errno == EINVAL);
  close(unbound);
  pid_t child = fork();
  CHECK(child >= 0);
  if (!child) {
    if (setresuid(2000, 2000, 2000))
      _exit(1);
    _exit(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &invalid) == -1 &&
                  errno == EPERM
              ? 0
              : 1);
  }
  wait_child(child);

  for (unsigned abi = 1; abi <= 2; ++abi) {
    int fd = listener(abi);
    struct yz_endpoint_register_cmd first = publish(ctl, abi, fd);
    CHECK(states[abi - 1]->available &&
          states[abi - 1]->pid == (unsigned)getpid());
    struct yz_endpoint_register_cmd next = publish(ctl, abi, fd);
    CHECK(next.generation > first.generation);
    struct yz_endpoint_unregister_cmd stale = {abi, 0, first.generation};
    CHECK(ioctl(ctl, KSU_IOCTL_YZ_UNREGISTER_ENDPOINT, &stale) == -1 &&
          errno == ESTALE);
    CHECK(states[abi - 1]->generation == next.generation &&
          states[abi - 1]->available);
    stale.generation = next.generation;
    CHECK(!ioctl(ctl, KSU_IOCTL_YZ_UNREGISTER_ENDPOINT, &stale));
    CHECK(!states[abi - 1]->available);
    close(fd);
  }
  void *command_page = mmap(NULL, getpagesize(), PROT_READ | PROT_WRITE,
                            MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  CHECK(command_page != MAP_FAILED);
  struct yz_endpoint_fd_cmd query = {YZ_ENDPOINT_VERSION, 1, -1, 0};
  memcpy(command_page, &query, sizeof(query));
  CHECK(!mprotect(command_page, getpagesize(), PROT_READ));
  unsigned before = fd_count();
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_GET_ENDPOINT_STATE_FD, command_page) == -1 &&
        errno == EFAULT);
  CHECK(fd_count() == before);
  int fault_listener = listener(90);
  struct yz_endpoint_register_cmd fault = {.abi = 1,
                                           .listener_fd = fault_listener};
  CHECK(!mprotect(command_page, getpagesize(), PROT_READ | PROT_WRITE));
  memcpy(command_page, &fault, sizeof(fault));
  CHECK(!mprotect(command_page, getpagesize(), PROT_READ));
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, command_page) == -1 &&
        errno == EFAULT);
  CHECK(!states[0]->available);
  munmap(command_page, getpagesize());
  close(fault_listener);
  for (unsigned mode = 0; mode < 3; ++mode) {
    int ready[2], resume[2];
    CHECK(!pipe(ready) && !pipe(resume));
    child = fork();
    CHECK(child >= 0);
    if (!child) {
      int fd = listener(100 + mode);
      publish(ctl, 1, fd);
      CHECK(write(ready[1], "R", 1) == 1);
      char byte;
      CHECK(read(resume[0], &byte, 1) == 1);
      if (mode == 1) {
        char ready_fd[16], resume_fd[16];
        snprintf(ready_fd, sizeof(ready_fd), "%d", ready[1]);
        snprintf(resume_fd, sizeof(resume_fd), "%d", resume[0]);
        execl("/init", "/init", "exec-child", ready_fd, resume_fd, NULL);
      }
      _exit(mode == 1 ? 1 : 0);
    }
    char byte;
    CHECK(read(ready[0], &byte, 1) == 1);
    CHECK(states[0]->available && states[0]->pid == (unsigned)child);
    int replacement = mode == 2 ? listener(200) : -1;
    if (replacement >= 0)
      publish(ctl, 1, replacement);
    if (mode == 0) {
      CHECK(!ioctl(ctl, _IO('E', 0)));
      CHECK(!states[0]->available);
    }
    CHECK(write(resume[1], "C", 1) == 1);
    if (mode == 1) {
      CHECK(read(ready[0], &byte, 1) == 1 && byte == 'E');
      CHECK(!states[0]->available);
      CHECK(write(resume[1], "D", 1) == 1);
    }
    wait_child(child);
    if (mode == 0)
      CHECK(!ioctl(ctl, _IO('E', 1)));
    if (replacement >= 0) {
      CHECK(states[0]->available && states[0]->pid == (unsigned)getpid());
      close(replacement);
    } else {
      CHECK(!states[0]->available);
    }
    for (unsigned i = 0; i < 2; ++i) {
      close(ready[i]);
      close(resume[i]);
    }
  }
  reuse_pid(ctl, states[0]);
  int disabled_listener = listener(400);
  publish(ctl, 2, disabled_listener);
  struct yz_endpoint_state original = *states[1];
  CHECK(!ioctl(ctl, _IO('E', 0)));
  CHECK(!states[0]->available && !states[1]->available);
  struct yz_endpoint_register_cmd disabled = {.abi = 2,
                                              .listener_fd = disabled_listener};
  CHECK(ioctl(ctl, KSU_IOCTL_YZ_REGISTER_ENDPOINT, &disabled) == -1 &&
        errno == ESHUTDOWN);
  child = fork();
  CHECK(child >= 0);
  if (!child) {
    CHECK(!ioctl(ctl, _IO('E', 1)));
    _exit(0);
  }
  wait_child(child);
  CHECK(states[1]->available && states[1]->pid == (unsigned)getpid());
  CHECK(states[1]->start_boottime == original.start_boottime &&
        states[1]->generation == original.generation &&
        !memcmp(states[1]->name, original.name, sizeof(original.name)));
  publish(ctl, 2, disabled_listener);
  close(disabled_listener);
  close(ctl);
  CHECK(!syscall(SYS_delete_module, "endpoint_probe", O_NONBLOCK));
  for (unsigned i = 0; i < 2; ++i) {
    CHECK(!states[i]->available);
    munmap(states[i], getpagesize());
    close(pages[i]);
  }
  finish(1,
         "readonly/reopen, auth, ABI, copyout, PID reuse, exit/exec, disable "
         "and unload",
         __LINE__);
}
