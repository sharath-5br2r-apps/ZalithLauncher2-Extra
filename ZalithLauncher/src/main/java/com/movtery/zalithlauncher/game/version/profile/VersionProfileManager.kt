/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option 3) any later version.
 */

package com.movtery.zalithlauncher.game.version.profile

import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionConfig
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.version.mod.isEnabled
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.io.FileWriter
import java.lang.reflect.Type
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VersionProfileManager"
private const val PROFILE_FILE_NAME = "version.profiles"
private const val DISABLED_SUFFIX = ".disabled"
private const val SHADER_OPTION = "shaderPack"
private const val RESOURCE_PACK_OPTION = "resourcePacks"

/**
 * Stores profiles inside the installed version's launcher-owned `.zalith`
 * directory. All file operations are synchronized because profile switching
 * can be requested from both the dashboard and the version list.
 */
object VersionProfileManager {
    private val cache = ConcurrentHashMap<String, VersionProfileFile>()
    private val profileFileType: Type = object : TypeToken<VersionProfileFile>() {}.type
    private var profileChangeRevision = 0L
    private var isApplyingProfile = false
    private val _profileChanges = MutableStateFlow<VersionProfileChange?>(null)

    /** Emits after a profile selection or activation has applied new files. */
    val profileChanges = _profileChanges.asStateFlow()

    init {
        // Auto-capture the active profile whenever the selected account changes,
        // so that account switches are persisted into the current profile without
        // any manual "save" step from the user.
        AccountsManager.addOnAccountChangedListener { _ ->
            if (!isApplyingProfile) {
                VersionsManager.currentVersion.value?.let { captureCurrentState(it) }
            }
        }

        // Auto-capture the active profile whenever the user changes a Configuration
        // preference (Manage Versions → Configuration). This hooks into VersionConfig's
        // save listener so that every setting change is immediately persisted into the
        // active profile without requiring any manual save action from the user.
        VersionConfig.addOnSaveListener { savedPath ->
            if (!isApplyingProfile) {
                VersionsManager.currentVersion.value?.let { version ->
                    if (version.getVersionPath().absolutePath == savedPath.absolutePath) {
                        captureCurrentState(version)
                    }
                }
            }
        }
    }

    fun listProfiles(version: Version): List<VersionProfile> =
        read(version).profiles

    fun activeProfile(version: Version): VersionProfile =
        read(version).let { file ->
            file.profiles.firstOrNull { it.name == file.activeProfile }
                ?: file.profiles.first()
        }

    fun activeProfileName(version: Version): String = activeProfile(version).name

    /**
     * Captures the current files and account into the active profile.
     * This is called before changing profiles and before changing instances.
     */
    @Synchronized
    fun captureCurrentState(version: Version) {
        val file = read(version)
        val active = file.profiles.firstOrNull { it.name == file.activeProfile }
            ?: return
        val updated = active.copyStateFrom(capture(version))
        write(version, file.copy(profiles = file.profiles.replaceProfile(updated)))
    }

    @Synchronized
    fun selectProfile(version: Version, name: String): Boolean {
        var file = read(version)
        var target = file.profiles.firstOrNull { it.name == name } ?: return false
        val current = VersionsManager.currentVersion.value
        if (current != null && current.getVersionName() == version.getVersionName()) {
            captureCurrentState(version)
            file = read(version)
            // Capturing may replace the active profile in the cache. Always
            // apply the freshly-read target so selecting a profile cannot
            // restore stale state from before the capture.
            target = file.profiles.firstOrNull { it.name == name } ?: return false
            apply(target, version)
        }
        write(version, file.copy(activeProfile = target.name))
        notifyProfileChanged(version)
        return true
    }

    @Synchronized
    fun createProfile(version: Version, requestedName: String): VersionProfile? {
        var file = read(version)
        if (VersionsManager.currentVersion.value?.getVersionName() == version.getVersionName()) {
            captureCurrentState(version)
            file = read(version)
        }
        val name = uniqueName(file.profiles, requestedName)
        val profile = capture(version).copy(name = name)
        write(version, file.copy(activeProfile = name, profiles = file.profiles + profile))
        notifyProfileChanged(version)
        return profile
    }

