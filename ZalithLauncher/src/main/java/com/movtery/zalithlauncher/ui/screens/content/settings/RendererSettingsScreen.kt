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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.movtery.zalithlauncher.ui.components.SimpleListItem
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.rememberSettingsCardShape
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.movtery.zalithlauncher.game.plugin.driver.Driver
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPluginManager
import com.movtery.zalithlauncher.game.plugin.renderer_v2.RendererV2Data
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.renderer.renderers.KopperZinkRenderer
import com.movtery.zalithlauncher.game.version.installed.GraphicsApi
import com.movtery.zalithlauncher.path.URL_CLOUD_DRIVE_DRIVER_PLUGINS
import com.movtery.zalithlauncher.path.URL_CLOUD_RENDERER_PLUGINS
import com.movtery.zalithlauncher.path.URL_GITHUB_DRIVER_PLUGINS
import com.movtery.zalithlauncher.utils.driver.TurnipDownloader
import com.movtery.zalithlauncher.path.URL_GITHUB_RENDERER_PLUGINS
import com.movtery.zalithlauncher.bridge.ZLBridge
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.utils.settings.MobileGluesConfig
import com.movtery.zalithlauncher.ui.components.AnimatedColumn
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.verticalScrollWithBar
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.IntSliderSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.ListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.TextInputSettingsCard
import com.movtery.zalithlauncher.ui.screens.navigateTo
import com.movtery.zalithlauncher.utils.device.checkVulkanSupport
import com.movtery.zalithlauncher.utils.fsr.FSRUtils
import com.movtery.zalithlauncher.utils.isAdrenoGPU
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.sendDLPlugin

