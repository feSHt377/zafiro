package com.fesht3.zafiro.chat

sealed interface TurnMode {
    data object InjectedLLM : TurnMode
    data object NativeTakeover : TurnMode
}
