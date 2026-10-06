package com.fesht3.zafiro.settings

sealed class MemoryMutationResult {
    data object Ok : MemoryMutationResult()
    data object NotFound : MemoryMutationResult()
    data object Ambiguous : MemoryMutationResult()
}
