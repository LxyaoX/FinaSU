package me.weishu.kernelsu.data

import androidx.compose.runtime.Immutable

/**
 * One CPU core's clock, in kilohertz, as reported by `/sys/devices/system/cpu/cpuN/cpufreq`.
 *
 * The pairs are what the stats board groups into clusters, so a core whose `cpufreq` node is
 * missing simply does not appear.
 */
@Immutable
data class CpuFrequency(
    val coreIndex: Int,
    val currentFreqKHz: Long,
    val maxFreqKHz: Long,
)
