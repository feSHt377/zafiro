package com.fesht3.zafiro.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.niki914.uikit.base.BaseTheme
import com.niki914.uikit.base.skin.LocalSkinImageLoader
import com.niki914.uikit.base.skin.SkinColor
import com.niki914.uikit.base.skin.SkinImageLoader
import com.fesht3.zafiro.app.ui.model.ThemeController
import com.fesht3.zafiro.app.ui.model.ThemePrefs
import com.fesht3.zafiro.repo.XRepo

/**
 * 皮肤图解码器。ui-kit 不认识沙箱，解码能力只能由 app 层注入。
 *
 * 顶层 val 而非组合内联：不捕获任何组合作用域的状态，没必要每次重组重建。
 */
private val skinImageLoader = SkinImageLoader { path, targetDimension ->
    XRepo.skinImages.decode(path, targetDimension)
}

/**
 * 按当前主题偏好铺开 [BaseTheme] —— 全应用唯一的主题装配点。
 *
 * 存在意义是**悬浮窗**：那是独立 Window，自己起一棵 Compose 树、自己调 BaseTheme。
 * 装配逻辑若各写一遍，加一个主题维度（如皮肤）就会只在主界面生效、悬浮窗静默漏跟。
 * 收在这里，两个入口永远同源。
 *
 * 需要单独拿「当前是不是深色」的地方（如顶栏图标着色）仍自行调用
 * [ThemePrefs.resolveDarkTheme]。
 */
@Composable
fun ZafiroTheme(
    prefs: ThemePrefs = ThemeController.prefs,
    content: @Composable () -> Unit,
) {
    val skin = prefs.skin
    // 皮肤里的主题色优先于独立配色设置：拼进去的颜色就是这套皮肤的一部分。
    // 皮肤不指定（内置皮肤都如此）时才跟随独立的配色选择。
    val resolvedSeed = when (val color = skin.color) {
        null -> prefs.seedColor
        SkinColor.Dynamic -> null
        is SkinColor.Argb -> color.value
    }
    CompositionLocalProvider(LocalSkinImageLoader provides skinImageLoader) {
        BaseTheme(
            darkTheme = prefs.resolveDarkTheme(isSystemInDarkTheme()),
            // 有种子色时它优先，dynamicColor 无意义；传 false 让意图显式
            dynamicColor = resolvedSeed == null,
            seedColor = resolvedSeed?.let { Color(it) },
            amoled = skin.amoled,
            tokens = skin.tokens,
            backdrop = skin.backdrop,
            dialogBackground = skin.dialogBackground,
            content = content,
        )
    }
}
