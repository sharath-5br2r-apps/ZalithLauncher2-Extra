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

package com.movtery.zalithlauncher.components

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.SplashException
import com.movtery.zalithlauncher.components.jre.Jre
import com.movtery.zalithlauncher.components.jre.UnpackJnaTask
import com.movtery.zalithlauncher.components.jre.UnpackJreTask
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "UnpackManager"

/**
 * Application-scoped singleton that manages the first-launch dependency installer.
 *
 * Installation runs as a background task via [TaskSystem], which uses its own
 * coroutine scope that is not bound to any Activity lifecycle. This allows the
 * user to leave the installer screen freely while installation continues.
 *
 * The [items] list and their [InstallableItem.state] / [AbstractUnpackTask.taskMessage]
 * StateFlows survive Activity recreation during the same process lifetime, enabling
 * seamless progress reconnection when the installer screen is reopened.
 */
object UnpackManager {

    /** Stable task ID for the background installation job in [TaskSystem]. */
    const val INSTALL_TASK_ID = "unpack_launcher_deps"

    private val _items: MutableList<InstallableItem> = ArrayList()

    /** The list of installable dependency items. Stable reference across Activity recreations. */
    val items: List<InstallableItem> get() = _items

    private val _finishedCount = AtomicInteger(0)

    /** Whether a flag has been seen indicating items have been built for this process. */
    private var initialized = false

    /** Human-readable task title resolved from string resources during [initItems]. */
    private var installTaskTitle: String? = null

    /**
     * Returns true if the installation is currently running as a background task
     * in [TaskSystem].
     */
    val isInstalling: Boolean get() = TaskSystem.containsTask(INSTALL_TASK_ID)

    /**
     * Build the list of installable dependency items from the launcher's bundled assets.
     * Idempotent — subsequent calls are no-ops once initialized.
     *
     * Must be called before [checkAll] or [startAll].
     */
    fun initItems(context: Context) {
        if (initialized) return
        initialized = true
        installTaskTitle = context.getString(R.string.unpack_install_task_title)

        Components.entries.forEach { component ->
            val task = UnpackComponentsTask(context, component)
            if (!task.isCheckFailed()) {
                _items.add(
                    InstallableItem(
                        component.displayName,
                        context.getString(component.summary),
                        task
                    )
                )
            }
        }
        Jre.entries.forEach { jre ->
            val task = UnpackJreTask(context, jre)
            if (!task.isCheckFailed()) {
                _items.add(
                    InstallableItem(
                        jre.jreName,
                        context.getString(jre.summary),
                        task
                    )
                )
            }
        }
        val jnaTask = UnpackJnaTask(context)
        if (!jnaTask.isCheckFailed()) {
            _items.add(
                InstallableItem(
                    "JNA",
                    context.getString(R.string.unpack_screen_jna),
                    jnaTask
                )
            )
        }
        _items.sort()
    }

    /**
     * Check and update each item's installation state by inspecting the filesystem.
     *
     * Skipped entirely when a background installation is already running — in that
     * case the items' [InstallableItem.state] flows are being updated live by the
     * running task and must not be reset.
     */
    fun checkAll() {
        if (isInstalling) return

        _finishedCount.set(0)
        _items.forEach { item ->
            val state = item.task.checkState()
            item.updateState(state)
            if (state == InstallableItem.State.FINISHED) {
                _finishedCount.incrementAndGet()
            }
        }
    }

    /**
     * Submit all pending items as a background installation task via [TaskSystem].
     *
     * If a task with [INSTALL_TASK_ID] is already running this call is a no-op;
     * the existing task continues and the caller can reconnect via [TaskSystem.tasksFlow].
     *
     * Installation continues even if the caller's Activity is destroyed.
     */
    fun startAll() {
        if (isInstalling) return

        val installTask = Task.runTask(
            id = INSTALL_TASK_ID,
            title = installTaskTitle,
            runningIcon = R.drawable.ic_download_2_filled,
            dispatcher = Dispatchers.IO,
            task = { _ ->
                // coroutineScope creates a child scope: all item launches run in
                // parallel, failures cancel siblings and propagate to TaskSystem.
                coroutineScope {
                    _items
                        .filter {
                            it.state.value == InstallableItem.State.NOT_STARTED ||
                            it.state.value == InstallableItem.State.PENDING
                        }
                        .forEach { item ->
                            launch(Dispatchers.IO) {
                                item.updateState(InstallableItem.State.RUNNING)
                                runCatching {
                                    item.task.run()
                                }.onFailure { cause ->
                                    throw SplashException(cause)
                                }
                                _finishedCount.incrementAndGet()
                                item.updateState(InstallableItem.State.FINISHED)
                            }
                        }
                }
            },
            onError = { th ->
                Logger.error(TAG, "Background dependency installation failed", th)
            },
            onFinally = {
                // Mirror the original post-install step: set default Java runtime.
                AllSettings.javaRuntime.apply {
                    if (getValue().isEmpty()) save(Jre.JRE_8.jreName)
                }
            }
        )

        TaskSystem.submitTask(installTask)
    }

    /**
     * Returns true when every item has reached [InstallableItem.State.FINISHED].
     */
    fun areAllFinished(): Boolean = _finishedCount.get() >= _items.size
}
