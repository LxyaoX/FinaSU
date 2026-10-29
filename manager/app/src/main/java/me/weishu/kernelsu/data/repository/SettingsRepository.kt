package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.model.BootPropsStatus
import me.weishu.kernelsu.data.model.YukiZygiskStatus
import me.weishu.kernelsu.data.model.RekernelXStatus
import me.weishu.kernelsu.data.model.TeesimStatus
import me.weishu.kernelsu.data.model.ModuleSortGroup
import me.weishu.kernelsu.data.modulestore.StoreSourceKind

/**
 * Everything the settings screens probe about kernel features, gathered by
 * [SettingsRepository.loadFeatureSnapshot] in one batched root-shell round trip.
 * [statuses] uses the same tokens as the individual readers (forced/managed/
 * supported/unsupported, or "" when the probe failed); [values] holds the current
 * kernel values and [persists] the stored configuration values (null = not set).
 */
data class FeatureSnapshot(
    val statuses: Map<String, String>,
    val values: Map<String, Long?>,
    val persists: Map<String, Long?>,
    val folkMount: Result<FolkMountStatus>,
    val nomount: Result<NomountStatus>,
)

interface SettingsRepository {
    var checkUpdate: Boolean
    var checkModuleUpdate: Boolean
    var themeMode: Int
    var keyColor: Int
    var colorStyle: String
    var useSystemDynamicColor: Boolean
    var colorGenerationMode: String
    var colorContrast: String
    var statsTopLayout: String
    var listWorkingCardModeHidden: Boolean
    var hideHomeFingerprint: Boolean
    var hideHomeZygisk: Boolean
    var hideHomeSusfs: Boolean
    var hideHomeMount: Boolean
    var hideHomeKernelFullVersion: Boolean
    var enablePredictiveBack: Boolean
    var enableNavigationBadge: Boolean
    var pageScale: Float
    var moduleDescriptionMaxLines: Int
    var enableWebDebugging: Boolean
    var homeLayoutStyle: String

    var moduleSortGroups: Set<ModuleSortGroup>
    var moduleSortEnabledFirst: Boolean
    var moduleSortCustomOrder: List<String>
    var superuserShowSystemApps: Boolean
    var superuserShowOnlyPrimaryUserApps: Boolean
    var superuserSortOption: Int
    var allowAnyDynamicManager: Boolean
    var suLogFilters: Set<String>?
    var autoJailbreak: Boolean
    var useSoftReboot: Boolean
    var repoSourceKind: StoreSourceKind
    var repoCustomUrl: String
    var repoSelectedRepositoryUrl: String
    val intentToken: String

    // Backup targets and content. The WebDAV password is *not* here: it is a reverse-readable
    // secret and lives in the keystore-backed SecretStore.
    var backupLocalEnabled: Boolean
    var backupWebDavEnabled: Boolean
    var webDavUrl: String
    var webDavUsername: String
    var webDavPath: String
    var backupModulesEnabled: Boolean
    var backupAllowlistEnabled: Boolean
    var backupPartitions: Set<String>

    /** When enabled, a snapshot is taken automatically before a flash overwrites a target. */
    var backupBeforeFlash: Boolean

    suspend fun getSuCompatStatus(): String
    suspend fun getSuCompatPersistValue(): Long?
    fun isSuEnabled(): Boolean
    fun setSuEnabled(enabled: Boolean): Boolean
    fun setSuCompatModePref(mode: Int)
    fun getSuCompatModePref(): Int

    suspend fun getKernelUmountStatus(): String
    fun isKernelUmountEnabled(): Boolean
    fun setKernelUmountEnabled(enabled: Boolean): Boolean

    suspend fun getSelinuxHideStatus(): String
    fun isSelinuxHideEnabled(): Boolean
    fun setSelinuxHideEnabled(enabled: Boolean): Int

    suspend fun getMountViewFilterStatus(): String
    suspend fun isMountViewFilterEnabled(): Boolean
    suspend fun setMountViewFilterEnabled(enabled: Boolean): Result<Unit>

    suspend fun getSELinuxMode(): String
    suspend fun setSELinuxMode(enforcing: Boolean): Boolean

    suspend fun getSulogStatus(): String
    suspend fun getAvcSpoofStatus(): String
    fun isAvcSpoofEnabled(): Boolean
    fun setAvcSpoofEnabled(enabled: Boolean): Boolean
    suspend fun getSulogPersistValue(): Long?
    suspend fun getSulogValue(): Long?

    /** The built-in Zygisk engine, as `ksud yukizygisk status` reports it. */
    suspend fun getYukiZygiskStatus(): Result<YukiZygiskStatus>
    fun setSulogEnabled(enabled: Boolean): Boolean

    suspend fun getFolkMountStatus(): Result<FolkMountStatus>
    suspend fun setFolkMountMode(mode: FolkMountMode): Result<Unit>
    suspend fun getRekernelXStatus(): Result<RekernelXStatus>
    suspend fun setRekernelXEnabled(enabled: Boolean): Result<Unit>
    suspend fun getBootPropsStatus(): Result<BootPropsStatus>
    suspend fun setBootPropsEnabled(enabled: Boolean): Result<Unit>
    suspend fun getTeesimStatus(): Result<TeesimStatus>
    suspend fun installTeesim(): Result<Unit>
    suspend fun setTeesimEnabled(enabled: Boolean): Result<Unit>
    suspend fun uninstallTeesim(): Result<Unit>
    suspend fun importTeesimKeybox(base64: String): Result<Unit>
    suspend fun removeTeesimKeybox(): Result<Unit>
    suspend fun setTeesimTargetApps(packages: List<String>): Result<Unit>
    suspend fun getNomountStatus(): Result<NomountStatus>
    suspend fun setNomountIsolated(enabled: Boolean): Result<Unit>
    suspend fun injectNomountRules(): Result<Unit>
    suspend fun clearNomountRules(): Result<Unit>
    suspend fun clearNomountEverything(): Result<Unit>
    suspend fun setNomountUids(uids: List<Int>): Result<Unit>
    suspend fun hotLoadNomountModule(id: String): Result<Unit>
    suspend fun hotUnloadNomountModule(id: String): Result<Unit>
    suspend fun setModuleSkip(id: String, skip: Boolean): Result<Unit>

    suspend fun getAdbRootStatus(): String
    suspend fun getAdbRootPersistValue(): Long?
    suspend fun getAdbRootValue(): Long?
    fun setAdbRootEnabled(enabled: Boolean): Boolean
    suspend fun getShellSuAdbOnlyStatus(): String
    suspend fun getShellSuAdbOnlyPersistValue(): Long?
    suspend fun getShellSuAdbOnlyValue(): Long?
    fun setShellSuAdbOnlyEnabled(enabled: Boolean): Boolean

    fun isDefaultUmountModules(): Boolean
    fun setDefaultUmountModules(enabled: Boolean): Boolean

    fun isLkmMode(): Boolean

    /**
     * Reads every feature status/value, the persisted feature config and the mount, Zygisk
     * and NoMount statuses in a single batched shell round trip. A cold post-reboot
     * settings open pays one command burst instead of one exec round trip per probe.
     */
    suspend fun loadFeatureSnapshot(): FeatureSnapshot

    fun execKsudFeatureSave(): Boolean
}
