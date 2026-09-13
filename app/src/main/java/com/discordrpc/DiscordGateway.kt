package com.discordrpc

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DiscordGateway(private val token: String, private val applicationId: String) {
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var heartbeatInterval = 41250L
    private var heartbeatThread: Thread? = null
    private var lastSequenceNumber = -1
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
    }

    fun connect() {
        EventBus.post(EventBus.Event.Log("Conectando..."))
        val request = Request.Builder().url(GATEWAY_URL).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                EventBus.post(EventBus.Event.Log("WebSocket abierto"))
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                EventBus.post(EventBus.Event.Disconnected("Cerrado: $reason"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                EventBus.post(EventBus.Event.Disconnected(t.message ?: "Error"))
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optInt("op", -1)) {
                10 -> {
                    val d = json.optJSONObject("d")
                    heartbeatInterval = d?.optLong("heartbeat_interval", 41250) ?: 41250
                    startHeartbeat()
                    sendIdentify()
                }
                11 -> {}
                0 -> {
                    val seq = json.optInt("s", -1)
                    if (seq >= 0) lastSequenceNumber = seq
                    handleEvent(json.optString("t", ""), json.opt("d"))
                }
                7 -> disconnect()
                9 -> { EventBus.post(EventBus.Event.Log("Token invalido")); disconnect() }
                1 -> sendHeartbeat(json.opt("d"))
            }
        } catch (e: Exception) { EventBus.post(EventBus.Event.Log("Error: ${e.message}")) }
    }

    private fun sendIdentify() {
        val payload = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", token)
                put("intents", 0)
                put("properties", JSONObject().apply {
                    put("os", "linux"); put("browser", "DiscordRPC"); put("device", "DiscordRPC")
                })
            })
        }
        webSocket?.send(payload.toString())
        EventBus.post(EventBus.Event.Log("Identify enviado"))
    }

    private fun startHeartbeat() {
        heartbeatThread?.interrupt()
        heartbeatThread = Thread {
            try { Thread.sleep(heartbeatInterval) } catch (e: InterruptedException) { return@Thread }
            while (isConnected.get() && !Thread.currentThread().isInterrupted) {
                try { sendHeartbeat(lastSequenceNumber); Thread.sleep(heartbeatInterval) }
                catch (e: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "Heartbeat"; start() }
    }

    private fun sendHeartbeat(d: Any?) {
        val seq = when (d) {
            is Int -> d; is Number -> d.toInt()
            else -> if (lastSequenceNumber >= 0) lastSequenceNumber else JSONObject.NULL
        }
        webSocket?.send(JSONObject().apply { put("op", 1); put("d", seq) }.toString())
    }

    private fun handleEvent(name: String, data: Any?) {
        if (name == "READY" && data is JSONObject) {
            val user = data.optJSONObject("user")
            val username = user?.optString("username", "?") ?: "?"
            isConnected.set(true)
            EventBus.post(EventBus.Event.Connected(username))
        }
    }

    fun updateActivity(
        appName: String,
        details: String,
        state: String,
        largeImageText: String? = null,
        startTimestamp: Long? = null
    ) {
        if (!isConnected.get()) return

        // Usa "default" como asset key - el usuario sube UN icono como "default"
        val activity = JSONObject().apply {
            put("name", appName)
            put("type", 0)
            if (details.isNotEmpty()) put("details", details)
            if (state.isNotEmpty()) put("state", state)
            put("assets", JSONObject().apply {
                put("large_image", "default")
                put("large_text", appName)
            })
            if (startTimestamp != null) {
                put("timestamps", JSONObject().apply { put("start", startTimestamp) })
            }
        }

        val payload = JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("status", "online")
                val arr = org.json.JSONArray(); arr.put(activity)
                put("activities", arr)
                put("afk", false)
                put("since", JSONObject.NULL)
            })
        }
        webSocket?.send(payload.toString())
    }

    fun clearActivity() {
        if (!isConnected.get()) return
        webSocket?.send(JSONObject().apply {
            put("op", 3)
            put("d", JSONObject().apply {
                put("status", "online")
                put("activities", org.json.JSONArray())
                put("afk", false)
                put("since", JSONObject.NULL)
            })
        }.toString())
    }

    fun disconnect() {
        isConnected.set(false)
        heartbeatThread?.interrupt(); heartbeatThread = null
        try { webSocket?.close(1000, "Bye") } catch (e: Exception) {}
        webSocket = null
        EventBus.post(EventBus.Event.Disconnected("Desconectado"))
    }

    fun isConnected() = isConnected.get()
}