    @Synchronized
    fun duplicateProfile(version: Version, sourceName: String): VersionProfile? {
        var file = read(version)
        val source = file.profiles.firstOrNull { it.name == sourceName } ?: return null
        val current = VersionsManager.currentVersion.value
        if (current?.getVersionName() == version.getVersionName()) {
            captureCurrentState(version)
            file = read(version)
        }
        val refreshedSource = file.profiles.firstOrNull { it.name == sourceName } ?: return null
        val profile = refreshedSource.copy(name = uniqueName(file.profiles, "${refreshedSource.name} Copy"))
        if (current?.getVersionName() == version.getVersionName()) {
            apply(profile, version)
        }
        write(version, file.copy(activeProfile = profile.name, profiles = file.profiles + profile))
        notifyProfileChanged(version)
        return profile
    }

    @Synchronized
    fun renameProfile(version: Version, oldName: String, requestedName: String): VersionProfile? {
        val file = read(version)
        val old = file.profiles.firstOrNull { it.name == oldName } ?: return null
        val newName = uniqueName(file.profiles.filterNot { it.name == oldName }, requestedName)
        val renamed = old.copy(name = newName)
        write(
            version,
            file.copy(
                activeProfile = if (file.activeProfile == oldName) newName else file.activeProfile,
                profiles = file.profiles.map { if (it.name == oldName) renamed else it }
            )
        )
        notifyProfileChanged(version)
        return renamed
    }

    @Synchronized
    fun deleteProfile(version: Version, name: String): Boolean {
        var file = read(version)
        if (file.profiles.size <= 1) return false
        if (VersionsManager.currentVersion.value?.getVersionName() == version.getVersionName()) {
            captureCurrentState(version)
            file = read(version)
        }
        val remaining = file.profiles.filterNot { it.name == name }
        if (remaining.size == file.profiles.size) return false
        val nextActive = if (file.activeProfile == name) remaining.first().name else file.activeProfile
        write(version, file.copy(activeProfile = nextActive, profiles = remaining))
        if (file.activeProfile == name && VersionsManager.currentVersion.value?.getVersionName() == version.getVersionName()) {
            apply(remaining.first(), version)
        }
        notifyProfileChanged(version)
        return true
    }

    /**
     * Called by VersionsManager after the current instance changes.
     */
    @Synchronized
    fun activate(version: Version?) {
        version ?: return
        ensure(version)
        apply(activeProfile(version), version)
        notifyProfileChanged(version)
    }

    /**
     * Re-applies the active profile immediately before a game launch.
     *
     * Launches can be initiated from shortcuts and other entry points that do
     * not necessarily pass through the normal version-selection UI. Keeping
     * this boundary here guarantees that the files, options, and account used
     * by the launch pipeline come from the active profile.
     */
    @Synchronized
    fun synchronizeForLaunch(version: Version) {
        ensure(version)
        apply(activeProfile(version), version)
        notifyProfileChanged(version)
    }

    /**
     * Registers a single newly-installed file as enabled in the active Version
     * Profile and ensures the file is physically in the enabled state on disk.
     *
     * Unlike [captureCurrentState] (which re-snapshots the entire directory and
     * can record a file as disabled if [apply] ran between the copy and the
     * capture), this method atomically updates only the one key that matters:
     * the newly installed file is recorded as enabled regardless of whether a
     * concurrent [apply] managed to rename it to `.disabled` in the meantime.
     *
     * Only the active profile is updated. Every other profile is left untouched,
     * so profile isolation is preserved: switching to another profile that did
     * not previously contain the file will still disable it.
     *
     * After updating the profile and re-enabling the file on disk,
     * [notifyProfileChanged] is emitted so that management screens refresh
     * immediately.
     *
     * @param version The game version the file was installed into.
     * @param file    The installed file (its parent directory determines whether
     *                it is a mod, resource pack, or shader).
     */
    @Synchronized
    fun registerInstalledContent(version: Version, file: File) {
        val folder = file.parentFile ?: return
        val key = file.profileKey()

        val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
        val resourcePacksDir = VersionFolders.RESOURCE_PACK.getDir(version.getGameDir())
        val shadersDir = VersionFolders.SHADERS.getDir(version.getGameDir())

        val profileFile = read(version)
        val active = profileFile.profiles.firstOrNull { it.name == profileFile.activeProfile }
            ?: return

        val updatedProfile = when (folder.absolutePath) {
            modsDir.absolutePath ->
                active.copy(modStates = active.modStates + (key to true))
            resourcePacksDir.absolutePath ->
                active.copy(resourcePackStates = active.resourcePackStates + (key to true))
            shadersDir.absolutePath ->
                active.copy(shaderStates = active.shaderStates + (key to true))
            else -> return // not a profile-managed folder; nothing to do
        }

        // Re-enable the file in case a concurrent apply() renamed it to .disabled
        // between the file copy and this call. The @Synchronized lock prevents any
        // subsequent apply() from disabling it again before we finish writing.
        setGroupEnabled(folder, key, true)

        write(version, profileFile.copy(profiles = profileFile.profiles.replaceProfile(updatedProfile)))
        notifyProfileChanged(version)
    }

