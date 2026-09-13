package com.discordrpc

object EventBus {
    var onEvent: ((Event) -> Unit)? = null

    fun post(event: Event) {
        onEvent?.invoke(event)
    }

    sealed class Event {
        data class Status(val message: String) : Event()
        data class Connected(val username: String) : Event()
        data class Disconnected(val reason: String) : Event()
        data class AppDetected(val name: String, val packageName: String) : Event()
        data class Error(val message: String) : Event()
        data class Log(val line: String) : Event()
    }
}
