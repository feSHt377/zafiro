package com.fesht3.zafiro.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤命名去重与 id 生成。
 *
 * 去重规则值得单独测：它决定「另存为」时会不会悄悄覆盖掉一套已经调好的皮肤。
 * 重名不报错而是追加序号，所以一旦算错，用户看到的只是「我的皮肤少了一套」。
 */
class SkinLibraryNamingTest {

    @Test
    fun `unused name passes through unchanged`() {
        assertEquals("我的皮肤", SkinLibraryNaming.uniqueName("我的皮肤", listOf("别的")))
        assertEquals("Mine", SkinLibraryNaming.uniqueName("Mine", emptyList()))
    }

    @Test
    fun `taken name gets numeric suffix`() {
        assertEquals("Mine 2", SkinLibraryNaming.uniqueName("Mine", listOf("Mine")))
        assertEquals(
            "Mine 3",
            SkinLibraryNaming.uniqueName("Mine", listOf("Mine", "Mine 2")),
        )
        // 序号不连续时取第一个空位，而不是最大值 +1
        assertEquals(
            "Mine 2",
            SkinLibraryNaming.uniqueName("Mine", listOf("Mine", "Mine 5")),
        )
    }

    @Test
    fun `comparison trims surrounding whitespace`() {
        // 名称带空格是用户手输入很容易出现的：不能因为空格差异就放行一个视觉重名
        assertEquals("Mine 2", SkinLibraryNaming.uniqueName("Mine", listOf("  Mine  ")))
        assertEquals("Mine", SkinLibraryNaming.uniqueName("  Mine  ", listOf("Other")))
    }

    @Test
    fun `blank name falls back to a placeholder`() {
        // 空白名不能让记录写成空字符串：列表里那一行会变成一片空白，无法辨认
        assertTrue(SkinLibraryNaming.uniqueName("", emptyList()).isNotBlank())
        assertTrue(SkinLibraryNaming.uniqueName("   ", emptyList()).isNotBlank())
    }

    @Test
    fun `generated ids are unique and prefixed`() {
        val ids = List(200) { SkinLibraryNaming.newId() }
        assertEquals("id 必须互不相同", ids.size, ids.toSet().size)
        ids.forEach { assertTrue("id 应带 skin- 前缀：$it", it.startsWith("skin-")) }
    }

    @Test
    fun `generated id cannot collide with the new skin sentinel`() {
        // 工作台用 "__new__" 当「另存为」的对话标识；真实 id 绝不能撞上它
        repeat(50) {
            assertNotEquals("__new__", SkinLibraryNaming.newId())
        }
    }
}