    /**
     * Applies a user-requested enabled/disabled state to files and immediately
     * persists the resulting filesystem state into the active profile.
     *
     * The management screens use this instead of calling File.renameTo()
     * directly. Keeping the operation synchronized prevents two quick taps or
     * a profile switch from interleaving moves and capturing a half-applied
     * state.
     */
    @Synchronized
    fun setFilesEnabled(version: Version, files: Collection<File>, enabled: Boolean): Boolean {
        val requests = files
            .map { it.parentFile to it.profileKey() }
            .distinctBy { (directory, key) -> "${directory?.absolutePath}\u0000$key" }

        if (requests.isEmpty()) return false

        var changed = false
        requests.forEach { (directory, key) ->
            if (directory != null) {
                changed = setGroupEnabled(directory, key, enabled) || changed
            }
        }

        if (changed) {
            captureCurrentState(version)
            notifyProfileChanged(version)
        }
        return changed
    }

    private fun notifyProfileChanged(version: Version) {
        profileChangeRevision++
        _profileChanges.value = VersionProfileChange(
            versionPath = version.getVersionPath().absolutePath,
            revision = profileChangeRevision
        )
    }

    private fun capture(version: Version): VersionProfile {
        return VersionProfile(
            name = "",
            modStates = snapshotStates(VersionFolders.MOD.getDir(version.getGameDir())),
            resourcePackStates = snapshotStates(VersionFolders.RESOURCE_PACK.getDir(version.getGameDir())),
            resourcePackOrder = optionList(version, RESOURCE_PACK_OPTION),
            shaderStates = snapshotStates(VersionFolders.SHADERS.getDir(version.getGameDir())),
            selectedShader = optionValue(version, SHADER_OPTION)?.takeUnless { it.isEmpty() },
            shaderEnabled = optionValue(version, SHADER_OPTION)?.isNotEmpty() == true,
            accountId = AccountsManager.currentAccountFlow.value?.uniqueUUID,
            // Capture the full Configuration screen preferences as a JSON snapshot.
            // Using GSON serialization of the live VersionConfig ensures that every
            // current and future configuration field is captured automatically.
            versionConfigSnapshot = captureVersionConfig(version)
        )
    }

    /**
     * Serializes the version's current [VersionConfig] to a JSON string so that
     * all Manage Versions → Configuration preferences are captured in the profile.
     * Returns null if serialization fails, leaving the snapshot unpopulated rather
     * than crashing profile operations.
     */
    private fun captureVersionConfig(version: Version): String? =
        runCatching { GSON.toJson(version.getVersionConfig()) }
            .onFailure { Logger.warning(TAG, "Failed to capture version config snapshot", it) }
            .getOrNull()

    private fun VersionProfile.copyStateFrom(state: VersionProfile): VersionProfile =
        copy(
            modStates = state.modStates,
            resourcePackStates = state.resourcePackStates,
            resourcePackOrder = state.resourcePackOrder,
            shaderStates = state.shaderStates,
            selectedShader = state.selectedShader,
            shaderEnabled = state.shaderEnabled,
            accountId = state.accountId,
            // Propagate the Configuration snapshot when copying profile state
            versionConfigSnapshot = state.versionConfigSnapshot
        )

    private fun apply(profile: VersionProfile, version: Version) {
        isApplyingProfile = true
        try {
            applyStates(VersionFolders.MOD.getDir(version.getGameDir()), profile.modStates)
            applyStates(VersionFolders.RESOURCE_PACK.getDir(version.getGameDir()), profile.resourcePackStates)
            applyStates(VersionFolders.SHADERS.getDir(version.getGameDir()), profile.shaderStates)
            writeOptionList(version, RESOURCE_PACK_OPTION, profile.resourcePackOrder)
            writeOptionValue(version, SHADER_OPTION, if (profile.shaderEnabled) profile.selectedShader.orEmpty() else "")
            profile.accountId?.let { id ->
                AccountsManager.accountsFlow.value.firstOrNull { it.uniqueUUID == id }?.let {
                    AccountsManager.setCurrentAccount(it)
                }
            }
            // Restore the Configuration screen preferences from the profile snapshot.
            // Runs inside isApplyingProfile = true so the VersionConfig save listener
            // does not re-capture immediately after we write the restored values.
            applyVersionConfig(profile.versionConfigSnapshot, version)
        } finally {
            isApplyingProfile = false
        }
    }

