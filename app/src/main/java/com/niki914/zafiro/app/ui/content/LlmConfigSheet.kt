package com.niki914.zafiro.app.ui.content

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niki914.uikit.infra.component.OptionRow
import com.niki914.uikit.infra.component.OptionSheet
import com.niki914.zafiro.app.R
import com.niki914.zafiro.app.ui.model.SavedConfigSummary

/**
 * 对话内切换模型的选择单：列出全部 LLM 接入配置，点一行即切换生效配置。
 *
 * 配置不属于会话，切换是**全局**的——影响所有对话，下一个回合生效
 * （运行时每回合开头重读生效配置）。故这里不做「本会话」的暗示文案。
 */
@Composable
fun LlmConfigSheet(
    visible: Boolean,
    configs: List<SavedConfigSummary>,
    activeConfigId: String?,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!visible) return
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    OptionSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.ui_home_model_sheet_title),
        // 配置通常只有几条：下限低一些，避免少量条目时单子被拉得过高
        bodyModifier = Modifier.heightIn(min = screenH * 0.3f, max = screenH * 0.6f),
    ) { dismissThen ->
        if (configs.isEmpty()) {
            Text(
                text = stringResource(R.string.ui_home_model_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn {
                items(configs, key = { it.id }) { config ->
                    OptionRow(
                        title = config.name,
                        // 名称与模型同名时不重复显示（新建配置的默认名就是品牌名，通常不同）
                        subtitle = config.modelId.takeIf(String::isNotBlank)
                            ?.takeIf { it != config.name },
                        checked = config.id == activeConfigId,
                        onClick = { dismissThen { onSelect(config.id) } },
                    )
                }
            }
        }
    }
}
