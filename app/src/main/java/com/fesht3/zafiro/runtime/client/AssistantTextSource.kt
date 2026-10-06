package com.fesht3.zafiro.runtime.client

import com.fesht3.zafiro.runtime.ipc.RenderFrame
import kotlinx.coroutines.flow.Flow

interface AssistantTextSource {
    fun submit(query: String): Flow<RenderFrame>
    suspend fun cancel()
    suspend fun resetConversation()
}