    /**
     * Restores a [VersionConfig] snapshot into the live config object for [version].
     *
     * The snapshot is a JSON string produced by [captureVersionConfig]. Every field
     * present in the JSON is applied to the existing config object (which is the same
     * reference held by [Version.getVersionConfig]), so callers automatically observe
     * the updated values without any additional indirection.
     *
     * [VersionConfig.pinned] is intentionally preserved from the current config
     * because it is a version-level UI ordering preference, not a per-profile
     * Configuration setting. All other fields from the snapshot are applied.
     *
     * The config is saved to disk after restoration so it persists across restarts.
     * Because this runs inside [isApplyingProfile] = true, the save listener in
     * [VersionProfileManager] will not trigger a re-capture.
     */
    private fun applyVersionConfig(snapshot: String?, version: Version) {
        snapshot ?: return
        try {
            val restored = GSON.fromJson(snapshot, VersionConfig::class.java) ?: return
            val config = version.getVersionConfig()

            // Apply every Configuration screen preference from the snapshot.
            // VersionConfig.pinned has private set and represents version-level UI ordering,
            // not a Configuration preference — it is intentionally not restored here.
            config.isolationType = restored.isolationType
            config.skipGameIntegrityCheck = restored.skipGameIntegrityCheck
            config.javaRuntime = restored.javaRuntime
            config.jvmArgs = restored.jvmArgs
            config.renderer = restored.renderer
            config.driver = restored.driver
            config.graphicsApi = restored.graphicsApi
            config.control = restored.control
            config.customPath = restored.customPath
            config.customInfo = restored.customInfo
            config.versionSummary = restored.versionSummary
            config.serverIp = restored.serverIp
            config.ramAllocation = restored.ramAllocation
            config.touchVibrateDuration = restored.touchVibrateDuration
            config.touchVibrateKind = restored.touchVibrateKind

            // Persist the restored configuration to disk.
            // Because this runs inside isApplyingProfile = true, the save listener
            // will not trigger a re-capture loop.
            config.save()
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to restore version config snapshot", e)
        }
    }

    private fun snapshotStates(directory: File): Map<String, Boolean> =
        directory.listFiles()?.filter { it.exists() }?.groupBy { it.profileKey() }
            ?.mapValues { (key, files) ->
                // A previous interrupted move can leave both forms on disk.
                // Prefer the canonical filename; otherwise prefer an enabled
                // entry so the snapshot is deterministic and usable.
                files.firstOrNull { it.name == key }?.isEnabled()
                    ?: files.any { it.isEnabled() }
            }
            ?: emptyMap()

    private fun applyStates(directory: File, states: Map<String, Boolean>) {
        directory.listFiles()?.filter { it.exists() }?.groupBy { it.profileKey() }
            ?.forEach { (key, _) ->
                // Files not in this profile's snapshot default to disabled
                // (newly installed content).
                setGroupEnabled(directory, key, states[key] ?: false)
            }
    }

    /**
     * Ensures exactly one canonical file remains for a logical content key.
     * If both `name` and `name.disabled` exist, the requested target wins and
     * the duplicate is removed when possible. This avoids iteration-order
     * dependent results from File.listFiles().
     */
    private fun setGroupEnabled(directory: File, key: String, enabled: Boolean): Boolean {
        val candidates = directory.listFiles()
            ?.filter { it.exists() && it.profileKey() == key }
            ?: return false
        if (candidates.isEmpty()) return false

        val target = File(directory, if (enabled) key else "$key$DISABLED_SUFFIX")
        var changed = false
        var successful = true

        if (!target.exists()) {
            val source = candidates.firstOrNull { it.isEnabled() == enabled }
                ?: candidates.first()
            if (source.absolutePath != target.absolutePath) {
                successful = moveFile(source, target)
                changed = successful
            }
        }

        if (target.exists() && target.isEnabled() == enabled) {
            candidates
                .filter { it.absolutePath != target.absolutePath && it.exists() }
                .forEach { duplicate ->
                    val deleted = runCatching {
                        Files.deleteIfExists(duplicate.toPath())
                    }.onFailure {
                        Logger.warning(
                            TAG,
                            "Failed to remove duplicate profile file: $duplicate",
                            it
                        )
                    }.getOrDefault(false)
                    successful = deleted && successful
                    changed = deleted || changed
                }
        } else {
            successful = false
        }

        if (!successful) {
            Logger.warning(
                TAG,
                "Profile state move did not produce the requested state: " +
                    "$directory/$key (enabled=$enabled)"
            )
        }
        return changed
    }

