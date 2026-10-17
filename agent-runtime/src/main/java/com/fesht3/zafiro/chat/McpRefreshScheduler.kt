package com.fesht3.zafiro.chat

import com.niki914.logging.Logger
import com.niki914.okia.Okia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

/**
 * MCP 后台刷新调度（问题 4 修复）：签名状态机 + 失败退避重试。
 *
 * 两个入口：[awaitFirstRound]（同步，首轮用，保证本轮请求就带 MCP 工具，超时
 * 即退回后台）与 [schedule]（后台，不阻塞回合，失败退避重试）。
 *
 * 签名三态分离：
 * - desired = 当前配置想要（schedule 每次更新）
 * - success = 全部目标服务器成功刷新过的签名。异常（撞活跃回合 / 实例已
 *   关闭 / 网络）与部分失败（McpRefreshResult.failedServers 非空）都不更新
 *   → 下次 schedule 重新触发，失败不再被永久跳过
 * - inFlight 防重：同一时刻至多一个后台刷新；in-flight 期间配置变化不吞——
 *   刷新完成后协程检查 desired，继续刷最新签名
 *
 * 失败语义：有上限退避（撞回合短退避，网络/部分失败指数退避），达上限放弃
 * 本轮，等下一次 schedule（每次 send 前的 refresh）重新触发。
 * 重试为全量（能力边界在 OKIA 库 API：refreshMcpTools 无按服务器刷入口；
 * 服务器级跳过留库侧，见 ISSUES_okia-integration.md）。
 */
internal class McpRefreshScheduler(private val scope: CoroutineScope) {

    private val logTag = "niki914_zafiro_McpRefreshScheduler"

    @Volatile
    private var desiredSignature: String? = null

    @Volatile
    private var successSignature: String? = null

    private val inFlight = AtomicBoolean(false)

    /** 已同步等待过一次的签名：同一配置只阻塞首字一次，避免每轮拖慢发送。 */
    @Volatile
    private var awaitedSignature: String? = null

    /**
     * 首轮就绪：该签名还没同步尝试过时，就地刷一次（有超时），让**本轮**请求
     * 的 tools 里就有 MCP 工具。
     *
     * 后台 [schedule] 不 await，请求在工具注册前就构造完了，首条消息必然拿不到
     * MCP 工具。这里补上同步路径，但只补一次：超时/失败都吞掉退回后台退避
     * （不在此重试、不上抛），所以服务器配错时只多一次首字延迟，不会每轮都等。
     *
     * @return true = 工具已就绪（本轮可用）
     */
    suspend fun awaitFirstRound(session: Okia, signature: String, timeoutMs: Long): Boolean {
        desiredSignature = signature
        if (signature == successSignature) return true
        if (awaitedSignature == signature) return false
        // 后台已在刷：这次不重复连，也不占用「只等一次」的名额（下一轮还能等）
        if (!inFlight.compareAndSet(false, true)) return false
        awaitedSignature = signature
        val startedAtMs = System.currentTimeMillis()
        val result = try {
            withTimeoutOrNull(timeoutMs) { session.refreshMcpTools() }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Logger.e(
                logTag,
                "mcp first round failed elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                        "errorType=${throwable::class.simpleName} message=${throwable.message}"
            )
            null
        } finally {
            inFlight.set(false)
        }
        if (result == null || result.failedServers.isNotEmpty()) {
            Logger.e(
                logTag,
                "mcp first round not ready elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                        "timedOut=${result == null} " +
                        "failed=${result?.failedServers?.joinToString(",") { "\"$it\"" }.orEmpty()}"
            )
            return false
        }
        successSignature = signature
        Logger.i(
            logTag,
            "mcp first round done elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                    "refreshed=${result.refreshedServers.joinToString(",") { "\"$it\"" }}"
        )
        return true
    }

    /** 签名变化才起后台刷新（不 await，不阻塞回合）；force = 跳过签名去重
     *  强制刷一次（会话切换预热，#switch-refresh）。in-flight 仍去重。 */
    fun schedule(session: Okia, signature: String, force: Boolean = false) {
        desiredSignature = signature
        if (!force && signature == successSignature) return
        if (inFlight.get()) return
        launchRefresh(session, signature)
    }

    fun reset() {
        desiredSignature = null
        successSignature = null
        awaitedSignature = null
        inFlight.set(false)
    }

    private fun launchRefresh(session: Okia, signature: String) {
        if (!inFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                var attempt = 0
                var currentSession = session
                var currentSignature = signature
                while (true) {
                    val startedAtMs = System.currentTimeMillis()
                    val result = try {
                        currentSession.refreshMcpTools()
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        attempt++
                        Logger.e(
                            logTag,
                            "mcp refresh failed attempt=$attempt " +
                                    "elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                                    "errorType=${throwable::class.simpleName} message=${throwable.message}"
                        )
                        val backoffMs = backoffMs(attempt, throwable)
                        if (backoffMs == null) break // 达上限 / 不可恢复：等下次 schedule 重试
                        delay(backoffMs)
                        null
                    }
                    if (result != null) {
                        if (result.failedServers.isEmpty()) {
                            successSignature = currentSignature
                            Logger.i(
                                logTag,
                                "mcp refresh done elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                                        "refreshed=${result.refreshedServers.joinToString(",") { "\"$it\"" }}"
                            )
                            attempt = 0
                        } else {
                            // 部分服务器失败：不算成功（否则失败服务器被永久跳过，
                            // 问题 4 原状），退避后全量重试
                            attempt++
                            Logger.e(
                                logTag,
                                "mcp refresh partial failed attempt=$attempt " +
                                        "elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                                        "failed=${result.failedServers.joinToString(",") { "\"$it\"" }}"
                            )
                            val backoffMs = backoffMs(attempt, null)
                            if (backoffMs == null) break
                            delay(backoffMs)
                        }
                    }
                    // 配置在 in-flight 期间变化 → 继续刷最新 desired（不吞）；
                    // 成功覆盖最新配置 → 结束。
                    val latestDesired = desiredSignature
                    if (latestDesired != null && latestDesired == successSignature) break
                    if (latestDesired != null) currentSignature = latestDesired
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun backoffMs(attempt: Int, throwable: Throwable?): Long? {
        val message = throwable?.message.orEmpty()
        return when {
            message.contains("closed") -> null
            message.contains("active turn") -> when (attempt) {
                1 -> 500L
                2 -> 500L
                3 -> 1_000L
                else -> null
            }

            else -> when (attempt) {
                1 -> 1_000L
                2 -> 3_000L
                else -> null
            }
        }
    }
}
