package com.discordrpc

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AppDetectionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private val EXCLUDED = setOf(
            "com.discordrpc", "com.discord", "com.discord.android",
            "com.android.systemui", "com.android.launcher", "com.android.launcher3",
            "com.android.settings", "com.android.incallui", "com.android.dialer",
            "com.android.phone", "com.miui.home", "com.sec.android.app.launcher",
            "com.huawei.android.launcher", "com.android.vending"
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
    private var lastDetections = mutableListOf<Long>()
    private var staleCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); EventBus.post(EventBus.Event.Log("Servicio creado")) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopService(); return START_NOT_STICKY }
        startForegroundNotification()
        startDetection()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "discord_rpc_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Discord RPC", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Deteccion"; setShowBadge(false) }
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
        val appId = PrefsManager.getAppId(this)
        if (token.isEmpty()) { EventBus.post(EventBus.Event.Error("Sin token")); stopSelf(); return }
        if (appId.isEmpty()) { EventBus.post(EventBus.Event.Error("Sin Application ID")); stopSelf(); return }

        acquireWakeLock()
        gateway = DiscordGateway(token, appId)
        gateway?.connect()

        running = true
        val interval = PrefsManager.getInterval(this)
        EventBus.post(EventBus.Event.Log("Deteccion cada ${interval/1000}s"))

        detectionThread = Thread {
            Thread.sleep(400)
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
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
        if (stats.isNullOrEmpty()) return

        val top = stats.maxByOrNull { it.lastTimeUsed } ?: return
        val pkg = top.packageName

        // If top app is excluded AND we have a current tracked app, check staleness
        if (pkg in EXCLUDED) {
            if (currentPackage.isNotEmpty()) {
                staleCount++
                // Clear after 3 consecutive stale checks (user went home/locked phone)
                if (staleCount >= 3) {
                    EventBus.post(EventBus.Event.Log("App cerrada - limpiando"))
                    EventBus.post(EventBus.Event.AppDetected("", ""))
                    currentPackage = ""
                    currentAppName = ""
                    staleCount = 0
                    if (gateway?.isConnected() == true) {
                        gateway?.clearActivity()
                    }
                }
            }
            return
        }

        staleCount = 0

        // Same app, skip
        if (pkg == currentPackage) return

        // Anti-flood
        if (now - lastSwitchTime < 800) return
        lastSwitchTime = now

        lastDetections.add(now)
        lastDetections = lastDetections.filter { now - it < 60000 }.toMutableList()
        if (lastDetections.size > 40) {
            EventBus.post(EventBus.Event.Log("Rate limit - esperando"))
            Thread.sleep(8000); return
        }

        currentPackage = pkg
        currentAppName = getAppName(pkg)
        appStartTime = now

        EventBus.post(EventBus.Event.Log("$currentAppName"))
        EventBus.post(EventBus.Event.AppDetected(currentAppName, pkg))

        if (gateway?.isConnected() == true) {
            gateway?.updateActivity(
                appName = currentAppName,
                packageName = pkg,
                details = "Playing on mobile",
                state = currentAppName,
                largeImageText = currentAppName,
                startTimestamp = appStartTime
            )
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DiscordRPC:Detect").apply { acquire(60 * 60 * 1000L) }
        } catch (_: Exception) { }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) { packageName.substringAfterLast('.') }
    }

    private fun stopService() {
        running = false
        detectionThread?.interrupt(); detectionThread = null
        try {
            gateway?.clearActivity()
            Thread.sleep(150)
            gateway?.disconnect()
        } catch (_: Exception) {}
        gateway = null
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        PrefsManager.setServiceRunning(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopService(); super.onDestroy() }
}