@Composable
fun RendererSettingsScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?,
    eventViewModel: EventViewModel,
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.Renderer, settingsScreenKey, false)
    ) { isVisible ->
        val context = LocalContext.current
        var showMobileGluesSettings by remember { mutableStateOf(false) }
        var showBenchmark by remember { mutableStateOf(false) }
        var driverToDelete by remember { mutableStateOf<Driver?>(null) }

        // Tab state: 0 = Built-in Renderers, 1 = App Renderers (external plugins)
        var selectedRendererTab by remember { mutableIntStateOf(0) }

        // Split all compatible renderers into built-in vs external plugin categories
        val allCompatibleRenderers = Renderers.getCompatibleRenderers(context).second
        val externalIdentifiers = RendererPluginManager.getRendererList()
            .map { it.getUniqueIdentifier() }
            .toSet()
        val builtInRenderers = remember(allCompatibleRenderers, externalIdentifiers) {
            allCompatibleRenderers.filter { it.getUniqueIdentifier() !in externalIdentifiers }
        }
        val externalRenderers = remember(allCompatibleRenderers, externalIdentifiers) {
            allCompatibleRenderers.filter { it.getUniqueIdentifier() in externalIdentifiers }
        }

        if (showMobileGluesSettings) {
            MobileGluesSettingsDialog(onDismissRequest = { showMobileGluesSettings = false })
        }

        if (showBenchmark) {
            Dialog(
                onDismissRequest = { showBenchmark = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    // Allow the dialog window to draw behind the notch/status-bar so
                    // the dark scrim covers the full physical display evenly.
                    decorFitsSystemWindows = false
                )
            ) {
                RendererBenchmarkOverlay(
                    availableRenderers = Renderers.getCompatibleRenderers(context).second,
                    onDismiss = { showBenchmark = false }
                )
            }
        }


        driverToDelete?.let { driver ->
            SimpleAlertDialog(
                title = stringResource(R.string.generic_delete),
                text = stringResource(R.string.turnip_driver_delete_confirm, driver.name),
                confirmText = stringResource(R.string.generic_delete),
                onConfirm = {
                    java.io.File(driver.path).deleteRecursively()
                    DriverPluginManager.scanExternalDrivers(context)
                    if (AllSettings.vulkanDriver.getValue() == driver.id) {
                        AllSettings.vulkanDriver.save(AllSettings.vulkanDriver.defaultValue)
                    }
                    driverToDelete = null
                },
                onDismiss = { driverToDelete = null }
            )
        }

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
                    val selectedRendererId = AllSettings.renderer.state
                    val v2PluginEnvUnits = remember(selectedRendererId) {
                        Renderers.getRenderers()
                            .filterIsInstance<RendererV2Data>()
                            .find { it.getUniqueIdentifier() == selectedRendererId }
                            ?.env?.getConfigurableUnits()?.takeIf { it.isNotEmpty() }
                    }
                    var showV2ConfigDialog by remember { mutableStateOf(false) }

                    RunBenchmarkPill(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        onClick = { showBenchmark = true }
                    )

                    // Renderer selection card — fully self-contained, custom-built (does NOT
                    // reuse ListSettingsCard/topContent) so the collapse/expand + tab placement
                    // is 100% explicit and verifiable in one place:
                    // Header (always visible) -> [collapsed: nothing else]
                    // -> [expanded: tab row, then the renderer list for the selected tab].
                    GlobalRendererCard(
                        selectedTab = selectedRendererTab,
                        onTabSelected = { selectedRendererTab = it },
                        builtInRenderers = builtInRenderers,
                        externalRenderers = externalRenderers,
                        onRendererSelected = { renderer ->
                            AllSettings.renderer.save(renderer.getUniqueIdentifier())
                        },
                        onMobileGluesSettingsClick = { showMobileGluesSettings = true },
                        onV2ConfigClick = if (v2PluginEnvUnits != null) { { showV2ConfigDialog = true } } else null,
                        onDownloadClick = {
                            eventViewModel.sendDLPlugin(
                                githubLink = URL_GITHUB_RENDERER_PLUGINS,
                                cloudDrives = listOf(
                                    EventViewModel.Event.DownloadPlugins.CloudDrive(
                                        language = "zh",
                                        link = URL_CLOUD_RENDERER_PLUGINS
                                    )
                                )
                            )
                        }
                    )

                    //新一代渲染器插件的环境变量配置对话框
                    if (showV2ConfigDialog && v2PluginEnvUnits != null) {
                        RendererV2ConfigDialog(
                            units = v2PluginEnvUnits,
                            onDismissRequest = { showV2ConfigDialog = false }
                        )
                    }

                    ListSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.vulkanDriver,
                        items = DriverPluginManager.getDriverList(),
                        title = stringResource(R.string.settings_renderer_global_vulkan_driver_title),
                        getItemText = { it.name },
                        getItemId = { it.id },
                        getItemSummary = {
                            DriverSummaryLayout(it)
                        },
                        getItemTrailing = { driver ->
                            if (driver.isExternal) {
                                IconButton(onClick = { driverToDelete = driver }) {
                                    Icon(
                                        modifier = Modifier.padding(4.dp),
                                        painter = painterResource(R.drawable.ic_delete_filled),
                                        contentDescription = stringResource(R.string.generic_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    eventViewModel.sendDLPlugin(
                                        githubLink = URL_GITHUB_DRIVER_PLUGINS,
                                        cloudDrives = listOf(
                                            EventViewModel.Event.DownloadPlugins.CloudDrive(
                                                language = "zh",
                                                link = URL_CLOUD_DRIVE_DRIVER_PLUGINS
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
                    )

                    SettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        title = stringResource(R.string.settings_renderer_download_turnip),
                        summary = stringResource(R.string.settings_renderer_download_turnip_summary),
                        onClick = {
                            key.backStack.navigateTo(NormalNavKey.Settings.TurnipDrivers)
                        },
                        trailingIcon = {
                            Row {
                                IconButton(
                                    onClick = {
                                        eventViewModel.sendEvent(EventViewModel.Event.OpenWeb(TurnipDownloader.getRepoReleasesUrl()))
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_link),
                                        contentDescription = stringResource(R.string.generic_open_link)
                                    )
                                }
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp)
                                )
                            }
                        }
                    )

                    ListSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.graphicsApi,
                        items = GraphicsApi.entries,
                        title = stringResource(R.string.settings_game_graphics_api_title),
                        summary = stringResource(R.string.settings_game_graphics_api_summary),
                        getItemText = {
                            when (it) {
                                GraphicsApi.DEFAULT -> stringResource(R.string.settings_game_graphics_api_default)
                                GraphicsApi.DEFAULT_OPENGL -> stringResource(R.string.settings_game_graphics_api_default_opengl)
                                else -> it.displayName
                            }
                        }
                    )

                    IntSliderSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.resolutionRatio,
                        title = stringResource(R.string.settings_renderer_resolution_scale_title),
                        summary = stringResource(R.string.settings_renderer_resolution_scale_summary),
                        valueRange = AllSettings.resolutionRatio.floatRange,
                        suffix = "%",
                        fineTuningControl = true
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.gameFullScreen,
                        title = stringResource(R.string.settings_renderer_full_screen_title),
                        summary = stringResource(R.string.settings_renderer_full_screen_summary)
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
                        unit = AllSettings.sustainedPerformance,
                        title = stringResource(R.string.settings_renderer_sustained_performance_title),
                        summary = stringResource(R.string.settings_renderer_sustained_performance_summary)
                    )

                    var adrenoGPUAlert by remember { mutableStateOf(false) }

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.zinkPreferSystemDriver,
                        title = stringResource(R.string.settings_renderer_vulkan_driver_system_title),
                        summary = stringResource(R.string.settings_renderer_vulkan_driver_system_summary),
                        onCheckedChange = { checked ->
                            if (checked && isAdrenoGPU()) adrenoGPUAlert = true
                        }
                    )

                    if (adrenoGPUAlert) {
                        SimpleAlertDialog(
                            title = stringResource(R.string.generic_warning),
                            text = stringResource(R.string.settings_renderer_zink_driver_adreno),
                            onConfirm = {
                                AllSettings.zinkPreferSystemDriver.save(true)
                                adrenoGPUAlert = false
                            },
                            onDismiss = {
                                AllSettings.zinkPreferSystemDriver.save(false)
                                adrenoGPUAlert = false
                            }
                        )
                    }

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.vsyncInZink,
                        title = stringResource(R.string.settings_renderer_vsync_in_zink_title),
                        summary = stringResource(R.string.settings_renderer_vsync_in_zink_summary)
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.bigCoreAffinity,
                        title = stringResource(R.string.settings_renderer_force_big_core_title),
                        summary = stringResource(R.string.settings_renderer_force_big_core_summary)
                    )

                    val display = LocalContext.current.display

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        unit = AllSettings.fpsLimitEnabled,
                        title = stringResource(R.string.settings_renderer_fps_limit_title),
                        summary = stringResource(R.string.settings_renderer_fps_limit_summary),
                        onCheckedChange = { checked ->
                            AllSettings.fpsLimitEnabled.save(checked)
                            if (checked) {
                                val hz = display?.refreshRate?.roundToInt() ?: 60
                                AllSettings.fpsLimit.save(hz)
                                ZLBridge.fpsLimitSet(hz)
                            } else {
                                ZLBridge.fpsLimitSet(0)
                            }
                        }
                    )

                    if (AllSettings.fpsLimitEnabled.state) {
                        IntSliderSettingsCard(
                            modifier = Modifier.fillMaxWidth(),
                            position = CardPosition.Middle,
                            value = AllSettings.fpsLimit.state,
                            onValueChange = { AllSettings.fpsLimit.updateState(it) },
                            onValueChangeFinished = {
                                val fps = AllSettings.fpsLimit.state
                                AllSettings.fpsLimit.save(fps)
                                ZLBridge.fpsLimitSet(fps)
                            },
                            title = stringResource(R.string.settings_renderer_fps_limit_title),
                            valueRange = AllSettings.fpsLimit.floatRange,
                            suffix = " FPS",
                            fineTuningControl = false
                        )
                    }

                    val isKopperZinkSelected = AllSettings.renderer.state == KopperZinkRenderer.getUniqueIdentifier()
                    var surfaceViewAutoDisabledAlert by remember { mutableStateOf(false) }

                    LaunchedEffect(isKopperZinkSelected) {
                        if (isKopperZinkSelected && AllSettings.useSurfaceView.state) {
                            AllSettings.useSurfaceView.save(false)
                            if (!AllSettings.surfaceViewKopperWarningDontShow.state) {
                                surfaceViewAutoDisabledAlert = true
                            }
                        }
                    }

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Middle,
                        //Kopper Zink 选中时，无论保存的偏好值是什么，都在界面上显示为关闭+禁用状态
                        checked = AllSettings.useSurfaceView.state && !isKopperZinkSelected,
                        enabled = !isKopperZinkSelected,
                        onCheckedChange = { checked ->
                            AllSettings.useSurfaceView.save(checked)
                        },
                        title = stringResource(R.string.settings_renderer_surface_title),
                        summary = if (isKopperZinkSelected) {
                            stringResource(R.string.settings_renderer_surface_summary_kopper_disabled)
                        } else {
                            stringResource(R.string.settings_renderer_surface_summary)
                        }
                    )

                    if (surfaceViewAutoDisabledAlert) {
                        AlertDialog(
                            onDismissRequest = { surfaceViewAutoDisabledAlert = false },
                            title = { Text(stringResource(R.string.generic_warning)) },
                            text = { Text(stringResource(R.string.settings_renderer_surface_kopper_warning)) },
                            confirmButton = {
                                Button(onClick = {
                                    AllSettings.surfaceViewKopperWarningDontShow.save(true)
                                    surfaceViewAutoDisabledAlert = false
                                }) {
                                    Text(stringResource(R.string.settings_renderer_surface_kopper_warning_dont_show))
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { surfaceViewAutoDisabledAlert = false }) {
                                    Text(stringResource(R.string.generic_confirm))
                                }
                            }
                        )
                    }

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.dumpShaders,
                        title = stringResource(R.string.settings_renderer_shader_dump_title),
                        summary = stringResource(R.string.settings_renderer_shader_dump_summary)
                    )

                }
            }

        }
    }
}

