/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.game.plugin.natives.NativePlugin
import com.movtery.zalithlauncher.game.plugin.natives.NativePluginManager
import com.movtery.zalithlauncher.path.URL_CLOUD_NATIVE_LIB_PLUGINS
import com.movtery.zalithlauncher.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.computeDynamicRAMAllocation
import com.movtery.zalithlauncher.setting.computeMaximumRAMAllocation
import com.movtery.zalithlauncher.setting.findBestRAMAllocation
import com.movtery.zalithlauncher.ui.components.CheckChip
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.setting.unit.min
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.AnimatedColumn
import com.movtery.zalithlauncher.ui.components.SimpleTextSlider
import com.movtery.zalithlauncher.ui.components.SliderValueEditDialog
import com.movtery.zalithlauncher.ui.components.verticalScrollWithBar
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.MemoryPreview
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.IntSliderSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.ListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.StringListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.TextInputSettingsCard
import com.movtery.zalithlauncher.utils.platform.getMaxMemoryForSettings
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.sendDLPlugin

@Composable
fun GameSettingsScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?,
    eventViewModel: EventViewModel
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.Game, settingsScreenKey, false)
    ) { isVisible ->
        AnimatedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollWithBar(state = rememberScrollState())
                .padding(all = 12.dp),
            isVisible = isVisible
        ) { scope ->
            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top,
                        unit = AllSettings.versionIsolation,
                        title = stringResource(R.string.settings_game_version_isolation_title),
                        summary = stringResource(R.string.settings_game_version_isolation_summary)
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.skipGameIntegrityCheck,
                        title = stringResource(R.string.settings_game_skip_game_integrity_check_title),
                        summary = stringResource(R.string.settings_game_skip_game_integrity_check_summary)
                    )

                    TextInputSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.versionCustomInfo,
                        title = stringResource(R.string.settings_game_version_custom_info_title),
                        summary = stringResource(R.string.settings_game_version_custom_info_summary)
                    )
                }
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    val runtimes = remember { RuntimesManager.getRuntimes() }

                    if (runtimes.isNotEmpty()) {
                        ListSettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Top,
                            unit = AllSettings.javaRuntime,
                            items = RuntimesManager.getRuntimes().filter { it.isCompatible() },
                            title = stringResource(R.string.settings_game_java_runtime_title),
                            summary = stringResource(R.string.settings_game_java_runtime_summary),
                            getItemText = { it.name },
                            getItemId = { it.name }
                        )
                    }

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = if (runtimes.isNotEmpty()) CardPosition.Middle else CardPosition.Top,
                        unit = AllSettings.autoPickJavaRuntime,
                        title = stringResource(R.string.settings_game_auto_pick_java_runtime_title),
                        summary = stringResource(R.string.settings_game_auto_pick_java_runtime_summary)
                    )

                    val nativePlugins = remember {
                        NativePluginManager.getPlugins()
                    }

                    @Composable
                    fun DLNativeLibsButton() {
                        IconButton(
                            onClick = {
                                eventViewModel.sendDLPlugin(
                                    githubLink = URL_GITHUB_NATIVE_LIB_PLUGINS,
                                    cloudDrives = listOf(
                                        EventViewModel.Event.DownloadPlugins.CloudDrive(
                                            language = "zh",
                                            link = URL_CLOUD_NATIVE_LIB_PLUGINS
                                        )
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_2_filled),
                                contentDescription = stringResource(R.string.generic_download)
                            )
                        }
                    }
                    if (nativePlugins.isNotEmpty()) {
                        StringListSettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Middle,
                            unit = AllSettings.disableNativeLibPlugins,
                            items = nativePlugins,
                            onItemsChange = { value, item ->
                                if (value) {
                                    this - item.packageName
                                } else {
                                    this + item.packageName
                                }
                            },
                            title = stringResource(R.string.settings_game_native_lib_plugin_title),
                            summary = stringResource(R.string.settings_game_native_lib_plugin_summary),
                            getItemID = { it.packageName },
                            getItemText = { it.displayName },
                            getItemSummary = { plugin ->
                                NativePluginSummaryLayout(plugin)
                            },
                            getItemCheck = { contains -> !contains },
                            trailingIcon = {
                                DLNativeLibsButton()
                            },
                        )
                    } else {
                        SettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Middle,
                            title = stringResource(R.string.settings_game_native_lib_plugin_title),
                            summary = stringResource(R.string.settings_game_native_lib_plugin_summary),
                            onClick = {},
                            trailingIcon = {
                                DLNativeLibsButton()
                            }
                        )
                    }

                    val context = LocalContext.current
                    val autoRamValueRange = AllSettings.ramAllocation.floatRange.start..getMaxMemoryForSettings(context).toFloat()

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.autoRamAllocation,
                        title = stringResource(R.string.settings_game_java_memory_title),
                        summary = stringResource(R.string.settings_game_auto_ram_allocation_summary),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Seed the slider immediately with the value appropriate
                                // for whichever mode was last selected.
                                val mode = AllSettings.autoRamAllocationMode.getValue()
                                AllSettings.ramAllocation.save(
                                    when (mode) {
                                        "static"  -> findBestRAMAllocation(context)
                                        "maximum" -> computeMaximumRAMAllocation(context)
                                        else      -> computeDynamicRAMAllocation(context)
                                    }
                                )
                            }
                        },
                        columnLayout = {
                            val autoRamEnabled = AllSettings.autoRamAllocation.state
                            val allocMode = AllSettings.autoRamAllocationMode.state
                            val ramValue = AllSettings.ramAllocation.state ?: AllSettings.ramAllocation.min
                            var showValueEditDialog by remember { mutableStateOf(false) }

                            // Periodically recompute and update the allocation while
                            // automatic mode is active.  Static mode is set once and never
                            // polled; dynamic and maximum refresh every 5 seconds.
                            // Restarts whenever the mode switches so the first tick uses
                            // the correct algorithm immediately.
                            LaunchedEffect(autoRamEnabled, allocMode) {
                                if (!autoRamEnabled || allocMode == "static") return@LaunchedEffect
                                while (true) {
                                    delay(5_000L)
                                    val newValue = when (allocMode) {
                                        "maximum" -> computeMaximumRAMAllocation(context)
                                        else      -> computeDynamicRAMAllocation(context)
                                    }
                                    val current = AllSettings.ramAllocation.state
                                        ?: AllSettings.ramAllocation.min
                                    if (kotlin.math.abs(newValue - current) >= 128) {
                                        AllSettings.ramAllocation.save(newValue)
                                    }
                                }
                            }

                            // Mode selector — slides down when automatic allocation is active
                            AnimatedVisibility(
                                visible = autoRamEnabled,
                                enter = expandVertically(animationSpec = tween(300)),
                                exit = shrinkVertically(animationSpec = tween(300))
                            ) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CheckChip(
                                        selected = allocMode == "static",
                                        label = { Text(stringResource(R.string.settings_game_auto_ram_mode_static)) },
                                        onClick = {
                                            AllSettings.autoRamAllocationMode.save("static")
                                            AllSettings.ramAllocation.save(findBestRAMAllocation(context))
                                        }
                                    )
                                    CheckChip(
                                        selected = allocMode == "dynamic",
                                        label = { Text(stringResource(R.string.settings_game_auto_ram_mode_dynamic)) },
                                        onClick = {
                                            AllSettings.autoRamAllocationMode.save("dynamic")
                                            AllSettings.ramAllocation.save(computeDynamicRAMAllocation(context))
                                        }
                                    )
                                    CheckChip(
                                        selected = allocMode == "maximum",
                                        label = { Text(stringResource(R.string.settings_game_auto_ram_mode_maximum)) },
                                        onClick = {
                                            AllSettings.autoRamAllocationMode.save("maximum")
                                            AllSettings.ramAllocation.save(computeMaximumRAMAllocation(context))
                                        }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleTextSlider(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = ramValue.toFloat(),
                                    shorter = true,
                                    enabled = !autoRamEnabled,
                                    onValueChange = { AllSettings.ramAllocation.updateState(it.toInt()) },
                                    onValueChangeFinished = { AllSettings.ramAllocation.save(AllSettings.ramAllocation.state) },
                                    onTextClick = { if (!autoRamEnabled) showValueEditDialog = true },
                                    toInt = true,
                                    valueRange = autoRamValueRange,
                                    suffix = "MB",
                                    fineTuningControl = true,
                                    fineTuningStep = 1f
                                )

                                MemoryPreview(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 2.dp),
                                    preview = ramValue.toDouble(),
                                    usedText = { usedMemory, totalMemory ->
                                        stringResource(R.string.settings_game_java_memory_used_text, usedMemory.toInt(), totalMemory.toInt())
                                    },
                                    previewText = { preview ->
                                        stringResource(R.string.settings_game_java_memory_allocation_text, preview.toInt())
                                    }
                                )
                            }

                            if (showValueEditDialog) {
                                SliderValueEditDialog(
                                    onDismissRequest = { showValueEditDialog = false },
                                    title = stringResource(R.string.settings_game_java_memory_title),
                                    valueRange = autoRamValueRange,
                                    value = ramValue.toFloat(),
                                    onValueChange = { AllSettings.ramAllocation.updateState(it.toInt()) },
                                    onValueChangeFinished = { AllSettings.ramAllocation.save(AllSettings.ramAllocation.state) },
                                    intCheck = true
                                )
                            }
                        }
                    )

                    TextInputSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.jvmArgs,
                        title = stringResource(R.string.settings_game_jvm_args_title),
                        summary = stringResource(R.string.settings_game_jvm_args_summary)
                    )
                }
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top,
                        unit = AllSettings.showLogAutomatic,
                        title = stringResource(R.string.settings_game_show_log_automatic_title),
                        summary = stringResource(R.string.settings_game_show_log_automatic_summary)
                    )

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.logTextSize,
                        title = stringResource(R.string.settings_game_log_text_size_title),
                        summary = stringResource(R.string.settings_game_log_text_size_summary),
                        valueRange = AllSettings.logTextSize.floatRange,
                        suffix = "Sp",
                        fineTuningControl = true
                    )

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.logBufferFlushInterval,
                        title = stringResource(R.string.settings_game_log_buffer_flush_interval_title),
                        summary = stringResource(R.string.settings_game_log_buffer_flush_interval_summary),
                        valueRange = AllSettings.logBufferFlushInterval.floatRange,
                        suffix = "ms",
                        fineTuningControl = true
                    )
                }
            }
        }
    }
}


@Composable
private fun NativePluginSummaryLayout(plugin: NativePlugin) {
    FlowRow(
        modifier = Modifier.alpha(0.7f),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_renderer_from_plugins,plugin.appName),
            style = MaterialTheme.typography.labelSmall
        )

        val minVer = plugin.minMCVer
        val maxVer = plugin.maxMCVer

        if (minVer != null || maxVer != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = stringResource(R.string.renderer_version_support), style = MaterialTheme.typography.labelSmall)

                minVer?.let {
                    Text(text = ">= $it", style = MaterialTheme.typography.labelSmall)
                }

                maxVer?.let {
                    Text(text = "<= $it", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}