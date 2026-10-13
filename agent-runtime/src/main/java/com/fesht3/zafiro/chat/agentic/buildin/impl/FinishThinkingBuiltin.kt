package com.fesht3.zafiro.chat.agentic.buildin.impl

import com.fesht3.zafiro.chat.agentic.buildin.BuiltinTool
import com.fesht3.zafiro.chat.agentic.buildin.BuiltinToolRequest
import com.fesht3.zafiro.chat.agentic.buildin.BuiltinToolResult

class FinishThinkingBuiltin : BuiltinTool() {
    override val name: String = NAME
    override val defaultEnabled: Boolean = true
    override val description: String =
        "Finish the visible working phase of the current turn. Call this once, on its own, " +
            "after gathering information and completing tools, immediately before writing the final answer. " +
            "All preceding assistant text, reasoning, and tool activity in this turn will be grouped " +
            "under a collapsed Thinking content section. The original content remains available to expand. " +
            "After this tool succeeds, write the final answer as normal text. No arguments are required."
    override val inputSchemaJson: String =
        """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""

    override suspend fun invoke(request: BuiltinToolRequest): BuiltinToolResult =
        BuiltinToolResult.success(
            message = "Working content is now grouped. Continue with the final answer as normal text.",
        )

    companion object {
        const val NAME = "finish_thinking"
    }
}
