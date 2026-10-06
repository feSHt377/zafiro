package com.niki914.uikit.base.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃的材质 token：玻璃强度、表面不透明度、圆角。
 *
 * 抽出来的唯一目的是让皮肤可切换——在此之前这些值散落在各玻璃组件的调用点上
 * 硬编码，皮肤除了换配色改不了任何手感。默认值**逐字等于**抽取前的硬编码值，
 * 所以不挂任何皮肤时渲染结果与抽取前完全一致。
 *
 * 有意不收录的东西：
 * - 文字/状态透明度（`onSurface.copy(alpha = 0.45f)` 之类）：那是可读性约定，
 *   不是皮肤材质，跟着皮肤变只会牺牲对比度。
 * - 交互样式（pressScale / 阻尼）：皮肤改的是「看起来怎样」，
 *   不是「按下去怎么动」，两者耦合会让每个皮肤都要重新调手感。
 * - LiquidScreen 顶栏的 `barAlpha`：它是随滚动量动画的**运行时值**，
 *   不是常量，不属于 token。
 */
@Immutable
data class LiquidTokens(
    // ── 玻璃采样 ────────────────────────────────────────────────────────────
    /** 通用玻璃模糊半径（按钮 / 输入框）。0 = 无模糊，退化为实心面。 */
    val glassBlur: Dp = 2.dp,
    /** 弹窗玻璃模糊半径（弹窗面积大，模糊重一档）。 */
    val dialogBlur: Dp = 4.dp,
    /** 通用玻璃镜头畸变：半径与高度成对使用。 */
    val glassLensRadius: Dp = 12.dp,
    val glassLensHeight: Dp = 24.dp,
    /** 弹窗镜头畸变。 */
    val dialogLensRadius: Dp = 14.dp,
    val dialogLensHeight: Dp = 28.dp,
    /** 色彩增艳。关掉后玻璃只剩模糊，观感更平。 */
    val vibrancyEnabled: Boolean = true,
    /** 小圆形元件的镜头折射高度（工具栏按钮 / 开关滑块）。0 = 无折射。 */
    val refractionHeight: Dp = 6.dp,
    /** 环境高光（元件顶部那层细微反光）。关掉后少一分「湿润感」，更接近纯色块。 */
    val ambientHighlightEnabled: Boolean = true,

    // ── 表面不透明度 ────────────────────────────────────────────────────────
    /** 弹窗面板底色不透明度。1f = 完全不透，看不见背后内容。 */
    val dialogSurfaceAlpha: Float = 0.76f,
    /** 弹窗品牌色叠加。 */
    val dialogTintAlpha: Float = 0.18f,
    /** 弹窗遮罩（背后整页的压暗程度）。 */
    val dialogScrimAlpha: Float = 0.42f,
    /** 输入框底色不透明度。 */
    val fieldSurfaceAlpha: Float = 0.64f,
    /** 输入框禁用态底色不透明度。 */
    val fieldDisabledSurfaceAlpha: Float = 0.42f,
    /** 输入框品牌色叠加。 */
    val fieldTintAlpha: Float = 0.32f,
    /** 按钮表面色相对容器色的不透明度（LiquidButton 系列）。 */
    val buttonSurfaceAlpha: Float = 0.18f,

    // ── 圆角 ────────────────────────────────────────────────────────────────
    /** 输入框 / 对话框内表单字段。 */
    val fieldRadius: Dp = 36.dp,
    /** 设置卡片。 */
    val cardRadius: Dp = 28.dp,
    /** 弹窗面板。 */
    val dialogRadius: Dp = 48.dp,
    /** 底部单的顶部两角。 */
    val sheetTopRadius: Dp = 32.dp,
    /** 选项单里一行的高亮框。 */
    val optionRowRadius: Dp = 20.dp,
)

/**
 * 当前生效的材质 token。由 [com.niki914.uikit.base.BaseTheme] 下发，
 * 玻璃组件经此读取，不再硬编码。
 */
val LocalLiquidTokens = staticCompositionLocalOf { LiquidTokens() }
