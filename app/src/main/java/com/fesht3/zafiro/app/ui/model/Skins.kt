package com.fesht3.zafiro.app.ui.model

import androidx.annotation.StringRes
import com.fesht3.zafiro.app.R
import com.fesht3.zafiro.repo.SavedSkinRecord

/**
 * 内置预设皮肤。
 *
 * 关键点：内置项**和用户拼出来的皮肤用同一个类型**（[SavedSkinRecord]），只存在代码里、
 * 不进皮肤库。这样解析、渲染、工作台列出一律走同一条路径，不需要「内置走一套、用户走另一套」
 * 的分支——那种分支正是加部件时最容易漏改的地方。
 *
 * 每套只声明体现性格的那几轴，其余留空走默认档（见 `CustomSkin.kt` 的档位映射）。
 * 名称不在记录里：内置名要 5 语言，文案归 app 的字符串资源（[skinNameRes]）。
 */
object Skins {
    /** 内置 id → 文案。用户皮肤的 `name` 为空时才回落这里。 */
    const val LIQUID_ID = "liquid"
    const val SOLID_ID = "solid"
    const val FROSTED_ID = "frosted"
    const val FLAT_ID = "flat"
    const val AMOLED_ID = "amoled"

    /** 现有观感。全默认档，所以它必须永远保持空记录。 */
    val Liquid = SavedSkinRecord(id = LIQUID_ID)

    /** 实心：去掉全部玻璃，表面不透明。 */
    val Solid = SavedSkinRecord(
        id = SOLID_ID,
        glass = SkinGlass.Off.storageKey,
        surface = SkinSurface.Solid.storageKey,
    )

    /** 重磨砂：玻璃加码 + 表面更透；配一层派生渐变才看得出「透」。 */
    val Frosted = SavedSkinRecord(
        id = FROSTED_ID,
        glass = SkinGlass.Strong.storageKey,
        surface = SkinSurface.Translucent.storageKey,
        backgroundStyle = SavedSkinRecord.BACKGROUND_SOFT,
    )

    /** 极简：无玻璃 + 小圆角 + 高不透明。把「液态」语言整体收掉。 */
    val Flat = SavedSkinRecord(
        id = FLAT_ID,
        glass = SkinGlass.Off.storageKey,
        surface = SkinSurface.Solid.storageKey,
        corner = SkinCorner.Sharp.storageKey,
    )

    /** 纯黑：AMOLED 省电向，顺带关掉玻璃（纯黑上做毛玻璃没有意义）。 */
    val Amoled = SavedSkinRecord(
        id = AMOLED_ID,
        amoled = true,
        glass = SkinGlass.Off.storageKey,
        surface = SkinSurface.Solid.storageKey,
    )

    val builtin: List<SavedSkinRecord> = listOf(Liquid, Solid, Frosted, Flat, Amoled)

    val default: SavedSkinRecord = Liquid

    /** 未知 id 回落默认，不抛：存量文件里的旧 id 不该让主题页崩。 */
    fun findBuiltin(id: String?): SavedSkinRecord =
        builtin.firstOrNull { it.id == id } ?: default

    fun isBuiltin(id: String?): Boolean = builtin.any { it.id == id }
}

@StringRes
internal fun skinNameRes(skinId: String): Int? = when (skinId) {
    Skins.Solid.id -> R.string.ui_skin_solid
    Skins.Frosted.id -> R.string.ui_skin_frosted
    Skins.Flat.id -> R.string.ui_skin_flat
    Skins.Amoled.id -> R.string.ui_skin_amoled
    Skins.Liquid.id -> R.string.ui_skin_liquid
    else -> null
}
