package com.fesht3.zafiro.settings

import com.fesht3.zafiro.settings.model.RuntimeBuiltinToolSetting
import com.fesht3.zafiro.settings.model.RuntimeCustomPyTool
import com.fesht3.zafiro.settings.model.RuntimeExecutionRule
import com.fesht3.zafiro.settings.model.RuntimeLlmConfig
import com.fesht3.zafiro.settings.model.RuntimeLoadedSkill
import com.fesht3.zafiro.settings.model.RuntimeMcpServer
import com.fesht3.zafiro.settings.model.RuntimeSkillMetadata
import com.fesht3.zafiro.settings.model.RuntimeToolValidation

interface RuntimeSettingsGateway {
    suspend fun readLlmConfig(agentId: String = "main"): RuntimeLlmConfig

    suspend fun listEnabledSkills(): List<RuntimeSkillMetadata> = emptyList()

    suspend fun loadSkill(id: String): RuntimeLoadedSkill? = null

    suspend fun listMcpServers(): List<RuntimeMcpServer>

    suspend fun addMemory(value: String)

    suspend fun removeMemory(oldText: String): MemoryMutationResult

    suspend fun replaceMemory(oldText: String, content: String): MemoryMutationResult

    suspend fun listCustomPyTools(): List<RuntimeCustomPyTool>

    suspend fun saveCustomPyTool(
        tool: RuntimeCustomPyTool,
        overwrite: Boolean = true,
    ): RuntimeToolValidation?

    suspend fun deleteCustomPyTool(name: String)

    suspend fun setCustomPyToolEnabled(name: String, enabled: Boolean)

    suspend fun listBuiltinToolSettings(): List<RuntimeBuiltinToolSetting>

    suspend fun setBuiltinToolEnabled(
        name: String,
        enabled: Boolean,
    ): RuntimeToolValidation?

    // 组定义在 app 层，网关仅透传 groupId；校验由实现完成。
    suspend fun setBuiltinToolGroupEnabled(
        groupId: String,
        enabled: Boolean,
    ): RuntimeToolValidation?

    suspend fun listExecutionRules(): List<RuntimeExecutionRule>
}
