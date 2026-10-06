package com.fesht3.zafiro.app.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.niki914.logging.Logger
import com.niki914.uikit.base.skin.Skin
import com.fesht3.zafiro.repo.SavedSkinRecord
import com.fesht3.zafiro.repo.SkinLibraryNaming
import com.fesht3.zafiro.repo.XRepo

/** 深浅色模式。 */
enum class ThemeMode(val storageKey: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorageKey(value: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: System
    }
}

/**
 * 主题偏好：深浅模式 + 独立配色 + 皮肤库。
 *
 * 「独立配色」与「皮肤里的主题色」是两回事，不是重复：
 * - [seedColor] 是用户手里的那面调色盘，切换它不影响皮肤选择；
 * - 皮肤若自己拼了主题色（[Skin.color]），渲染时优先于 [seedColor]。
 * 这样内置预设能继续跟随用户的配色选择，而用户拼好的皮肤自带颜色、换皮肤即换色。
 */
data class ThemePrefs(
    val mode: ThemeMode = ThemeMode.Dark,
    /** ARGB int；null = 壁纸动态色。 */
    val seedColor: Int? = 0xFF52DBC9.toInt(),
    /** 生效皮肤 id：内置预设 id 或用户皮肤 id；null = 内置默认。 */
    val activeSkinId: String? = null,
    /** 用户拼出来的皮肤库。 */
    val skins: List<SavedSkinRecord> = emptyList(),
) {
    fun resolveDarkTheme(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    /**
     * 生效的皮肤记录：用户皮肤优先，否则回落内置预设。
     *
     * 必须先查 [skins] 再查内置：内置 id 与用户皮肤 id 理论上可能撞名（用户手改文件），
     * 而用户皮肤是显式选择的结果，优先级更高。
     */
    val activeRecord: SavedSkinRecord
        get() = skins.firstOrNull { it.id == activeSkinId } ?: Skins.findBuiltin(activeSkinId)

    /** 生效的是内置预设（尚未派生）——工作台据此提示「改动会另存一份」。 */
    val activeIsBuiltin: Boolean get() = skins.none { it.id == activeSkinId }

    /** 工作台列表：内置预设在前，用户皮肤在后。 */
    val library: List<SavedSkinRecord> get() = Skins.builtin + skins

    /** 渲染用皮肤。 */
    val skin: Skin get() = activeRecord.toSkin()
}

/**
 * 全局主题状态单一来源：冷启动 [load]，设置页经各 setter 更新内存并落盘
 * （调用方用组合作用域 launch），[com.fesht3.zafiro.app.ui.ZafiroTheme] 据此驱动 BaseTheme。
 *
 * TODO(appstate-reactive): 深浅模式与独立配色仍是手搓的「内存快照 + 写穿 + 手工 load」，
 *  待 XRepo 响应式设置注册表稳定后收编为 ReactiveAppStateField。皮肤库已走独立 store，
 *  不再落在这个模式里。
 */
object ThemeController {
    private const val LOG_TAG = "niki914_zafiro_ThemeController"

    var prefs by mutableStateOf(ThemePrefs())
        private set

    suspend fun load() {
        runCatching {
            val library = XRepo.skinLibrary.document()
            prefs = ThemePrefs(
                mode = ThemeMode.fromStorageKey(XRepo.themeMode()),
                seedColor = XRepo.themeSeedColor().takeIf { it.isNotBlank() }?.toLongOrNull(16)
                    ?.toInt(),
                activeSkinId = library.activeId,
                skins = library.skins,
            )
        }.onFailure {
            Logger.w(LOG_TAG, "load failed ${it.message}")
        }
    }

    suspend fun setMode(mode: ThemeMode) {
        prefs = prefs.copy(mode = mode)
        runCatching { XRepo.setThemeMode(mode.storageKey) }
            .onFailure { Logger.w(LOG_TAG, "persist mode failed ${it.message}") }
    }

    suspend fun setSeedColor(argb: Int?) {
        prefs = prefs.copy(seedColor = argb)
        val hex = argb?.let { "%08X".format(it) } ?: ""
        runCatching { XRepo.setThemeSeedColor(hex) }
            .onFailure { Logger.w(LOG_TAG, "persist seed failed ${it.message}") }
    }

    /** 选一套皮肤。内置与用户皮肤走同一入口——两者只是记录来源不同。 */
    suspend fun selectSkin(id: String) {
        val known = Skins.isBuiltin(id) || prefs.skins.any { it.id == id }
        val resolved = if (known) id else Skins.default.id
        prefs = prefs.copy(activeSkinId = resolved)
        runCatching { XRepo.skinLibrary.setActive(resolved) }
            .onFailure { Logger.w(LOG_TAG, "persist active skin failed ${it.message}") }
        // 库内存快照与落盘对齐（id 非法时上面已回落，这里补一次真实值）
        reloadLibrary()
    }

    /**
     * 改当前生效皮肤的**一个部件**。
     *
     * 生效项是内置预设时先**派生**一份用户皮肤再改：内置是不可变的代码常量，
     * 直接改它等于让所有用同一预设的人（以及回退路径）一起变。
     * 派生名字由 UI 按语言给出（[materializeName]），同名时自动追加序号。
     *
     * 每个部件改动都直接写穿：工作台是「所见即所改」，没有独立的保存动作，
     * 因此也不存在「改了没保存」这个状态。
     */
    suspend fun editActivePart(
        materializeName: String,
        mutator: (SavedSkinRecord) -> SavedSkinRecord,
    ) {
        val currentUserRecord = prefs.skins.firstOrNull { it.id == prefs.activeSkinId }
        val target = if (currentUserRecord != null) {
            mutator(currentUserRecord)
        } else {
            val document = XRepo.skinLibrary.document()
            val forked = prefs.activeRecord.copy(
                id = SkinLibraryNaming.newId(),
                name = SkinLibraryNaming.uniqueName(
                    base = materializeName,
                    taken = document.skins.map { it.name },
                ),
                createdAt = System.currentTimeMillis(),
            )
            mutator(forked)
        }
        runCatching { XRepo.skinLibrary.insertActive(target) }
            .onFailure { Logger.w(LOG_TAG, "persist skin part failed ${it.message}") }
        reloadLibrary()
        // 换图后旧文件可能已无人引用：这里顺手清掉，否则用户每换一次背景就留一份垃圾
        cleanOrphanImages()
    }

    /**
     * 把当前生效的组合另存为一条新的用户皮肤并生效。
     *
     * 与 [editActivePart] 的派生区别：这是显式动作，名字由用户给；
     * 派生是隐式的，名字由 [materializeName] 推出来。
     */
    suspend fun saveAsNew(name: String) {
        val document = XRepo.skinLibrary.document()
        val record = prefs.activeRecord.copy(
            id = SkinLibraryNaming.newId(),
            name = SkinLibraryNaming.uniqueName(name, document.skins.map { it.name }),
            createdAt = System.currentTimeMillis(),
        )
        runCatching { XRepo.skinLibrary.insertActive(record) }
            .onFailure { Logger.w(LOG_TAG, "save new skin failed ${it.message}") }
        reloadLibrary()
    }

    suspend fun renameSkin(id: String, name: String) {
        val record = prefs.skins.firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == record.name) return
        val unique = SkinLibraryNaming.uniqueName(
            base = trimmed,
            taken = prefs.skins.filterNot { it.id == id }.map { it.name },
        )
        runCatching { XRepo.skinLibrary.upsert(record.copy(name = unique)) }
            .onFailure { Logger.w(LOG_TAG, "rename skin failed ${it.message}") }
        reloadLibrary()
    }

    /** 删除用户皮肤。删的是生效项时回落内置默认，不替用户猜下一个。 */
    suspend fun deleteSkin(id: String) {
        if (Skins.isBuiltin(id)) return
        runCatching { XRepo.skinLibrary.delete(id) }
            .onFailure { Logger.w(LOG_TAG, "delete skin failed ${it.message}") }
        reloadLibrary()
        // 这套皮肤的图可能还被别的皮肤引用着（内容寻址会去重），必须按并集判断
        cleanOrphanImages()
    }

    /** 删除不再被任何皮肤引用的图片文件。内置预设不含图片，故只看用户皮肤。 */
    private suspend fun cleanOrphanImages() {
        runCatching {
            XRepo.skinImages.deleteUnreferenced(
                prefs.skins.flatMapTo(mutableSetOf()) { it.referencedImagePaths() }
            )
        }.onFailure { Logger.w(LOG_TAG, "clean orphan skin images failed ${it.message}") }
    }

    private suspend fun reloadLibrary() {
        runCatching {
            val document = XRepo.skinLibrary.document()
            prefs = prefs.copy(activeSkinId = document.activeId, skins = document.skins)
        }.onFailure { Logger.w(LOG_TAG, "reload skin library failed ${it.message}") }
    }
}
