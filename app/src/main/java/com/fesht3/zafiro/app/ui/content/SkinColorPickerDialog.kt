package com.fesht3.zafiro.app.ui.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niki914.uikit.infra.LiquidDialog
import com.niki914.uikit.infra.component.MaterialTintLiquidButton
import com.fesht3.zafiro.app.R
import com.fesht3.zafiro.app.ui.model.SkinHsv
import com.fesht3.zafiro.repo.XRepo
import kotlinx.coroutines.launch

/**
 * 任意颜色取色器：饱和度/明度面 + 色相条。
 *
 * 两个刻意的取舍：
 * - **只支持点击与拖动，不做全局实时预览**。拖动时每一帧都写穿到 store 会带来
 *   整棵主题树的高频重组，得不偿失；对话框内的预览圆点已经足够看清结果。
 * - 颜色在对话框内是**本地状态**，点确定才落地。这样取消就是真的取消。
 */
@Composable
internal fun SkinColorPickerDialog(
    initialArgb: Int,
    /** 非空时提供「从背景图取色」。传的是当前生效皮肤的背景图路径。 */
    backgroundPath: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initial = remember(initialArgb) { SkinHsv.hsvOf(initialArgb) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    val scope = rememberCoroutineScope()

    val currentArgb = SkinHsv.toArgb(hue, saturation, value)

    LiquidDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.ui_skin_color_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            // 饱和度(横) × 明度(纵)：底色是纯色相，上叠白→透明与透明→黑
            PositionTrackingCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium),
                onPosition = { fx, fy ->
                    saturation = fx
                    value = 1f - fy
                },
            ) {
                val pure = Color(SkinHsv.toArgb(hue, 1f, 1f))
                drawRect(pure)
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawCursor(
                    x = saturation * size.width,
                    y = (1f - value) * size.height,
                )
            }

            // 色相条：7 个锚点绕一圈，线性插值出的中间色与 HSV 色相环一致
            PositionTrackingCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(MaterialTheme.shapes.small),
                onPosition = { fx, _ -> hue = SkinHsv.normalizeHue(fx * 360f) },
            ) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                            Color(0xFFFF0000),
                        )
                    )
                )
                drawCursor(x = hue / 360f * size.width, y = size.height / 2f, radius = 9f)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(currentArgb)),
                )
                Text(
                    text = "#%06X".format(currentArgb and 0xFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!backgroundPath.isNullOrBlank()) {
                MaterialTintLiquidButton(
                    text = stringResource(R.string.ui_skin_color_from_background),
                    onClick = {
                        scope.launch {
                            // 取到就直接落到本地状态，之后仍可继续微调
                            XRepo.skinImages.dominantColor(backgroundPath)?.let { picked ->
                                val hsv = SkinHsv.hsvOf(picked)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        actions = {
            MaterialTintLiquidButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            MaterialTintLiquidButton(
                text = stringResource(R.string.ui_skin_name_confirm),
                onClick = { onConfirm(currentArgb) },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}

/**
 * 可按下、可拖动的画布。
 *
 * 用 `awaitEachGesture` 而不是 `detectDragGestures`：后者有触摸阈值，
 * **单击不会触发**，而取色器必须支持点一下就选色。拖动也走同一段逻辑，
 * 所以按下即生效、移动即跟随。
 */
@Composable
private fun PositionTrackingCanvas(
    modifier: Modifier,
    onPosition: (fractionX: Float, fractionY: Float) -> Unit,
    draw: DrawScope.() -> Unit,
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun emit(position: Offset) {
                    onPosition(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (position.y / size.height).coerceIn(0f, 1f),
                    )
                }
                emit(down.position)
                down.consume()
                drag(down.id) { change ->
                    emit(change.position)
                    change.consume()
                }
            }
        },
    ) { draw() }
}

/** 取色游标：白圈 + 半透明黑边，深浅底上都看得见。 */
private fun DrawScope.drawCursor(x: Float, y: Float, radius: Float = 11f) {
    drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = radius + 2f, center = Offset(x, y))
    drawCircle(color = Color.White, radius = radius, center = Offset(x, y))
}
