package com.niki914.uikit.infra.component

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 胶囊高度：比 LiquidButton 的 48dp 矮一档，用于输入栏上方的轻量选择器。 */
val LiquidChipHeight: Dp = 32.dp

/**
 * 紧凑型玻璃胶囊：宽度随内容，承载一行短文本（如当前模型名）。
 *
 * 与 [LiquidButton] 同一套玻璃语言（vibrancy + blur + lens），仅高度与
 * 宽度约束不同——[LiquidButton] 是按钮语义的固定高度，这里是标签语义的
 * 选择器。[trailingIcon] 用于提示「可点开」。
 */
@Composable
fun LiquidChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    maxWidth: Dp = 220.dp,
) {
    val backdrop = rememberLayerBackdrop()
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.widthIn(max = maxWidth),
        height = LiquidChipHeight,
        tint = containerColor,
        surfaceColor = liquidButtonSurfaceColor(containerColor),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}
