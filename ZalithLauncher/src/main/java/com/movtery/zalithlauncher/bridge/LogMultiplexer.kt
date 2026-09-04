package com.movtery.zalithlauncher.bridge

object LogMultiplexer : LoggerBridge.EventLogListener {
    private val listeners = mutableListOf<LoggerBridge.EventLogListener>()

    fun addListener(listener: LoggerBridge.EventLogListener) {
        synchronized(listeners) {
            if (listeners.isEmpty()) {
                listeners.add(listener)
                LoggerBridge.setListener(this)
                return
            }
            if (listener !in listeners) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: LoggerBridge.EventLogListener) {
        synchronized(listeners) {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                LoggerBridge.setListener(null)
            }
        }
    }

    override fun onEventLogged(text: String) {
        synchronized(listeners) {
            listeners.forEach { it.onEventLogged(text) }
        }
    }
}
