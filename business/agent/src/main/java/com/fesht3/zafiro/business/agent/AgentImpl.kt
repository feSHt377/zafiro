package com.fesht3.zafiro.business.agent

import com.niki914.logging.Logger
import com.niki914.okia.message.ContentBlock
import com.fesht3.zafiro.api.Agent
import com.fesht3.zafiro.api.Approver
import com.fesht3.zafiro.api.TurnStart
import com.fesht3.zafiro.api.model.AgentState
import com.fesht3.zafiro.api.model.isRunning
import com.fesht3.zafiro.api.model.ApprovalDecision
import com.fesht3.zafiro.api.model.ApprovalRequest
import com.fesht3.zafiro.api.model.Attachment
import com.fesht3.zafiro.api.model.Conversation
import com.fesht3.zafiro.api.model.ConversationId
import com.fesht3.zafiro.api.model.Draft
import com.fesht3.zafiro.api.model.DraftImage
import com.fesht3.zafiro.chat.LLMController
import com.fesht3.zafiro.chat.LlmStreamEvent
import com.fesht3.zafiro.service.requireService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
object AgentImpl : Agent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 持久化端口：由 app 侧实现并在组合根 install。 */
    private fun store(): ConversationStore_Tmp = requireService()

    private const val LOG_TAG = "niki914_zafiro_AgentImpl"

    /** 同态纯文本增长的发射间隔：thinking 全量重发的刷屏主凶压到此粒度。 */
    private const val STATUS_TEXT_THROTTLE_MS = 500L

    /** ingest 管线统一转码，用户附件按 jpeg 声明。 */
    private const val USER_IMAGE_MIME = "image/jpeg"

    internal class SessionContext(
        var id: ConversationId?,
        val conversationFlow: MutableStateFlow<Conversation> = MutableStateFlow(Conversation(id = id)),
        val statusFlow: MutableStateFlow<AgentState> = MutableStateFlow(AgentState.Idle()),
        val draftFlow: MutableStateFlow<Draft> = MutableStateFlow(Draft()),
        var streamJob: Job? = null,
        var roundToken: Int = 0,
        val roundActive: MutableStateFlow<Boolean> = MutableStateFlow(false),
        var reduced: Reduced = Reduced(Conversation(id = id)),
        var reducedStatus: ReducedStatus = AgentStateReducer.reset(),
        var lastStatusEmitMs: Long = 0L,
    )

    private val sessions = ConcurrentHashMap<String, SessionContext>()
    private var unnamedSession = SessionContext(id = null)
    private val currentSessionFlow = MutableStateFlow<SessionContext>(unnamedSession)

    private val activeSessionStatesFlow = MutableStateFlow<Map<String, AgentState>>(emptyMap())
    override val sessionStates: StateFlow<Map<String, AgentState>> = activeSessionStatesFlow.asStateFlow()

    /** 当前在途审批（显示用；结算靠 Approver.decide 返回值，本 flow 只进 status 投影）。 */
    private val approvalFlow = MutableStateFlow<ApprovalRequest?>(null)

    override val conversation: StateFlow<Conversation> =
        currentSessionFlow.flatMapLatest { it.conversationFlow }
            .stateIn(scope, SharingStarted.Eagerly, Conversation())

    override val draft: StateFlow<Draft> =
        currentSessionFlow.flatMapLatest { it.draftFlow }
            .stateIn(scope, SharingStarted.Eagerly, Draft())

    override val status: StateFlow<AgentState> =
        combine(
            currentSessionFlow.flatMapLatest { it.statusFlow },
            approvalFlow
        ) { engine, pending ->
            pending?.let { AgentState.WaitingApproval(it) } ?: engine
        }.stateIn(scope, SharingStarted.Eagerly, AgentState.Idle())

    init {
        // 监听当前前台 session 的草稿 Pending 项，自动转码落盘
        scope.launch {
            currentSessionFlow.flatMapLatest { it.draftFlow }.collect { draft ->
                for (pending in draft.images.filterIsInstance<DraftImage.Pending>()) {
                    ingest(pending)
                }
            }
        }
    }

    private fun currentSession(): SessionContext = currentSessionFlow.value

    private fun updateActiveSessionState(sessionId: String?, state: AgentState) {
        if (sessionId == null) return
        val map = activeSessionStatesFlow.value.toMutableMap()
        if (state is AgentState.Idle && state.lastOutcome == null) {
            map.remove(sessionId)
        } else {
            map[sessionId] = state
        }
        activeSessionStatesFlow.value = map
    }

    override fun updateDraft(transform: (Draft) -> Draft) {
        val session = currentSession()
        session.draftFlow.value = transform(session.draftFlow.value)
    }

    override fun clearDraft() {
        currentSession().draftFlow.value = Draft()
    }

    override fun stream(): TurnStart = stream(currentSession())

    override fun stream(id: ConversationId): TurnStart {
        val session = sessions[id.value] ?: return TurnStart.Busy
        return stream(session)
    }

    private fun stream(session: SessionContext): TurnStart {
        val draft = session.draftFlow.value
        if (draft.text.isBlank() && draft.images.isEmpty() && draft.files.isEmpty()) {
            return TurnStart.DraftEmpty
        }
        if (!session.roundActive.compareAndSet(expect = false, update = true)) {
            return TurnStart.Busy
        }

        val query = draft.text
        val images = draft.images.mapNotNull { image ->
            when (image) {
                is DraftImage.Pending -> null
                is DraftImage.Ready -> image.attachment
            }
        }
        val files = draft.files
        val contentImages = images.map {
            ContentBlock.Image(it.path, it.mimeType ?: USER_IMAGE_MIME)
        }
        session.draftFlow.value = Draft()
        session.reducedStatus = AgentStateReducer.startRound()
        emitStatus(session, session.reducedStatus.status)
        foldWith(session, ConversationReducer.startTurn(session.conversationFlow.value, query, images, files))

        val token = ++session.roundToken
        session.streamJob = scope.launch {
            try {
                val conversationId = ensureConversation(session, query)
                store().saveDraft(conversationId, "")
                Logger.i(LOG_TAG, "round started conversationId=${conversationId.value} queryLength=${query.length}")
                LLMController.stream(
                    query = query,
                    images = contentImages,
                    files = files,
                    sessionId = conversationId.value,
                ).collect { event ->
                    fold(session, event)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Logger.e(LOG_TAG, "round failed errorType=${throwable::class.simpleName} message=${throwable.message}")
                val message = throwable.message?.trim()?.takeIf(String::isNotEmpty)
                if (message != null) fold(session, LlmStreamEvent.Error(message = message))
                else {
                    session.reducedStatus = AgentStateReducer.interrupt(session.reducedStatus)
                    emitStatus(session, session.reducedStatus.status)
                }
            } finally {
                if (session.roundToken == token && session.statusFlow.value !is AgentState.Stopping) {
                    if (session.statusFlow.value.isRunning) {
                        session.reducedStatus = AgentStateReducer.interrupt(session.reducedStatus)
                        emitStatus(session, session.reducedStatus.status)
                    }
                    session.streamJob = null
                    session.roundActive.value = false
                }
            }
        }
        return TurnStart.Started
    }

    override fun stop() {
        stop(currentSession())
    }

    override fun stop(id: ConversationId) {
        val session = sessions[id.value] ?: if (currentSession().id == id) currentSession() else null
        session?.let { stop(it) }
    }

    private fun stop(session: SessionContext) {
        if (!session.roundActive.value || session.statusFlow.value is AgentState.Stopping) return

        foldWith(session, session.reduced.copy(conversation = ConversationReducer.interrupt(session.reduced.conversation)))
        session.reducedStatus = AgentStateReducer.stopping(session.reducedStatus)
        emitStatus(session, session.reducedStatus.status)

        val currentJob = session.streamJob
        val token = session.roundToken
        val sessionId = session.id?.value
        scope.launch {
            try {
                if (sessionId != null) {
                    LLMController.stopSession(sessionId)
                } else {
                    LLMController.stopCurrentRound()
                }
                currentJob?.cancelAndJoin()
            } finally {
                if (session.roundToken == token) {
                    session.reducedStatus = AgentStateReducer.interrupt(session.reducedStatus)
                    emitStatus(session, session.reducedStatus.status)
                    session.streamJob = null
                    session.roundActive.value = false
                }
            }
        }
    }

    override fun discard() {
        // 开新会话：进行中的旧会话在后台继续安全执行，不打断、不强杀
        unnamedSession = SessionContext(id = null)
        currentSessionFlow.value = unnamedSession
        scope.launch {
            runCatching {
                LLMController.createNewSession()
            }.onFailure {
                Logger.w(LOG_TAG, "createNewSession on discard failed: ${it.message}")
            }
        }
    }

    override suspend fun load(id: ConversationId) {
        // 关键重构：切换会话不再强行 stop 旧会话！后台会话继续并行执行并落盘。
        val existing = sessions[id.value]
        if (existing != null) {
            currentSessionFlow.value = existing
            store().setLastOpened(id)
            Logger.i(LOG_TAG, "switched to existing session id=${id.value} isRunning=${existing.roundActive.value}")
            return
        }

        val stored = store().load(id)
        if (stored == null) {
            Logger.w(LOG_TAG, "load skipped notFound id=${id.value}")
            return
        }
        LLMController.openSession(stored.snapshot)
        store().setLastOpened(id)

        val newSession = SessionContext(
            id = id,
            conversationFlow = MutableStateFlow(stored.conversation.copy(id = id)),
            statusFlow = MutableStateFlow(AgentState.Idle()),
            draftFlow = MutableStateFlow(Draft(text = stored.draftText)),
            reduced = Reduced(stored.conversation.copy(id = id)),
            reducedStatus = AgentStateReducer.reset(),
        )
        sessions[id.value] = newSession
        currentSessionFlow.value = newSession
        Logger.i(LOG_TAG, "loaded id=${id.value} turns=${stored.conversation.turns.size}")
    }

    private val approvers = CopyOnWriteArrayList<Approver>()

    override fun addApprover(approver: Approver) {
        if (!approvers.contains(approver)) {
            approvers.add(approver)
        }
    }

    override fun removeApprover(approver: Approver) {
        approvers.remove(approver)
    }

    override suspend fun decideApproval(request: ApprovalRequest): ApprovalDecision = coroutineScope {
        val currentApprovers = approvers.toList()
        if (currentApprovers.isEmpty()) return@coroutineScope ApprovalDecision.Deny

        approvalFlow.value = request
        val deferred = CompletableDeferred<ApprovalDecision>()
        val jobs = currentApprovers.map { approver ->
            launch {
                try {
                    val decision = approver.decide(request)
                    if (decision != ApprovalDecision.Abstain) {
                        deferred.complete(decision)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                }
            }
        }
        val allAbstained = launch {
            jobs.joinAll()
            deferred.complete(ApprovalDecision.Deny)
        }

        try {
            deferred.await()
        } finally {
            approvalFlow.value = null
            jobs.forEach { it.cancel() }
            allAbstained.cancel()
        }
    }

    internal fun clearForTest() {
        sessions.values.forEach { it.streamJob?.cancel() }
        sessions.clear()
        unnamedSession = SessionContext(id = null)
        currentSessionFlow.value = unnamedSession
        activeSessionStatesFlow.value = emptyMap()
        approvers.clear()
        approvalFlow.value = null
    }

    private suspend fun ensureConversation(session: SessionContext, firstUserInput: String): ConversationId {
        session.id?.let { return it }
        val sessionId = ConversationId(LLMController.createNewSession())
        session.id = sessionId
        sessions[sessionId.value] = session
        store().create(sessionId, firstUserInput)
        Logger.i(LOG_TAG, "conversation created id=${sessionId.value}")
        store().setLastOpened(sessionId)
        applySessionId(session, sessionId)
        return sessionId
    }

    internal fun applySessionId(sessionId: ConversationId) {
        applySessionId(currentSession(), sessionId)
    }

    private fun applySessionId(session: SessionContext, sessionId: ConversationId) {
        session.id = sessionId
        session.reduced = session.reduced.copy(conversation = session.reduced.conversation.copy(id = sessionId))
        session.conversationFlow.value = session.reduced.conversation
        sessions[sessionId.value] = session
    }

    private fun fold(session: SessionContext, event: LlmStreamEvent) {
        foldWith(session, ConversationReducer.reduce(session.reduced, event))
        session.reducedStatus = AgentStateReducer.reduce(session.reducedStatus, event)
        publishStatus(session, session.reducedStatus.status, event)
    }

    private fun publishStatus(session: SessionContext, state: AgentState, event: LlmStreamEvent) {
        val direct = when {
            state::class != session.statusFlow.value::class -> true
            event is LlmStreamEvent.Completed || event is LlmStreamEvent.Error -> true
            event is LlmStreamEvent.TextDelta && event.isSegmentStart -> true
            else -> false
        }
        if (direct) {
            session.statusFlow.value = state
            session.lastStatusEmitMs = nowMs()
            updateActiveSessionState(session.id?.value, state)
            return
        }
        if (state is AgentState.Generating || state is AgentState.Thinking) {
            if (nowMs() - session.lastStatusEmitMs >= STATUS_TEXT_THROTTLE_MS) {
                session.statusFlow.value = state
                session.lastStatusEmitMs = nowMs()
                updateActiveSessionState(session.id?.value, state)
            }
            return
        }
        session.statusFlow.value = state
        session.lastStatusEmitMs = nowMs()
        updateActiveSessionState(session.id?.value, state)
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun emitStatus(session: SessionContext, state: AgentState) {
        session.statusFlow.value = state
        session.lastStatusEmitMs = nowMs()
        updateActiveSessionState(session.id?.value, state)
    }

    internal fun foldForTest(event: LlmStreamEvent) {
        fold(currentSession(), event)
    }

    private fun foldWith(session: SessionContext, reduced: Reduced) {
        session.reduced = reduced
        session.conversationFlow.value = reduced.conversation
    }

    private suspend fun ingest(pending: DraftImage.Pending) {
        val ingested = runCatching { LLMController.ingestUserImage(pending.uri) }.getOrNull()
        updateDraft { current ->
            val rest = current.images.filterNot { it is DraftImage.Pending && it.uri == pending.uri }
            val images = if (ingested == null) {
                rest
            } else {
                rest + DraftImage.Ready(Attachment(path = ingested.path, mimeType = USER_IMAGE_MIME))
            }
            current.copy(images = images)
        }
    }
}
