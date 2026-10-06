package com.niki914.uikit.infra

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.niki914.uikit.base.skin.LocalLiquidTokens
import com.niki914.uikit.base.skin.LocalSkinDialogBackground
import com.niki914.uikit.base.skin.LocalSkinImageLoader
import com.niki914.uikit.base.skin.SkinDialogBackground
import com.niki914.uikit.base.skin.SkinImageFit
import com.niki914.uikit.base.skin.SkinImageSpec
import com.niki914.uikit.infra.shape.G2FieldShape
import java.util.concurrent.atomic.AtomicLong

internal class LiquidDialogHostEntry(
    val id: Long,
    content: @Composable () -> Unit,
) {
    var content by mutableStateOf(content)
        internal set
}

internal class LiquidDialogHostState {
    private val mutableEntries = mutableStateListOf<LiquidDialogHostEntry>()

    val entries: List<LiquidDialogHostEntry>
        get() = mutableEntries

    internal fun upsert(id: Long, content: @Composable () -> Unit) {
        val entry = mutableEntries.firstOrNull { it.id == id }
        if (entry != null) {
            entry.content = content
        } else {
            mutableEntries += LiquidDialogHostEntry(id = id, content = content)
        }
    }

    internal fun remove(id: Long) {
        mutableEntries.removeAll { it.id == id }
    }
}

internal val LocalLiquidDialogHostState: ProvidableCompositionLocal<LiquidDialogHostState> =
    staticCompositionLocalOf {
        error("LiquidDialog must be used inside LiquidScreen")
    }

private val liquidDialogHostEntryId = AtomicLong(0L)

private fun nextLiquidDialogHostEntryId(): Long = liquidDialogHostEntryId.incrementAndGet()

@Composable
fun LiquidDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackgroundTap: Boolean = true,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val hostState = LocalLiquidDialogHostState.current
    val dialogId = remember { nextLiquidDialogHostEntryId() }

    SideEffect {
        hostState.upsert(dialogId) {
            LiquidDialogSurface(
                visible = visible,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                dismissOnBackgroundTap = dismissOnBackgroundTap,
                title = title,
                text = text,
                actions = actions,
                content = content,
            )
        }
    }

    DisposableEffect(hostState, dialogId) {
        onDispose {
            hostState.remove(dialogId)
        }
    }
}

