package com.niki914.uikit.infra.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 行内次级点击目标：命中区（窗口坐标）+ 回调。
 *
 * 用「命中区 + 手工分发」而不是给子元素挂 clickable 是有意的：整行按压变色要统一，
 * 子级 clickable 会让按下态割裂出局部 ripple（见下方 pointerInput 内的说明）。
 */
data class SettingsItemTrailingAction(
    val boundsInWindow: Rect,
    val onClick: () -> Unit,
)

@Composable
fun SettingsItemSurface(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 64.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.ContextClick,
    highlightPulseKey: Any? = null,
    highlightPulseDurationMillis: Int = 500,
    shape: Shape = RectangleShape,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** 次级点击目标（如尾随「复制」「编辑」），按顺序命中的第一个优先于 onClick。 */
    trailingActions: List<SettingsItemTrailingAction> = emptyList(),
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentTrailingActions by rememberUpdatedState(trailingActions)
    val isInteractive = enabled && (
        currentOnClick != null ||
            currentTrailingActions.isNotEmpty() ||
            currentOnLongClick != null
    )

    val restingColor = Color.Transparent
    val pressedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    var backgroundColor by remember { mutableStateOf(restingColor) }
    var surfaceOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isInteractive || (enabled && highlightPulseKey != null)) {
            backgroundColor
        } else {
            restingColor
        },
        animationSpec = tween(durationMillis = 500),
        label = "settingsItemSurfaceBackground",
    )

    LaunchedEffect(highlightPulseKey) {
        if (highlightPulseKey != null) {
            backgroundColor = pressedColor
            delay(highlightPulseDurationMillis.coerceAtLeast(0).toLong())
            backgroundColor = restingColor
        }
    }

    val interactiveModifier = if (isInteractive) {
        Modifier
            .onGloballyPositioned { surfaceOriginInWindow = it.positionInWindow() }
            .pointerInput(currentOnClick, currentOnLongClick, currentTrailingActions, hapticFeedbackType) {
                detectTapGestures(
                    onPress = {
                        backgroundColor = pressedColor
                        try {
                            tryAwaitRelease()
                        } finally {
                            backgroundColor = restingColor
                        }
                    },
                    onTap = { offset ->
                        hapticFeedbackType?.let(haptics::performHapticFeedback)
                        // 单一 pointerInput 内做命中分发，按压整行变色，不出现局部 ripple 割裂
                        val pointInWindow = offset + surfaceOriginInWindow
                        val hit = currentTrailingActions.firstOrNull {
                            it.boundsInWindow.contains(pointInWindow)
                        }
                        if (hit != null) hit.onClick() else currentOnClick?.invoke()
                    },
                    onLongPress = if (currentOnLongClick != null) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnLongClick?.invoke()
                        }
                    } else null,
                )
            }
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(animatedBackgroundColor, shape)
            .heightIn(min = minHeight)
            .then(interactiveModifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
