package com.niki914.uikit.base.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** 皮肤图的铺法。 */
enum class SkinImageFit {
    Cover,
    Contain,
    Stretch,
}

/**
 * 一张皮肤图的绘制参数。
 *
 * [path] 指向沙箱内的文件。**绘制侧必须把它当「随时可能不存在」处理**：用户可能
 * 通过清理工具或卸载残留把文件删掉，此时应退化成纯材质而不是崩或画空白。
 */
@Immutable
data class SkinImageSpec(
    val path: String,
    val fit: SkinImageFit = SkinImageFit.Cover,
    /**
     * 图片之上、内容之下的压暗层强度。
     * 全屏照片当背景时，没有这层就保证不了任何文字的可读性。
     */
    val scrimAlpha: Float = 0.25f,
)

/**
 * 皮肤的背景装饰层（层次 3）。
 *
 * 与材质 token 分开建模：材质决定「表面怎么呈现」，背景决定「表面之下有什么」。
 * 两者都跟着皮肤走，但可调空间不同，混成一个类型会让拼接界面无法分别取舍。
 */
@Immutable
sealed interface SkinBackdrop {
    /** 不做任何装饰：窗口底色即 MaterialTheme 的 background。 */
    data object None : SkinBackdrop

    /**
     * 由**当前配色派生**的柔和竖向渐变（表面色 → 主色容器的混色）。
     *
     * 有意不写死色值：皮肤与配色是两条轴。若把渐变的颜色固化在皮肤里，
     * 用户换了主题色背景却纹丝不动，看起来像坏了。
     *
     * @param intensity 混色比例倍率，0f = 等同于 [None]。
     */
    data class ThemedGradient(val intensity: Float = 1f) : SkinBackdrop

    /** 用户自选的背景图。 */
    data class Image(val spec: SkinImageSpec) : SkinBackdrop

    companion object {
        /** 由 [ThemedGradient.intensity] 分档，供离散选择界面使用。 */
        const val SOFT_INTENSITY: Float = 0.35f
        const val BOLD_INTENSITY: Float = 0.7f
    }
}

/**
 * 对话框面板的背景。
 *
 * 与主背景分成两个槽位：弹窗内容密、字号小，通常需要比主背景更强的压暗层，
 * 复用同一个 spec 会迫使两者取一个折中值。
 */
@Immutable
sealed interface SkinDialogBackground {
    data object None : SkinDialogBackground
    data class Image(val spec: SkinImageSpec) : SkinDialogBackground
}

/**
 * 当前生效的背景装饰。由 [com.niki914.uikit.base.BaseTheme] 下发，
 * 由 `LiquidScreen` 作为最底层渲染。
 */
val LocalSkinBackdrop = staticCompositionLocalOf<SkinBackdrop> { SkinBackdrop.None }

/** 当前生效的对话框背景。由 [com.niki914.uikit.base.BaseTheme] 下发，由 `LiquidDialog` 读取。 */
val LocalSkinDialogBackground =
    staticCompositionLocalOf<SkinDialogBackground> { SkinDialogBackground.None }

/**
 * 皮肤图解码器。**由 app 层注入**（见 `ZafiroTheme`）。
 *
 * ui-kit 是设计系统，不认识存储与沙箱：把路径变成位图的知识在 `:app` 的
 * `SkinImageStore` 里。这里只留一个函数式接口，ui-kit 侧按需调用。
 *
 * 为 null（如 Preview、独立样例）时图片槽位一律按「无图」渲染——
 * 宁可少画一层装饰，也不能因为拿不到加载器而崩或留白块。
 *
 * @param targetDimension 图片实际会被画到的最大边长（像素）。实现必须据此降采样：
 *   全屏原图直接解码会一次吃掉几十 MB，低端机必 OOM。
 */
fun interface SkinImageLoader {
    suspend fun load(path: String, targetDimension: Int): ImageBitmap?
}

val LocalSkinImageLoader = staticCompositionLocalOf<SkinImageLoader?> { null }