@Composable
private fun LiquidDialogSurface(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackgroundTap: Boolean = true,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val backdrop = rememberLayerBackdrop()
    val colorScheme = MaterialTheme.colorScheme
    val tokens = LocalLiquidTokens.current
    val scrimColor = colorScheme.scrim.copy(alpha = tokens.dialogScrimAlpha)
    val panelSurfaceColor = colorScheme.surfaceContainerHigh.copy(alpha = tokens.dialogSurfaceAlpha)
    val panelTintColor = colorScheme.primaryContainer.copy(alpha = tokens.dialogTintAlpha)
    val interactionSource = remember { MutableInteractionSource() }
    val panelShape = G2FieldShape(tokens.dialogRadius)
    // 皮肤的对话框背景图。文件可能已被外部清掉，取不到就退回纯材质。
    val dialogBackground = LocalSkinDialogBackground.current
    val dialogImageSpec = (dialogBackground as? SkinDialogBackground.Image)?.spec
    val dialogImage = rememberSkinImage(dialogImageSpec)

    // 治本：调用方条件组合（首帧 visible 即 true）时，AnimatedVisibility 无过渡可播、
    // 直接闪现。这里先按 false 渲染一帧再翻转，强制播放入场过渡；对常驻
    // 组合（先 false 后翻 true）的调用姿势无影响。
    var dialogMounted by remember { mutableStateOf(false) }
    val effectiveVisible = dialogMounted && visible
    LaunchedEffect(Unit) {
        withFrameNanos { }
        dialogMounted = true
    }

    // 弹窗可见时消费系统返回键（issue：语言/协议弹窗按返回直接弹页）。
    // LiquidDialog 是 portal overlay 而非 Dialog，返回不会自动落在弹窗上；
    // 这里后于页面级 BackHandler 注册（弹窗内容组合在后），可见时优先接管，
    // 隐藏后随 AnimatedVisibility 自动注销。
    BackHandler(enabled = effectiveVisible) {
        onDismissRequest()
    }

    AnimatedVisibility(
        visible = effectiveVisible,
        enter = fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 1.04f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 1.02f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val horizontalMargin = 24.dp
            val verticalMargin = 24.dp
            val maxPanelWidth = (maxWidth - horizontalMargin * 2)
                .coerceAtLeast(0.dp)
                .coerceAtMost(360.dp)
            val minPanelWidth = if (maxPanelWidth < 200.dp) {
                maxPanelWidth
            } else {
                200.dp
            }
            val maxPanelHeight = (maxHeight - verticalMargin * 2).coerceAtLeast(0.dp)
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        enabled = true,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (dismissOnBackgroundTap) {
                                onDismissRequest()
                            }
                        },
                    ),
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = horizontalMargin, vertical = verticalMargin)
                    .widthIn(min = minPanelWidth, max = maxPanelWidth)
                    .heightIn(max = maxPanelHeight)
                    .verticalScroll(scrollState)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { panelShape },
                        effects = {
                            if (tokens.vibrancyEnabled) vibrancy()
                            blur(tokens.dialogBlur.toPx())
                            lens(tokens.dialogLensRadius.toPx(), tokens.dialogLensHeight.toPx())
                        },
                        onDrawSurface = {
                            if (dialogImage != null && dialogImageSpec != null) {
                                // 有图时：图片铺满面板 → 压暗层保可读 → 品牌色叠加保留皮肤色相。
                                // 不再铺 panelSurfaceColor：它是实色面板底，会把图彻底盖住。
                                drawSkinImage(dialogImage, dialogImageSpec)
                                drawRect(Color.Black.copy(alpha = dialogImageSpec.scrimAlpha))
                                drawRect(panelTintColor, blendMode = BlendMode.Hue)
                            } else {
                                drawRect(panelTintColor, blendMode = BlendMode.Hue)
                                drawRect(panelSurfaceColor)
                            }
                        },
                    )
                    .clip(panelShape)
                    .clickable(
                        enabled = true,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                title?.invoke()
                text?.invoke()
                content?.invoke(this)
                if (actions != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }
    }
}

/**
 * 解码皮肤图供绘制使用。
 *
 * 弹窗尺寸要到布局期才知道，这里按「屏幕最大边」预解码当上界：弹窗不会大于屏幕，
 * 所以解码结果只多不少，而 inSampleSize 的粗缩已经足够把内存压到可控范围。
 * 拿不到加载器（Preview 等）或 [spec] 为空时返回 null，调用方退回纯材质。
 */
@Composable
private fun rememberSkinImage(spec: SkinImageSpec?): ImageBitmap? {
    val loader = LocalSkinImageLoader.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val path = spec?.path
    val targetPx = with(density) {
        maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx()
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = if (path.isNullOrBlank() || loader == null) null else loader.load(path, targetPx)
    }
    return bitmap
}

/** 把皮肤图按 [SkinImageSpec.fit] 铺满当前绘制区（面板内部已是圆角裁剪）。 */
private fun DrawScope.drawSkinImage(bitmap: ImageBitmap, spec: SkinImageSpec) {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()
    if (srcW <= 0f || srcH <= 0f || size.width <= 0f || size.height <= 0f) return

    if (spec.fit == SkinImageFit.Stretch) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
        return
    }

    val scale = when (spec.fit) {
        // Cover 取大：铺满并溢出裁剪；Contain 取小：整图可见并留边
        SkinImageFit.Cover -> maxOf(size.width / srcW, size.height / srcH)
        SkinImageFit.Contain -> minOf(size.width / srcW, size.height / srcH)
        SkinImageFit.Stretch -> 1f
    }
    val dstW = srcW * scale
    val dstH = srcH * scale
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(
            ((size.width - dstW) / 2f).toInt(),
            ((size.height - dstH) / 2f).toInt(),
        ),
        dstSize = IntSize(dstW.toInt(), dstH.toInt()),
        filterQuality = FilterQuality.Medium,
    )
}
