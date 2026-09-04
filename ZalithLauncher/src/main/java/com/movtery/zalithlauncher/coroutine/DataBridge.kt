package com.movtery.zalithlauncher.coroutine

import kotlinx.coroutines.channels.Channel

class DataBridge<T> {
    private val channel = Channel<T>(Channel.CONFLATED)

    suspend fun awaitData(): T {
        return channel.receive()
    }

    fun provideData(value: T) {
        channel.trySend(value)
    }
}
