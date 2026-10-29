#!/usr/bin/env python3
"""Trim a busybox defconfig down to the applets module scripts actually call.

Run inside the busybox source tree, after `make defconfig`:

    python3 trim_busybox.py .config

Every `CONFIG_<APPLET>=y` that is not on the keep list is disabled. `FEATURE_*`
options are left alone: they describe how an applet behaves, and `make oldconfig`
drops the ones whose applet went away. The shell has to stay, and so do the
build switches that make the result a static binary Android can exec.
"""

import re
import sys

# Applets module scripts and ksud itself use. Name them by their CONFIG_ symbol,
# which is the applet name in upper case except for the shell and a few aliases.
KEEP = set("""
CAT CP MV RM LS LN MKDIR RMDIR TOUCH CHMOD CHOWN CHGRP STAT READLINK REALPATH
BASENAME DIRNAME MKTEMP FIND XARGS TEST ECHO PRINTF SLEEP SYNC TRUE FALSE YES
ENV ID WHOAMI UNAME DATE DF DU PS KILL KILLALL PIDOF PGREP PKILL
MOUNT UMOUNT LOSETUP BLKID DD MKNOD MKFIFO SWITCH_ROOT
GREP EGREP FGREP SED AWK CUT TR SORT UNIQ WC HEAD TAIL TEE DIFF CMP
MD5SUM SHA1SUM SHA256SUM SHA512SUM BASE64 BASE32 XXD OD HEXDUMP STRINGS
GZIP GUNZIP ZCAT BZIP2 BUNZIP2 BZCAT XZ UNXZ XZCAT LZMA UNLZMA UNZIP TAR CPIO
WGET NC
EXPR SEQ TIMEOUT WHICH GETOPT MOUNTPOINT
CROND CRONTAB LSMOD INSMOD MKDIR
""".split())

# Not applets: the shell selector, the build switches and the install layout.
ALWAYS = set("""
STATIC PIE LONG_OPTS DESKTOP FEATURE_BUFFERS_USE_MALLOC BUSYBOX_EXEC_PATH
PREFIX CROSS_COMPILER_PREFIX SYSROOT INSTALL_NO_USR
INSTALL_APPLET_SYMLINKS INSTALL_APPLET_HARDLINKS INSTALL_APPLET_SCRIPT_WRAPPERS
INSTALL_APPLET_DONT INSTALL_APPLET_SCRIPT_WRAPPER
SH_IS_ASH SH_IS_HUSH SH_IS_NONE ASH ASH_BASH_COMPAT ASH_JOB_CONTROL ASH_ALIAS
ASH_OPTIMIZE_FOR_SIZE ASH_INTERACTIVE ASH_EXPAND_PRMT ASH_IDLE_TIMEOUT
ASH_ECHO ASH_PRINTF ASH_TEST ASH_CMDCMD ASH_GETOPTS ASH_BUILTIN_UMASK
ASH_RANDOM_SUPPORT ASH_HELP
""".split())

# Forced values regardless of what defconfig picked.
FORCE = {"STATIC": "y", "PIE": "n", "DESKTOP": "n"}


def main(path: str) -> int:
    lines = open(path, encoding="utf-8").read().splitlines()
    out = []
    seen = set()
    dropped = []
    for line in lines:
        match = re.match(r"CONFIG_([A-Z0-9_]+)=(y|m)$", line)
        if not match:
            out.append(line)
            continue
        name = match.group(1)
        seen.add(name)
        if name in FORCE:
            out.append("CONFIG_%s=%s" % (name, FORCE[name]))
            continue
        if name.startswith("FEATURE_") or name in ALWAYS or name in KEEP:
            out.append(line)
            continue
        dropped.append(name)
        out.append("# CONFIG_%s is not set" % name)

    for name, value in FORCE.items():
        if name not in seen:
            out.append("CONFIG_%s=%s" % (name, value))

    open(path, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print("kept %d applet symbols, disabled %d" % (len(KEEP & seen), len(dropped)))
    if dropped:
        print("disabled: " + " ".join(sorted(dropped)[:40]) + (" ..." if len(dropped) > 40 else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else ".config"))
