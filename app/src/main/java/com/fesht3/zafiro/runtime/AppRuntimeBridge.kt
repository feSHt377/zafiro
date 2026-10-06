package com.fesht3.zafiro.runtime

import com.fesht3.zafiro.repo.XRepoRuntimeGateway
import com.fesht3.zafiro.settings.RuntimeBridge

fun createAppRuntimeBridge(): RuntimeBridge {
    return RuntimeBridge(
        settings = XRepoRuntimeGateway(),
        host = IpcRuntimeHostGateway(),
    )
}
