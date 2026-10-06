package com.fesht3.zafiro.settings

interface RuntimeHostGateway {
    suspend fun postNotification(title: String, content: String, uri: String?): Boolean
}
