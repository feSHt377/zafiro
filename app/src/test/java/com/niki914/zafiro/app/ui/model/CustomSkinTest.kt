package com.niki914.zafiro.app.ui.model

import com.niki914.uikit.base.skin.LiquidTokens
import com.niki914.uikit.base.skin.SkinBackdrop
import com.niki914.uikit.base.skin.SkinColor
import com.niki914.uikit.base.skin.SkinDialogBackground
import com.niki914.uikit.base.skin.SkinImageFit
import com.niki914.zafiro.repo.SavedSkinRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼接部件的映射：落盘记录 → 渲染皮肤。
 *
 * 重点锁住两条靠人眼守不住的不变量：
 * 1. **全默认档必须逐值等于 [LiquidTokens] 默认值**——改了 token 默认值、忘了同步档位，
 *    结果只是「新建的皮肤一进来就和默认观感不一样」，没有任何编译错误或崩溃提示。
 * 2. **图片档但没路径必须退化成无装饰**——否则用户清掉文件后会看到一块空白，
 *    那看起来像 bug 而不像「图片没了」。
 */
class CustomSkinTest {

    @Test
    fun `default material choices match default tokens exactly`() {
        assertEquals(
            "全默认档必须等于 LiquidTokens 默认值；改了默认值请同步 CustomSkin.kt",
            LiquidTokens(),
            CustomSkinChoices().toSkinTokens(),
        )
    }

    @Test
    fun `each non default axis actually changes something`() {
        // 反向保证：每个非默认档至少要改动一个值，否则那个档位是个哑选项
        SkinGlass.entries.filter { it != SkinGlass.Standard }.forEach { glass ->
            assertTrue(
                "$glass 档没有改变任何参数",
                CustomSkinChoices(glass = glass).toSkinTokens() != LiquidTokens(),
            )
        }
        SkinSurface.entries.filter { it != SkinSurface.Standard }.forEach { surface ->
            assertTrue(
                "$surface 档没有改变任何参数",
                CustomSkinChoices(surface = surface).toSkinTokens() != LiquidTokens(),
            )
        }
        SkinCorner.entries.filter { it != SkinCorner.Standard }.forEach { corner ->
            assertTrue(
                "$corner 档没有改变任何参数",
                CustomSkinChoices(corner = corner).toSkinTokens() != LiquidTokens(),
            )
        }
    }

    @Test
    fun `blank record maps to a skin without any decoration`() {
        // 空记录 = 内置「玻璃」预设 = 抽取皮肤系统之前的观感
        val skin = SavedSkinRecord(id = "x").toSkin()
        assertEquals(LiquidTokens(), skin.tokens)
        assertEquals(SkinBackdrop.None, skin.backdrop)
        assertEquals(SkinDialogBackground.None, skin.dialogBackground)
        assertNull("不指定 = 跟随全局配色", skin.color)
    }

    @Test
    fun `gradient styles map to graded intensities`() {
        assertEquals(
            SkinBackdrop.ThemedGradient(SkinBackdrop.SOFT_INTENSITY),
            SavedSkinRecord(id = "x", backgroundStyle = SavedSkinRecord.BACKGROUND_SOFT).toSkin().backdrop,
        )
        assertEquals(
            SkinBackdrop.ThemedGradient(SkinBackdrop.BOLD_INTENSITY),
            SavedSkinRecord(id = "x", backgroundStyle = SavedSkinRecord.BACKGROUND_BOLD).toSkin().backdrop,
        )
    }

    @Test
    fun `image background carries path fit and scrim`() {
        val skin = SavedSkinRecord(
            id = "x",
            backgroundStyle = SavedSkinRecord.BACKGROUND_IMAGE,
            backgroundPath = "/data/skins/abc.png",
            backgroundFit = "contain",
            backgroundScrim = 0.5f,
        ).toSkin()
        val backdrop = skin.backdrop as SkinBackdrop.Image
        assertEquals("/data/skins/abc.png", backdrop.spec.path)
        assertEquals(SkinImageFit.Contain, backdrop.spec.fit)
        assertEquals(0.5f, backdrop.spec.scrimAlpha, 0.0001f)
    }

    @Test
    fun `image style without path degrades to no decoration`() {
        // 用户清掉了文件 / 存量数据异常：必须退化成无装饰，不能画一块空白
        val skin = SavedSkinRecord(
            id = "x",
            backgroundStyle = SavedSkinRecord.BACKGROUND_IMAGE,
            backgroundPath = "",
        ).toSkin()
        assertEquals(SkinBackdrop.None, skin.backdrop)
    }

