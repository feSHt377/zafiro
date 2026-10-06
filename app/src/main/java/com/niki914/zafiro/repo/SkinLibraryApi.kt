package com.niki914.zafiro.repo

import com.niki914.store.StoreDescriptorRegistry
import java.util.UUID

/**
 * 皮肤库：用户拼出来的多套皮肤，其中一份生效。
 *
 * 与 `LlmConfigsApi` 同构（active + 列表），因为使用方式一致。几条刻意的约定：
 *
 * - **内置预设不进库**：它们是代码常量，只在解析时参与查找。所以内置项的 id 永远不会
 *   出现在 `document().skins` 里，`activeId` 却可能指向它——判断「是不是用户皮肤」
 *   必须用 `skins.any { }`，不能拿 activeId 直接去查。
 * - **写操作不改 active 归属**，除非调用方显式要求（[insertActive] / [setActive]）。
 *   与配置列表踩过的坑同源：一次保存不该把用户正在用的东西换掉。
 * - **删除生效项不自动选下一个**：回落到内置默认（activeId = null）。替用户猜下一个
 *   等于在用户没看的时候换了皮肤。
 */
class SkinLibraryApi internal constructor(
    private val repo: XRepo,
) {
    suspend fun document(): SkinLibraryDocument =
        SkinLibraryCodec.parse(repo.readJson(StoreDescriptorRegistry.SKIN_LIBRARY_ID))

    suspend fun list(): List<SavedSkinRecord> = document().skins

    /** 生效的**用户**皮肤；null = 生效的是内置预设，或还没选过。 */
    suspend fun activeRecord(): SavedSkinRecord? = document().active()

    suspend fun upsert(record: SavedSkinRecord) {
        repo.updateJson(StoreDescriptorRegistry.SKIN_LIBRARY_ID) { json ->
            val doc = SkinLibraryCodec.parse(json)
            val skins = if (doc.skins.any { it.id == record.id }) {
                doc.skins.map { if (it.id == record.id) record else it }
            } else {
                doc.skins + record
            }
            SkinLibraryCodec.encode(doc.copy(skins = skins))
        }
    }

    /** 追加并置为生效。用于「保存为新皮肤」与内置预设的写时复制。 */
    suspend fun insertActive(record: SavedSkinRecord) {
        repo.updateJson(StoreDescriptorRegistry.SKIN_LIBRARY_ID) { json ->
            val doc = SkinLibraryCodec.parse(json)
            val skins = doc.skins.filterNot { it.id == record.id } + record
            SkinLibraryCodec.encode(SkinLibraryDocument(activeId = record.id, skins = skins))
        }
    }

    suspend fun delete(id: String) {
        repo.updateJson(StoreDescriptorRegistry.SKIN_LIBRARY_ID) { json ->
            val doc = SkinLibraryCodec.parse(json)
            val remaining = doc.skins.filterNot { it.id == id }
            if (remaining.size == doc.skins.size) return@updateJson json
            SkinLibraryCodec.encode(
                SkinLibraryDocument(
                    activeId = doc.activeId?.takeIf { it != id },
                    skins = remaining,
                )
            )
        }
    }

    suspend fun setActive(id: String) {
        repo.updateJson(StoreDescriptorRegistry.SKIN_LIBRARY_ID) { json ->
            val doc = SkinLibraryCodec.parse(json)
            if (doc.activeId == id) return@updateJson json
            SkinLibraryCodec.encode(doc.copy(activeId = id))
        }
    }

    /** 空皮：全默认档，等价于「玻璃」预设。用于「新建」。 */
    fun newBlankRecord(name: String): SavedSkinRecord = SavedSkinRecord(
        id = newSkinId(),
        name = name,
        createdAt = System.currentTimeMillis(),
    )
}

/** 皮肤库的纯逻辑：命名去重与 id 生成。抽出来是为了能脱离 store 单测。 */
internal object SkinLibraryNaming {

    /**
     * 名称去重：`base` 被占用时追加 " 2"、" 3"…
     *
     * 用数字后缀而不是「副本」之类的词：后者要进 5 份 i18n，而数字是各语言通用的，
     * 且「副本」重复三次也不如 `2` / `3` 好读。与配置复制用的是同一套规则。
     */
    fun uniqueName(base: String, taken: Collection<String>): String {
        val trimmed = base.trim().ifBlank { "Skin" }
        val used = taken.mapTo(mutableSetOf()) { it.trim() }
        if (trimmed !in used) return trimmed
        var index = 2
        while ("$trimmed $index" in used) index++
        return "$trimmed $index"
    }

    fun newId(): String = "skin-" + UUID.randomUUID().toString().replace("-", "").take(12)
}

private fun newSkinId(): String = SkinLibraryNaming.newId()
