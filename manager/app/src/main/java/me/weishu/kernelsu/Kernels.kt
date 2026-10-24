package me.weishu.kernelsu

import android.system.Os

/**
 * @author weishu
 * @date 2022/12/10.
 */

data class KernelVersion(val major: Int, val patchLevel: Int, val subLevel: Int) {
    override fun toString(): String {
        return "$major.$patchLevel.$subLevel"
    }

    /**
     * Whether this kernel is one the manager supports at all: GKI 6.1 and newer, which is the
     * Android 14 generation onward. Everything older (5.10, 5.15, the 4.x trees) is out of scope -
     * the kernel module refuses to build for it, so asking the user to install anything there would
     * only lead to a module that cannot load.
     */
    fun isGKI(): Boolean {
        return major > 6 || (major == 6 && patchLevel >= 1)
    }
}

fun parseKernelVersion(version: String): KernelVersion {
    val find = "(\\d+)\\.(\\d+)\\.(\\d+)".toRegex().find(version)
    if (find != null) {
        val major = find.groupValues[1].toIntOrNull()
        val patchLevel = find.groupValues[2].toIntOrNull()
        val subLevel = find.groupValues[3].toIntOrNull()
        if (major != null && patchLevel != null && subLevel != null) {
            return KernelVersion(major, patchLevel, subLevel)
        }
    }
    return KernelVersion(-1, -1, -1)
}

fun getKernelVersion(): KernelVersion {
    Os.uname().release.let {
        return parseKernelVersion(it)
    }
}