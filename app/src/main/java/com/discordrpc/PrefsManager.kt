package com.discordrpc

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "discord_rpc_prefs"
    private const val KEY_TOKEN = "discord_token"
    private const val KEY_APP_ID = "discord_app_id"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_INTERVAL = "detection_interval"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String {
        return getPrefs(context).getString(KEY_TOKEN, "") ?: ""
    }

    fun saveAppId(context: Context, appId: String) {
        getPrefs(context).edit().putString(KEY_APP_ID, appId).apply()
    }

    fun getAppId(context: Context): String {
        return getPrefs(context).getString(KEY_APP_ID, "") ?: ""
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
        return getPrefs(context).getLong(KEY_INTERVAL, 3000L)
    }
}
