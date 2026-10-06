package com.fesht3.zafiro.app.ui.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.niki914.uikit.base.skin.LiquidTokens
import com.niki914.uikit.base.skin.Skin
import com.niki914.uikit.base.skin.SkinBackdrop
import com.niki914.uikit.base.skin.SkinColor
import com.niki914.uikit.base.skin.SkinDialogBackground
import com.niki914.uikit.base.skin.SkinImageFit
import com.niki914.uikit.base.skin.SkinImageSpec
import com.fesht3.zafiro.repo.SavedSkinRecord

/**
 * 材质的三个离散轴。这是**拼接系统的「材质」部件**，不再代表整套皮肤：
 * 背景、对话框图、主题色都是与之并列的独立部件，各自由落盘记录承载。
 *
 * 为什么是离散档位而不是滑杆：全应用没有任何连续控件，交互语言是「选项卡片」。
 * 引入滑杆要连带造一个与液态玻璃同语言的控件，成本远高于收益；档位也能把
 * 「可调范围」收紧到不会调出难看组合的区间内。
 */
enum class SkinGlass(val storageKey: String) {
    Off("off"),
    Light("light"),
    Standard("standard"),
    Strong("strong");

    companion object {
        fun fromStorageKey(value: String?): SkinGlass =
            entries.firstOrNull { it.storageKey == value } ?: Standard
    }
}

enum class SkinSurface(val storageKey: String) {
    Translucent("translucent"),
    Standard("standard"),
    Solid("solid");

    companion object {
        fun fromStorageKey(value: String?): SkinSurface =
            entries.firstOrNull { it.storageKey == value } ?: Standard
    }
}

enum class SkinCorner(val storageKey: String) {
    Sharp("sharp"),
    Standard("standard"),
    Round("round");

    companion object {
        fun fromStorageKey(value: String?): SkinCorner =
            entries.firstOrNull { it.storageKey == value } ?: Standard
    }
}

/** 背景装饰档位：无 / 两档派生渐变 / 自选图片。图片档的路径在记录的 `backgroundPath`。 */
enum class SkinBackgroundStyle(val storageKey: String) {
    None("none"),
    Soft("soft"),
    Bold("bold"),
    Image("image");

    companion object {
        fun fromStorageKey(value: String?): SkinBackgroundStyle =
            entries.firstOrNull { it.storageKey == value } ?: None
    }
}

/**
 * 材质三轴取值。全 [Standard] 时与内置「玻璃」预设逐值相同——
 * 这是刻意的：新建的皮肤不该一进来就是个和默认不一样的怪东西。
 */
data class CustomSkinChoices(
    val glass: SkinGlass = SkinGlass.Standard,
    val surface: SkinSurface = SkinSurface.Standard,
    val corner: SkinCorner = SkinCorner.Standard,
)

/** 从记录里取出它的材质三轴。 */
fun SavedSkinRecord.materialChoices(): CustomSkinChoices = CustomSkinChoices(
    glass = SkinGlass.fromStorageKey(glass),
    surface = SkinSurface.fromStorageKey(surface),
    corner = SkinCorner.fromStorageKey(corner),
)

/** 三轴档位 → 渲染用材质 token。 */
fun CustomSkinChoices.toSkinTokens(): LiquidTokens {
    val g = glass.tokens()
    val s = surface.tokens()
    val c = corner.tokens()
    return LiquidTokens(
        glassBlur = g.blur,
        dialogBlur = g.dialogBlur,
        glassLensRadius = g.lens,
        glassLensHeight = g.lensHeight,
        dialogLensRadius = g.dialogLens,
        dialogLensHeight = g.dialogLensHeight,
        vibrancyEnabled = g.vibrancy,
        refractionHeight = g.refraction,
        ambientHighlightEnabled = g.highlight,
        fieldSurfaceAlpha = s.fieldSurface,
        fieldDisabledSurfaceAlpha = s.fieldDisabledSurface,
        fieldTintAlpha = s.fieldTint,
        dialogSurfaceAlpha = s.dialogSurface,
        dialogTintAlpha = s.dialogTint,
        dialogScrimAlpha = s.dialogScrim,
        buttonSurfaceAlpha = s.buttonSurface,
        fieldRadius = c.field,
        cardRadius = c.card,
        dialogRadius = c.dialog,
        sheetTopRadius = c.sheet,
        optionRowRadius = c.optionRow,
    )
}

// ── 档位 → 具体数值 ────────────────────────────────────────────────────────
// Standard 档逐值等于 LiquidTokens 的默认值，改默认值时这里要同步。

private data class GlassTokens(
    val blur: Dp,
    val dialogBlur: Dp,
    val lens: Dp,
    val lensHeight: Dp,
    val dialogLens: Dp,
    val dialogLensHeight: Dp,
    val vibrancy: Boolean,
    val refraction: Dp,
    val highlight: Boolean,
)

private fun SkinGlass.tokens(): GlassTokens = when (this) {
    SkinGlass.Off -> GlassTokens(
        blur = 0.dp, dialogBlur = 0.dp,
        lens = 0.dp, lensHeight = 0.dp, dialogLens = 0.dp, dialogLensHeight = 0.dp,
        vibrancy = false, refraction = 0.dp, highlight = false,
    )

    SkinGlass.Light -> GlassTokens(
        blur = 1.dp, dialogBlur = 2.dp,
        lens = 8.dp, lensHeight = 16.dp, dialogLens = 10.dp, dialogLensHeight = 20.dp,
        vibrancy = true, refraction = 4.dp, highlight = true,
    )

    SkinGlass.Standard -> GlassTokens(
        blur = 2.dp, dialogBlur = 4.dp,
        lens = 12.dp, lensHeight = 24.dp, dialogLens = 14.dp, dialogLensHeight = 28.dp,
        vibrancy = true, refraction = 6.dp, highlight = true,
    )

    SkinGlass.Strong -> GlassTokens(
        blur = 8.dp, dialogBlur = 12.dp,
        lens = 18.dp, lensHeight = 32.dp, dialogLens = 20.dp, dialogLensHeight = 40.dp,
        vibrancy = true, refraction = 10.dp, highlight = true,
    )
}