    private fun File.profileKey(): String =
        name.removeSuffixIgnoreCase(DISABLED_SUFFIX)

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    private fun moveFile(source: File, target: File): Boolean {
        if (source == target) return true
        return runCatching {
            target.parentFile?.mkdirs()
            Files.move(
                source.toPath(),
                target.toPath()
            )
            true
        }.onFailure {
            Logger.warning(TAG, "Failed to apply profile file state: $source -> $target", it)
        }.getOrDefault(false)
    }

    private fun optionValue(version: Version, key: String): String? =
        optionsFile(version).takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')

    private fun optionList(version: Version, key: String): List<String> {
        val raw = optionValue(version, key) ?: return emptyList()
        return raw.removeSurrounding("[", "]")
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim().removeSurrounding("\"") }
            ?: emptyList()
    }

    private fun writeOptionValue(version: Version, key: String, value: String) {
        updateOption(version, key, value)
    }

    private fun writeOptionList(version: Version, key: String, values: List<String>) {
        updateOption(version, key, values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })
    }

    private fun updateOption(version: Version, key: String, value: String) {
        val file = optionsFile(version)
        if (!file.exists()) return
        val lines = file.readLines().toMutableList()
        val index = lines.indexOfFirst { it.startsWith("$key:") }
        if (index >= 0) lines[index] = "$key:$value" else lines.add("$key:$value")
        file.writeText(lines.joinToString("\n"))
    }

    private fun optionsFile(version: Version): File = File(version.getGameDir(), "options.txt")

    private fun read(version: Version): VersionProfileFile {
        val key = version.getVersionPath().absolutePath
        cache[key]?.let { return it }
        val file = File(VersionsManager.getZalithVersionPath(version), PROFILE_FILE_NAME)
        val loaded = runCatching {
            if (file.exists()) GSON.fromJson<VersionProfileFile>(file.readText(), profileFileType)
            else null
        }.onFailure { Logger.warning(TAG, "Failed to load version profiles.", it) }.getOrNull()
        val result = loaded?.takeIf { it.profiles.isNotEmpty() } ?: run {
            val default = VersionProfile(
                name = DEFAULT_VERSION_PROFILE_NAME,
                modStates = snapshotStates(VersionFolders.MOD.getDir(version.getGameDir())),
                resourcePackStates = snapshotStates(VersionFolders.RESOURCE_PACK.getDir(version.getGameDir())),
                resourcePackOrder = optionList(version, RESOURCE_PACK_OPTION),
                shaderStates = snapshotStates(VersionFolders.SHADERS.getDir(version.getGameDir())),
                selectedShader = optionValue(version, SHADER_OPTION)?.takeUnless { it.isEmpty() },
                shaderEnabled = optionValue(version, SHADER_OPTION)?.isNotEmpty() == true,
                accountId = AccountsManager.currentAccountFlow.value?.uniqueUUID
            )
            VersionProfileFile(DEFAULT_VERSION_PROFILE_NAME, listOf(default))
        }
        cache[key] = result
        if (!file.exists()) write(version, result)
        return result
    }

    private fun ensure(version: Version) = read(version)

    private fun write(version: Version, value: VersionProfileFile) {
        val key = version.getVersionPath().absolutePath
        cache[key] = value
        val directory = VersionsManager.getZalithVersionPath(version)
        if (!directory.exists()) directory.mkdirs()
        runCatching {
            FileWriter(File(directory, PROFILE_FILE_NAME), false).use { it.write(GSON.toJson(value)) }
        }.onFailure { Logger.error(TAG, "Failed to save version profiles.", it) }
    }

    private fun uniqueName(existing: List<VersionProfile>, requested: String): String {
        val base = requested.trim().ifEmpty { "Profile" }
        if (existing.none { it.name.equals(base, ignoreCase = true) }) return base
        var index = 2
        while (existing.any { it.name.equals("$base $index", ignoreCase = true) }) index++
        return "$base $index"
    }

    private fun List<VersionProfile>.replaceProfile(updated: VersionProfile): List<VersionProfile> =
        map { if (it.name == updated.name) updated else it }
}