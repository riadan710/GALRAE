package com.galrae.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.galrae.app.R

/**
 * 다른 앱이 우리 창을 차지해 백그라운드로 가도 프로세스가 얼지 않게 하는 포그라운드 서비스.
 *
 * 하는 일은 "앱을 살려두기"뿐이다. SDK 연결과 상태는 GalraeApp(MyCar 등)이 들고 있고,
 * 프로세스가 재시작되면 Application.onCreate가 다시 돌아 함께 복구된다.
 *
 * 유형은 location이라 위치 권한이 허용된 뒤에만 시작할 수 있다 (없으면 startForeground가 SecurityException).
 * 앱을 쓰는 동안에만 켜 두고, 앱을 끝내면 끈다.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        runCatching { startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }
            .onSuccess { Log.i(TAG, "started") }
            .onFailure {
                Log.e(TAG, "start failed", it)
                stopSelf()
            }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GALRAE/KeepAlive"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