    @Test
    fun `dialog image degrades to none when path blank`() {
        assertEquals(
            SkinDialogBackground.None,
            SavedSkinRecord(id = "x", dialogPath = "").toSkin().dialogBackground,
        )
        val withImage = SavedSkinRecord(id = "x", dialogPath = "/p.png", dialogScrim = 0.6f)
            .toSkin().dialogBackground as SkinDialogBackground.Image
        assertEquals("/p.png", withImage.spec.path)
        assertEquals(0.6f, withImage.spec.scrimAlpha, 0.0001f)
    }

    @Test
    fun `color has three distinct states`() {
        assertNull("空串 = 不指定，跟随全局配色", SavedSkinRecord(id = "x").toSkin().color)
        assertEquals(
            SkinColor.Dynamic,
            SavedSkinRecord(id = "x", color = SavedSkinRecord.COLOR_DYNAMIC).toSkin().color,
        )
        // 与 theme_seed_color 同一约定：8 位 hex ARGB
        assertEquals(
            SkinColor.Argb(0xFF52DBC9.toInt()),
            SavedSkinRecord(id = "x", color = "FF52DBC9").toSkin().color,
        )
        // 畸形 hex 不能崩，退化成「不指定」
        assertNull(SavedSkinRecord(id = "x", color = "zzz").toSkin().color)
    }

    @Test
    fun `unknown axis keys fall back to default`() {
        val skin = SavedSkinRecord(
            id = "x",
            glass = "nope",
            surface = "",
            corner = "garbage",
        ).toSkin()
        assertEquals(LiquidTokens(), skin.tokens)
    }

    @Test
    fun `library lists builtins before user skins`() {
        val user = SavedSkinRecord(id = "skin-a", name = "Mine")
        val prefs = ThemePrefs(activeSkinId = user.id, skins = listOf(user))
        assertEquals(Skins.builtin.size + 1, prefs.library.size)
        assertEquals(Skins.builtin.map { it.id }, prefs.library.dropLast(1).map { it.id })
        assertEquals("skin-a", prefs.library.last().id)
    }

    @Test
    fun `active record prefers user skin then falls back to builtin`() {
        val user = SavedSkinRecord(id = "skin-a", name = "Mine", glass = SkinGlass.Off.storageKey)
        // 用户皮肤生效
        assertEquals(user, ThemePrefs(activeSkinId = "skin-a", skins = listOf(user)).activeRecord)
        // 内置 id 生效（不在 skins 里，必须回落内置查找）
        assertEquals(
            Skins.Frosted.id,
            ThemePrefs(activeSkinId = Skins.Frosted.id).activeRecord.id,
        )
        // 未知 id 也不崩
        assertEquals(Skins.default.id, ThemePrefs(activeSkinId = "gone").activeRecord.id)
        // null = 内置默认
        assertEquals(Skins.default.id, ThemePrefs(activeSkinId = null).activeRecord.id)
    }

    @Test
    fun `activeIsBuiltin distinguishes preset from user skin`() {
        val user = SavedSkinRecord(id = "skin-a", name = "Mine")
        assertTrue(ThemePrefs(activeSkinId = "skin-a", skins = listOf(user)).activeIsBuiltin.not())
        assertTrue(ThemePrefs(activeSkinId = Skins.Solid.id).activeIsBuiltin)
        assertTrue(ThemePrefs(activeSkinId = null).activeIsBuiltin)
    }

    @Test
    fun `frosted preset ships a gradient and liquid does not`() {
        assertEquals(SkinBackdrop.None, Skins.Liquid.toSkin().backdrop)
        assertTrue(Skins.Frosted.toSkin().backdrop is SkinBackdrop.ThemedGradient)
        // 内置预设不能自带主题色：那会让它们不再跟随用户的全局配色
        Skins.builtin.forEach { assertNull("${it.id} 不应自带主题色", it.color.ifBlank { null }) }
    }

    @Test
    fun `builtin presets are visually distinct from each other`() {
        // 比整套皮肤而不是只比 token：纯黑与实心的材质参数**完全相同**，
        // 它的区别在 amoled（调色板层）。只比 token 会漏掉这类差异、也会误报。
        val looks = Skins.builtin.map { it.toSkin().copy(id = "") }
        looks.forEachIndexed { i, look ->
            looks.forEachIndexed { j, other ->
                if (i != j) {
                    assertNotEquals(
                        "${Skins.builtin[i].id} 与 ${Skins.builtin[j].id} 观感完全相同",
                        look,
                        other,
                    )
                }
            }
        }
    }
}
