package com.fesht3.zafiro.chat.agentic.buildin

import com.fesht3.zafiro.chat.agentic.buildin.impl.ExecutePythonBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.FindInstalledAppsBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.LaunchAppBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.LoadSkillBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.MemoryBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.NotifyBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.OpenUriBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.PyMetaToolsBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.ScreenOperationAccessibilityBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.ScreenOperationShellBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.ScreenshotBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.TerminalBuiltin
import com.fesht3.zafiro.chat.agentic.buildin.impl.ViewImageBuiltin

class BuiltinToolRegistry(
    private val tools: List<BuiltinTool>,
) {
    fun all(): List<BuiltinTool> = tools

    fun find(name: String): BuiltinTool? {
        return tools.firstOrNull { it.name == name }
    }

    companion object {
        fun default(): BuiltinToolRegistry = BuiltinToolRegistry(
            listOf(
                ExecutePythonBuiltin(),
                LaunchAppBuiltin(),
                PyMetaToolsBuiltin(),
                MemoryBuiltin(),
                NotifyBuiltin(),
                OpenUriBuiltin(),
                LoadSkillBuiltin(),
                TerminalBuiltin(),
                FindInstalledAppsBuiltin(),
                ScreenOperationAccessibilityBuiltin(),
                ScreenOperationShellBuiltin(),
                ScreenshotBuiltin(),
                ViewImageBuiltin(),
            )
        )
    }
}