private data class SurfaceTokens(
    val fieldSurface: Float,
    val fieldDisabledSurface: Float,
    val fieldTint: Float,
    val dialogSurface: Float,
    val dialogTint: Float,
    val dialogScrim: Float,
    val buttonSurface: Float,
)

private fun SkinSurface.tokens(): SurfaceTokens = when (this) {
    SkinSurface.Translucent -> SurfaceTokens(
        fieldSurface = 0.42f, fieldDisabledSurface = 0.30f, fieldTint = 0.32f,
        dialogSurface = 0.55f, dialogTint = 0.18f, dialogScrim = 0.42f, buttonSurface = 0.12f,
    )

    SkinSurface.Standard -> SurfaceTokens(
        fieldSurface = 0.64f, fieldDisabledSurface = 0.42f, fieldTint = 0.32f,
        dialogSurface = 0.76f, dialogTint = 0.18f, dialogScrim = 0.42f, buttonSurface = 0.18f,
    )

    SkinSurface.Solid -> SurfaceTokens(
        fieldSurface = 1f, fieldDisabledSurface = 0.92f, fieldTint = 0.12f,
        dialogSurface = 1f, dialogTint = 0.10f, dialogScrim = 0.60f, buttonSurface = 0.18f,
    )
}

private data class CornerTokens(
    val field: Dp,
    val card: Dp,
    val dialog: Dp,
    val sheet: Dp,
    val optionRow: Dp,
)

private fun SkinCorner.tokens(): CornerTokens = when (this) {
    SkinCorner.Sharp -> CornerTokens(
        field = 8.dp, card = 8.dp, dialog = 12.dp, sheet = 12.dp, optionRow = 6.dp,
    )

    SkinCorner.Standard -> CornerTokens(
        field = 36.dp, card = 28.dp, dialog = 48.dp, sheet = 32.dp, optionRow = 20.dp,
    )

    SkinCorner.Round -> CornerTokens(
        field = 40.dp, card = 36.dp, dialog = 56.dp, sheet = 40.dp, optionRow = 28.dp,
    )
}

// ── 落盘记录 → 渲染皮肤 ────────────────────────────────────────────────────
// 档位 key 未知一律回落默认档：改档位名不该让存量皮肤崩，只是那一轴回到默认。

/** 落盘记录 → 渲染用皮肤。图片文件是否存在不在这里判断，渲染侧负责兜底。 */
fun SavedSkinRecord.toSkin(): Skin {
    val material = CustomSkinChoices(
        glass = SkinGlass.fromStorageKey(glass),
        surface = SkinSurface.fromStorageKey(surface),
        corner = SkinCorner.fromStorageKey(corner),
    )
    return Skin(
        id = id,
        amoled = amoled,
        tokens = material.toSkinTokens(),
        backdrop = backgroundBackdrop(),
        dialogBackground = dialogBackdrop(),
        color = color.toSkinColor(),
    )
}

private fun SavedSkinRecord.backgroundBackdrop(): SkinBackdrop = when (backgroundStyle) {
    SavedSkinRecord.BACKGROUND_SOFT -> SkinBackdrop.ThemedGradient(SkinBackdrop.SOFT_INTENSITY)
    SavedSkinRecord.BACKGROUND_BOLD -> SkinBackdrop.ThemedGradient(SkinBackdrop.BOLD_INTENSITY)

    SavedSkinRecord.BACKGROUND_IMAGE -> if (backgroundPath.isBlank()) {
        // 档位是图片但没路径（存量异常 / 用户清了文件）：退化成无装饰，
        // 不能给一块空白，那看起来像 bug
        SkinBackdrop.None
    } else {
        SkinBackdrop.Image(
            SkinImageSpec(
                path = backgroundPath,
                fit = skinImageFitOf(backgroundFit),
                scrimAlpha = backgroundScrim,
            )
        )
    }

    else -> SkinBackdrop.None
}

private fun SavedSkinRecord.dialogBackdrop(): SkinDialogBackground =
    if (dialogPath.isBlank()) {
        SkinDialogBackground.None
    } else {
        SkinDialogBackground.Image(
            SkinImageSpec(path = dialogPath, scrimAlpha = dialogScrim)
        )
    }

/** 主题色三态：空串 = 不指定（跟随独立配色设置），"dynamic" = 壁纸取色，其余按 hex。 */
private fun String.toSkinColor(): SkinColor? = when {
    isBlank() -> null
    this == SavedSkinRecord.COLOR_DYNAMIC -> SkinColor.Dynamic
    // 与 theme_seed_color 同一约定：8 位 hex ARGB，转 Int 后就是 Color() 要的值
    else -> toLongOrNull(16)?.toInt()?.let(SkinColor::Argb)
}

internal fun skinImageFitOf(key: String): SkinImageFit = when (key) {
    "contain" -> SkinImageFit.Contain
    "stretch" -> SkinImageFit.Stretch
    else -> SkinImageFit.Cover
}

internal fun SkinImageFit.toStorageKey(): String = when (this) {
    SkinImageFit.Cover -> "cover"
    SkinImageFit.Contain -> "contain"
    SkinImageFit.Stretch -> "stretch"
}