/**
 * Pill-style tab row for switching between renderer categories.
 * Matches the design of the Zalith 2 / Legacy tab selector in Control Management.
 *
 * @param selectedTab 0 = Built-in Renderers, 1 = App Renderers
 * @param onTabSelected callback when the user picks a tab
 */
@Composable
private fun RendererTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Pair(stringResource(R.string.settings_renderer_tab_builtin), R.drawable.ic_build_outlined),
        Pair(stringResource(R.string.settings_renderer_tab_external), R.drawable.ic_extension_outlined)
    )
    BoxWithConstraints(
        modifier = modifier
            .height(52.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTab,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "renderer_tab_indicator"
        )
        // Animated selection indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        // Tab labels
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tabEntry ->
                val (label, iconRes) = tabEntry
                val isSelected = selectedTab == index
                val contentColor = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Self-contained "Global Renderer" card.
 *
 * This intentionally does NOT reuse the generic ListSettingsCard/topContent mechanism.
 * It owns its own `expanded` state and lays out content in one explicit, linear block so
 * there is no ambiguity about ordering or visibility:
 *
 *   Header row (title / summary / download / expand arrow)   <- ALWAYS visible
 *   AnimatedVisibility(visible = expanded) {
 *       RendererTabRow (Built-in / App Renderers pill tabs)  <- ONLY when expanded
 *       Renderer list for the selected tab (or empty state)  <- ONLY when expanded
 *   }
 *
 * Collapsing/expanding animates the tab row and the list together, as one unit, since
 * they live inside the same AnimatedVisibility block.
 */
@Composable
private fun RunBenchmarkPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val gradient = remember(colorScheme) {
        Brush.horizontalGradient(
            listOf(colorScheme.primary, colorScheme.tertiary)
        )
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 3.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .background(brush = gradient, shape = CircleShape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rocket_launch_filled),
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                modifier = Modifier.padding(start = 10.dp),
                text = stringResource(R.string.benchmark_run),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun GlobalRendererCard(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    builtInRenderers: List<RendererInterface>,
    externalRenderers: List<RendererInterface>,
    onRendererSelected: (RendererInterface) -> Unit,
    onMobileGluesSettingsClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Upstream: show a settings entry when the selected renderer is a v2 plugin with config
    onV2ConfigClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val currentRendererId = AllSettings.renderer.state

    val shape = rememberSettingsCardShape(position = CardPosition.Top)

    BackgroundCard(
        modifier = modifier,
        shape = shape
    ) {
        // Header — always visible, regardless of expanded state.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(all = 16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_renderer_global_renderer_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.settings_renderer_global_renderer_summary),
                    style = MaterialTheme.typography.labelSmall
                )
                val selectedName = (builtInRenderers + externalRenderers)
                    .firstOrNull { it.getUniqueIdentifier() == currentRendererId }
                    ?.getRendererName()
                if (selectedName != null) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.settings_element_selected, selectedName),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // v2 renderer plugin config button — only shown when applicable (upstream feature)
            if (onV2ConfigClick != null) {
                IconButton(onClick = onV2ConfigClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_filled),
                        contentDescription = stringResource(R.string.settings_renderer_config_title)
                    )
                }
            }
            IconButton(onClick = onDownloadClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_2_filled),
                    contentDescription = stringResource(R.string.generic_download)
                )
            }
            val rotation by animateFloatAsState(
                targetValue = if (expanded) -180f else 0f,
                animationSpec = getAnimateTween()
            )
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(34.dp)
                    .rotate(rotation),
                onClick = { expanded = !expanded }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down_rounded),
                    contentDescription = stringResource(if (expanded) R.string.generic_expand else R.string.generic_collapse)
                )
            }
        }

        // Expanded content — tab row THEN renderer list/empty-state, hidden entirely when collapsed.
        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visible = expanded,
            enter = expandVertically(animationSpec = getAnimateTween()),
            exit = shrinkVertically(animationSpec = getAnimateTween()) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                RendererTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )

                val rendererItems = if (selectedTab == 0) builtInRenderers else externalRenderers

                if (selectedTab == 1 && rendererItems.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_renderer_tab_external_empty_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                modifier = Modifier.alpha(0.7f),
                                text = stringResource(R.string.settings_renderer_tab_external_empty),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_2_filled),
                                contentDescription = stringResource(R.string.generic_download)
                            )
                        }
                    }
                } else {
                    rendererItems.forEach { renderer ->
                        SimpleListItem(
                            modifier = Modifier.fillMaxWidth(),
                            selected = renderer.getUniqueIdentifier() == currentRendererId,
                            itemName = renderer.getRendererName(),
                            summary = { RendererSummaryLayout(renderer) },
                            trailing = if (renderer.getRendererName() == "MobileGlues") {
                                {
                                    IconButton(onClick = onMobileGluesSettingsClick) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_settings_filled),
                                            contentDescription = stringResource(R.string.generic_setting)
                                        )
                                    }
                                }
                            } else null,
                            onClick = {
                                if (renderer.getUniqueIdentifier() != currentRendererId) {
                                    onRendererSelected(renderer)
                                    expanded = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RendererSummaryLayout(renderer: RendererInterface) {
    FlowRow(
        modifier = Modifier.alpha(0.7f),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        with(renderer) {
            getRendererSummary()?.let { summary ->
                Text(text = summary, style = MaterialTheme.typography.labelSmall)
            }

            val minVer = getMinMCVersion()
            val maxVer = getMaxMCVersion()

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
}

@Composable
fun DriverSummaryLayout(driver: Driver) {
    with(driver) {
        summary?.let { text ->
            Text(
                modifier = Modifier.alpha(0.7f),
                text = text, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
