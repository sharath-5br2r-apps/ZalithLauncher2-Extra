/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.game.version.profile

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * A named snapshot of the parts of an installed instance that are currently
 * supported by version profiles.  The nullable/future fields intentionally
 * leave room for launcher settings without changing the file format later.
 */
@Keep
data class VersionProfile(
    val name: String,
    @SerializedName("mods")
    val modStates: Map<String, Boolean> = emptyMap(),
    @SerializedName("resourcePacks")
    val resourcePackStates: Map<String, Boolean> = emptyMap(),
    val resourcePackOrder: List<String> = emptyList(),
    @SerializedName("shaders")
    val shaderStates: Map<String, Boolean> = emptyMap(),
    val selectedShader: String? = null,
    val shaderEnabled: Boolean = false,
    val accountId: String? = null,
    // Snapshot of the version's Configuration screen preferences (Manage Versions → Configuration).
    // Stored as a JSON string so that every current and future VersionConfig field is
    // automatically captured and restored without requiring explicit enumeration here.
    val versionConfigSnapshot: String? = null,
    // Reserved for future profile categories. They are not populated yet.
    val javaRuntime: String? = null,
    val jvmArguments: String? = null,
    val renderer: String? = null,
    val resolution: String? = null,
    val launchArguments: String? = null,
    val controllerSettings: String? = null
)

@Keep
data class VersionProfileFile(
    val activeProfile: String = DEFAULT_VERSION_PROFILE_NAME,
    val profiles: List<VersionProfile> = emptyList()
)

/**
 * Reactive notification that the files for a version's active profile changed.
 * Management screens use this as their refresh trigger instead of recreating
 * their navigation destination.
 */
data class VersionProfileChange(
    val versionPath: String,
    val revision: Long
)

const val DEFAULT_VERSION_PROFILE_NAME = "Default"