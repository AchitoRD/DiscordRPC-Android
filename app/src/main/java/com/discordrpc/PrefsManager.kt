package com.discordrpc

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "discord_rpc_prefs"
    private const val KEY_TOKEN = "discord_token"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_INTERVAL = "detection_interval"
    private const val KEY_ENABLED_APPS = "enabled_apps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String {
        return getPrefs(context).getString(KEY_TOKEN, "") ?: ""
    }

    fun setServiceRunning(context: Context, running: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
    }

    fun isServiceRunning(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SERVICE_RUNNING, false)
    }

    fun setInterval(context: Context, intervalMs: Long) {
        getPrefs(context).edit().putLong(KEY_INTERVAL, intervalMs).apply()
    }

    fun getInterval(context: Context): Long {
        return getPrefs(context).getLong(KEY_INTERVAL, 10000L)
    }

    fun saveEnabledApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_ENABLED_APPS, apps).apply()
    }

    fun getEnabledApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_ENABLED_APPS, emptySet()) ?: emptySet()
    }
}
