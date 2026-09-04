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

package com.movtery.zalithlauncher.game.recorder

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.notification.NOTIFICATION_ID_RECORDING_SERVICE
import com.movtery.zalithlauncher.notification.NotificationChannelData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal foreground service required by Android 14+ (API 34) to hold a
 * [android.media.projection.MediaProjection] token for internal-audio capture.
 *
 * Android enforces that [android.media.projection.MediaProjectionManager.getMediaProjection]
 * may only be called while a foreground service whose [ServiceInfo.foregroundServiceType]
 * includes [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION] is actively running.
 *
 * Android 15+ (API 35+) tightened the enforcement further: calling [startForeground] with
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION] before the user has granted
 * MediaProjection consent in the current session causes [startForeground] to silently fail,
 * which triggers [android.app.RemoteServiceException.ForegroundServiceDidNotStartInTimeException]
 * after the 5-second deadline.
 *
 * Lifecycle (Android 15+ correct order):
 * 1. [GameScreen] launches the MediaProjection consent dialog (no service started yet).
 * 2. User grants consent — the activity-result callback fires.
 * 3. Caller calls [resetReadyState] then [android.content.Context.startForegroundService].
 * 4. [onStartCommand] calls [startForeground] — consent already granted, call succeeds.
 * 5. [isReady] flips to `true` — caller awaits this before calling
 *    [android.media.projection.MediaProjectionManager.getMediaProjection].
 * 6. Stopped via [android.content.Context.stopService] from [GameRecorder.cleanup] when
 *    the recording session ends (or is cancelled before it starts).
 */
class MediaProjectionForegroundService : Service() {
    companion object {
        private const val TAG = "ProjectionFGS"

        /**
         * Becomes `true` after [startForeground] has been called successfully in
         * [onStartCommand].  Callers must [resetReadyState] before starting the service
         * and then `collect { it }` (or `first { it }`) on this flow to know when
         * [android.media.projection.MediaProjectionManager.getMediaProjection] is safe
         * to call (Android 14+ requires the foreground service to be running first).
         */
        private val _isReady = MutableStateFlow(false)
        val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        /**
         * Reset to `false` before calling [android.content.Context.startForegroundService]
         * so that a stale `true` from a previous session is never observed.
         */
        fun resetReadyState() { _isReady.value = false }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannelData.RECORDING_SERVICE_CHANNEL
        val notification: Notification = NotificationCompat.Builder(this, channel.channelId)
            .setContentTitle(getString(R.string.recorder_notification_title))
            .setContentText(getString(R.string.recorder_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val canUseMediaProjection = checkSelfPermission(
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseMediaProjection) {
            startForeground(
                NOTIFICATION_ID_RECORDING_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w(TAG, "FOREGROUND_SERVICE_MEDIA_PROJECTION not granted, starting without type")
            startForeground(
                NOTIFICATION_ID_RECORDING_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            )
        } else {
            startForeground(NOTIFICATION_ID_RECORDING_SERVICE, notification)
        }

        // Signal that startForeground() has been called — the caller can now safely
        // invoke MediaProjectionManager.getMediaProjection().
        _isReady.value = true

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isReady.value = false
    }
}
