package com.discordrpc

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AppDetectionService : Service() {

    companion object {
        private const val TAG = "AppDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val MAX連續_SAME_APP_MS = 1800000L // 30 min max showing same app
        private const val MIN_INTERVAL_MS = 5000L
        private const val MAX_INTERVAL_MS = 120000L
        private val EXCLUDED = setOf(
            "com.discordrpc",
            "com.discord",
            "com.discord.android",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.settings",
            "com.android.incallui",
            "com.android.dialer",
            "com.android.phone",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher"
        )
    }

    private var gateway: DiscordGateway? = null
    private var detectionThread: Thread? = null
    @Volatile private var running = false
    private var currentPackage = ""
    private var currentAppName = ""
    private var appStartTime = 0L
    private var lastSwitchTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var totalDetections = 0
    private var lastDetections = mutableListOf<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        EventBus.post(EventBus.Event.Log("Servicio creado"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { stopService(); return START_NOT_STICKY }
        }
        startForegroundNotification()
        startDetection()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "discord_rpc_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Discord RPC", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Deteccion activa"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val stopI = Intent(this, AppDetectionService::class.java).apply { action = "STOP" }
        val stopP = PendingIntent.getService(this, 0, stopI, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openI = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val openP = PendingIntent.getActivity(this, 0, openI, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DiscordRPC")
            .setContentText("Detectando apps...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(openP)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopP)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, n)
    }

    private fun startDetection() {
        val token = PrefsManager.getToken(this)
        if (token.isEmpty()) {
            EventBus.post(EventBus.Event.Error("Sin token - pon tu token de Discord"))
            stopSelf()
            return
        }

        acquireWakeLock()

        gateway = DiscordGateway(token)
        gateway?.connect()

        running = true
        val interval = PrefsManager.getInterval(this).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        EventBus.post(EventBus.Event.Log("Deteccion cada ${interval/1000}s"))

        detectionThread = Thread {
            Thread.sleep(1500) // wait for gateway
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    detectApp()
                    Thread.sleep(interval)
                } catch (e: InterruptedException) { break }
                catch (e: Exception) {
                    EventBus.post(EventBus.Event.Log("Error: ${e.message}"))
                    try { Thread.sleep(interval) } catch (e2: InterruptedException) { break }
                }
            }
        }.apply { isDaemon = true; name = "Detector"; start() }
    }

    private fun detectApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15000, now)
        if (stats.isNullOrEmpty()) {
            EventBus.post(EventBus.Event.Log("Sin datos UsageStats"))
            return
        }

        val top = stats.maxByOrNull { it.lastTimeUsed } ?: return
        val pkg = top.packageName
        if (pkg in EXCLUDED || pkg == currentPackage) return

        // Anti-spam: throttle rapid switches
        val timeSinceLastSwitch = now - lastSwitchTime
        if (timeSinceLastSwitch < 2000) return // min 2s between switches
        lastSwitchTime = now

        // Anti-flood: track detections per minute
        totalDetections++
        lastDetections.add(now)
        lastDetections = lastDetections.filter { now - it < 60000 }.toMutableList()
        if (lastDetections.size > 30) {
            EventBus.post(EventBus.Event.Log("Anti-flood: demasiados cambios, pausando 30s"))
            Thread.sleep(30000)
            return
        }

        val oldApp = currentAppName
        currentPackage = pkg
        currentAppName = getAppName(pkg)
        appStartTime = now

        EventBus.post(EventBus.Event.Log("[$totalDetections] $oldApp -> $currentAppName"))
        EventBus.post(EventBus.Event.AppDetected(currentAppName, pkg))

        if (gateway?.isConnected() == true) {
            gateway?.updateActivity(
                appName = currentAppName,
                details = "Playing on mobile",
                state = currentAppName,
                largeImageText = currentAppName,
                startTimestamp = appStartTime
            )
        } else {
            EventBus.post(EventBus.Event.Log("Gateway no conectado, app detectada localmente"))
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DiscordRPC:Detection").apply {
                acquire(60 * 60 * 1000L) // max 1 hour
            }
        } catch (e: Exception) {
            EventBus.post(EventBus.Event.Log("WakeLock error: ${e.message}"))
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun stopService() {
        running = false
        detectionThread?.interrupt()
        detectionThread = null

        // CLEANUP: limpiar Rich Presence al detener
        try {
            gateway?.clearActivity()
            Thread.sleep(200)
            gateway?.disconnect()
        } catch (e: Exception) {}
        gateway = null

        try { wakeLock?.release() } catch (e: Exception) {}
        wakeLock = null

        PrefsManager.setServiceRunning(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        EventBus.post(EventBus.Event.Log("Servicio detenido y Rich Presence limpiado"))
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }
}
