package com.niki914.uikit.base.skin

import androidx.compose.runtime.Immutable

/**
 * 一套皮肤 = 一组液态玻璃材质参数（可选附带纯黑背景开关）。
 *
 * **有意不含调色**：种子色（`ThemePrefs.seedColor`）是独立的一条轴，皮肤只管
 * 「材质手感」。两者正交——换皮肤不该冲掉用户挑好的主题色，换主题色也不该
 * 退回默认材质。要一套「连配色一起给」的成品，那是预设（preset）的事，
 * 不塞进这里。
 *
 * 显示名不放这里：ui-kit 是设计系统，不带 i18n 文案；名字由 app 侧按 [id] 映射。
 */
/**
 * 一套皮肤 = 一组可拼接的部件：主题色 + 材质 + 背景装饰 + 对话框背景。
 *
 * 四个部件各自独立可选，任意组合都成立——这正是「拼接式」的含义：
 * 不存在「必须成套」的预设，内置的那几套也只是拼好的现成组合。
 *
 * 部件之间有意不互相推导：背景图**不会**自动改变主题色（那需要取色算法，
 * 是显式动作，不是副作用），材质也**不会**因为换了背景图而变。
 */
@Immutable
data class Skin(
    /** 稳定 id，落盘值。改 id 等于让存量用户掉回默认皮肤。 */
    val id: String,
    /** 纯黑背景（AMOLED）。仅在设了种子色时生效——只影响生成的色板。 */
    val amoled: Boolean = false,
    val tokens: LiquidTokens = LiquidTokens(),
    /** 主界面背景装饰。默认无装饰 = 与抽取 token 之前的观感一致。 */
    val backdrop: SkinBackdrop = SkinBackdrop.None,
    /** 对话框面板背景。 */
    val dialogBackground: SkinDialogBackground = SkinDialogBackground.None,
    /**
     * 主题色。**null = 不指定**，由当前主题偏好的配色决定。
     *
     * 之所以允许缺省而不是给个默认色：配色早已是独立的一面选择（12 个预设 + 动态色），
     * 内置皮肤不指定就能继续跟随用户的选择；只有用户显式拼进去的颜色才写在这里。
     */
    val color: SkinColor? = null,
)

/**
 * 主题色来源。拼进皮肤里的一个部件。
 */
@Immutable
sealed interface SkinColor {
    /** 跟随系统壁纸动态色（SDK 31+；更低版本由 BaseTheme 回落到内置色板）。 */
    data object Dynamic : SkinColor

    /** 任意 ARGB：预设色板与取色器产出的都是它。 */
    data class Argb(val value: Int) : SkinColor
}
