#!/system/bin/sh
# What the kernel can still be asked while nothing else makes progress.
#
# `sysrq w` is the important one: it prints every blocked task with its kernel
# stack, which is what names the code path that stopped the boot. Everything
# else is context for the same boot: what the kernel already complained about,
# where the log ends, and whether the vendor's persistent store kept a panic.

DMESG=/system/bin/dmesg
[ -x "$DMESG" ] || DMESG=dmesg

echo "--- sysrq: show blocked tasks ---"
echo 1 >/proc/sys/kernel/sysrq 2>/dev/null
echo w >/proc/sysrq-trigger 2>/dev/null
sleep 3

echo "--- dmesg highlights ---"
"$DMESG" 2>/dev/null | grep -E "INFO: task|blocked for more than|Call trace|BUG:|WARNING:|NoMount|nomount" | tail -n 200

echo "--- dmesg tail ---"
"$DMESG" 2>/dev/null | tail -n 1500

echo "--- tasks in D state ---"
for d in /proc/[0-9]*; do
    s=$(cat "$d/stat" 2>/dev/null) || continue
    case "$s" in
        *" D "*)
            echo "### ${d#/proc/}: $s"
            cat "$d/stack" 2>/dev/null
            cat "$d/syscall" 2>/dev/null
            echo
            ;;
    esac
done

echo "--- pstore ---"
ls -l /sys/fs/pstore 2>/dev/null
for f in /sys/fs/pstore/*; do
    echo "### $f"
    cat "$f" 2>/dev/null | tail -n 200
done
