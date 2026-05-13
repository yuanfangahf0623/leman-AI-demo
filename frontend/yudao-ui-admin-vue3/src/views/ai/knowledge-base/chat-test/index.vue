<template>
  <div class="chat-test-page">
    <aside class="chat-sidebar">
      <div class="sidebar-top">
        <el-button type="primary" class="new-chat-btn" @click="handleNewConversation">
          <Icon icon="ep:plus" class="mr-5px" />
          新建对话
        </el-button>
        <el-select
          v-model="queryParams.knowledgeBaseId"
          placeholder="全部知识库"
          clearable
          filterable
          class="!w-1/1"
          @change="handleKnowledgeFilterChange"
        >
          <el-option
            v-for="item in knowledgeOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>

      <el-scrollbar class="conversation-scroll">
        <el-skeleton v-if="conversationLoading" :rows="8" animated />
        <div v-else-if="conversationList.length === 0" class="conversation-empty">
          暂无历史对话
        </div>
        <div v-else class="conversation-list">
          <div
            v-for="conversation in conversationList"
            :key="conversation.id"
            class="conversation-item"
            :class="{ active: activeConversationId === conversation.id }"
            @click="handleSelectConversation(conversation)"
          >
            <div class="conversation-item-main">
              <div class="conversation-title-row">
                <span class="conversation-title">
                  {{ conversation.title || `会话 ${conversation.id}` }}
                </span>
                <el-tag v-if="conversation.pinned" size="small" type="primary">置顶</el-tag>
              </div>
              <span class="conversation-knowledge">
                {{ getConversationKnowledgeName(conversation) }}
              </span>
              <span class="conversation-time">
                {{ formatDateTime(conversation.lastMessageTime || conversation.createTime) }}
              </span>
            </div>
            <el-dropdown
              trigger="click"
              class="conversation-actions"
              @click.stop
              @command="(command) => handleConversationCommand(command, conversation)"
            >
              <button type="button" class="conversation-more" @click.stop>
                <Icon icon="ep:more-filled" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <Icon icon="ep:edit" class="mr-6px" />
                    重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="pin">
                    <Icon :icon="conversation.pinned ? 'ep:bottom' : 'ep:top'" class="mr-6px" />
                    {{ conversation.pinned ? '取消置顶' : '置顶聊天' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="archive">
                    <Icon icon="ep:folder-checked" class="mr-6px" />
                    归档
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <Icon icon="ep:delete" class="mr-6px" />
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-scrollbar>

      <Pagination
        v-if="conversationTotal > 0"
        :total="conversationTotal"
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        small
        layout="prev, pager, next"
        @pagination="getConversationList"
      />
    </aside>

    <section class="chat-main">
      <header class="chat-header">
        <div class="assistant-heading">
          <div class="assistant-avatar">AI</div>
          <div>
            <div class="assistant-name">理文科技AI助手</div>
            <div class="assistant-desc">
              基于企业知识库回答问题，资料不足时会明确说明无法确认。
            </div>
          </div>
        </div>

        <div class="chat-header-actions">
          <el-select
            v-model="formData.knowledgeBaseId"
            placeholder="全部知识库"
            filterable
            class="knowledge-select"
            @change="handleActiveKnowledgeChange"
          >
            <el-option label="全部知识库" :value="ALL_KNOWLEDGE_BASE_ID" />
            <el-option
              v-for="item in knowledgeOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-tag v-if="activeConversationId" type="info">会话 {{ activeConversationId }}</el-tag>
          <el-tag v-else type="success">新对话</el-tag>
        </div>
      </header>

      <div v-if="currentQuestions.length > 0" class="question-summary">
        <button
          v-for="question in currentQuestions"
          :key="question.id"
          type="button"
          class="question-summary-item"
          :class="{ active: activeQuestionMessageId === question.id }"
          :title="question.content"
          @click="scrollToMessage(question.id)"
        >
          <span class="question-summary-text">{{ question.content }}</span>
          <span class="question-summary-minus">-</span>
        </button>
      </div>

      <el-scrollbar ref="messageScrollRef" class="message-scroll" @scroll="handleMessageScroll">
        <el-skeleton v-if="messageLoading" :rows="8" animated />
        <div v-else-if="messageList.length === 0" class="empty-chat">
          <div class="empty-chat-title">新对话</div>
          <div class="empty-chat-desc">选择知识库后输入问题，助手会返回答案和引用来源。</div>
        </div>
        <div v-else class="message-list">
          <div
            v-for="messageItem in messageList"
            :key="messageItem.id"
            :id="getMessageAnchorId(messageItem.id)"
            class="message-row"
            :class="[messageItem.role, { highlighted: highlightedMessageId === messageItem.id }]"
          >
            <div class="message-avatar">
              {{ isUserMessage(messageItem) ? '你' : 'AI' }}
            </div>
            <div class="message-card">
              <div class="message-meta">
                <span>{{ getRoleLabel(messageItem.role) }}</span>
                <span>{{ formatDateTime(messageItem.createTime) }}</span>
                <template v-if="isAssistantMessage(messageItem)">
                  <span v-if="messageItem.model">模型：{{ messageItem.model }}</span>
                  <span v-if="messageItem.totalTokens !== undefined">
                    Token：{{ messageItem.totalTokens }}
                  </span>
                  <span v-if="messageItem.latencyMs !== undefined">
                    耗时：{{ messageItem.latencyMs }}ms
                  </span>
                </template>
              </div>
              <div class="message-content">
                {{ messageItem.content || '-' }}
              </div>

              <div v-if="isAssistantMessage(messageItem)" class="citation-area">
                <el-button
                  link
                  type="primary"
                  :loading="citationLoadingMap[messageItem.id] === true"
                  @click="handleToggleCitations(messageItem)"
                >
                  {{ isCitationExpanded(messageItem.id) ? '收起引用来源' : '查看引用来源' }}
                </el-button>
                <el-tag
                  v-if="getCitationCount(messageItem.id) !== undefined"
                  size="small"
                  type="success"
                >
                  {{ getCitationCount(messageItem.id) }} 条引用
                </el-tag>

                <div v-if="messageItem.debugInfo" class="debug-area">
                  <el-collapse :model-value="['debug']">
                    <el-collapse-item title="RAG 调试过程" name="debug">
                      <pre class="debug-content">{{ messageItem.debugInfo }}</pre>
                    </el-collapse-item>
                  </el-collapse>
                </div>

                <div v-if="citationMap[messageItem.id]" class="citation-list">
                  <el-empty
                    v-if="citationMap[messageItem.id].length === 0"
                    description="暂无引用来源"
                  />
                  <div
                    v-for="(citation, index) in citationMap[messageItem.id]"
                    :key="`${messageItem.id}-${citation.documentId || citation.chunkId || index}`"
                    class="citation-card"
                  >
                    <div class="citation-title">
                      <div class="citation-doc">
                        <Icon icon="ep:document" class="mr-6px" />
                        <span>{{ citation.documentTitle || '未知文档' }}</span>
                        <span v-if="citation.chunkNo" class="citation-chunk">
                          Chunk {{ citation.chunkNo }}
                        </span>
                      </div>
                      <el-tag size="small" type="success">
                        score: {{ formatScore(citation.score) }}
                      </el-tag>
                    </div>
                    <div class="citation-content">
                      {{ getCitationContent(citation) }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <footer class="chat-composer">
        <el-input
          v-model="formData.question"
          type="textarea"
          maxlength="2000"
          show-word-limit
          resize="none"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="给理文科技AI助手发送消息"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="composer-actions">
          <span>Enter 发送，Shift + Enter 换行</span>
          <el-button type="primary" :loading="loading" @click="handleSend">
            <Icon icon="ep:promotion" class="mr-5px" />
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { AiKnowledgeApi, AiKnowledgeVO } from '@/api/ai/knowledge'
import {
  AiChatCompletionApi,
  AiChatCompletionCitationVO,
  AiChatCompletionRespVO
} from '@/api/ai/chat/completion'
import {
  AiChatCitationVO,
  AiChatConversationVO,
  AiChatMessageVO,
  AiChatRecordApi
} from '@/api/ai/chat/record'

defineOptions({ name: 'AiKnowledgeChatTest' })

type DisplayCitation = Partial<AiChatCitationVO & AiChatCompletionCitationVO> & {
  contentSnapshot?: string
}

type DisplayChatMessage = AiChatMessageVO & {
  debugInfo?: string
}

type ConversationCommand = 'rename' | 'pin' | 'archive' | 'delete'

const FALLBACK_ANSWER = '根据当前知识库资料无法确认'
const ALL_KNOWLEDGE_BASE_ID = 0

const message = useMessage()

const loading = ref(false)
const conversationLoading = ref(false)
const messageLoading = ref(false)
const knowledgeOptions = ref<AiKnowledgeVO[]>([])
const conversationList = ref<AiChatConversationVO[]>([])
const conversationTotal = ref(0)
const messageList = ref<DisplayChatMessage[]>([])
const activeConversationId = ref<number>()
const activeConversation = ref<AiChatConversationVO>()
const messageScrollRef = ref()
const highlightedMessageId = ref<number>()
const activeQuestionMessageId = ref<number>()
const citationMap = reactive<Record<number, DisplayCitation[]>>({})
const citationCacheMap = reactive<Record<number, DisplayCitation[]>>({})
const citationLoadingMap = reactive<Record<number, boolean>>({})
let tempMessageId = -1
let highlightTimer: ReturnType<typeof setTimeout> | undefined
let programmaticScrollTimer: ReturnType<typeof setTimeout> | undefined
let programmaticActiveQuestionId: number | undefined
const PROGRAMMATIC_SCROLL_LOCK_MS = 1200

const queryParams = reactive({
  pageNo: 1,
  pageSize: 12,
  knowledgeBaseId: undefined as number | undefined
})

const formData = reactive({
  knowledgeBaseId: ALL_KNOWLEDGE_BASE_ID as number | undefined,
  question: ''
})

const currentQuestions = computed(() =>
  messageList.value
    .filter((item) => item.role === 'user' && item.content)
    .map((item) => ({
      id: item.id,
      content: item.content
    }))
)

watch(
  currentQuestions,
  (questions) => {
    if (questions.length === 0) {
      activeQuestionMessageId.value = undefined
      return
    }
    if (!questions.some((question) => question.id === activeQuestionMessageId.value)) {
      activeQuestionMessageId.value = questions[0].id
    }
  },
  { immediate: true }
)

const getKnowledgeOptions = async () => {
  const data = await AiKnowledgeApi.getKnowledgePage({
    pageNo: 1,
    pageSize: 100
  })
  knowledgeOptions.value = data.list
}

const getConversationList = async () => {
  conversationLoading.value = true
  try {
    const data = await AiChatRecordApi.getConversationPage(queryParams)
    conversationList.value = data.list
    conversationTotal.value = data.total
    if (data.total === 0 && activeConversationId.value) {
      handleNewConversation(false)
      return
    }
    if (activeConversationId.value) {
      const currentConversation = data.list.find((item) => item.id === activeConversationId.value)
      if (currentConversation) {
        activeConversation.value = currentConversation
      } else {
        handleNewConversation(false)
      }
    }
  } finally {
    conversationLoading.value = false
  }
}

const handleKnowledgeFilterChange = async () => {
  queryParams.pageNo = 1
  handleNewConversation(true)
  await getConversationList()
}

const handleActiveKnowledgeChange = () => {
  if (!activeConversationId.value) {
    return
  }
  handleNewConversation(false)
}

const handleNewConversation = (resetKnowledge = true) => {
  activeConversationId.value = undefined
  activeConversation.value = undefined
  messageList.value = []
  activeQuestionMessageId.value = undefined
  clearCitationState()
  formData.question = ''
  if (resetKnowledge) {
    formData.knowledgeBaseId = resolveDefaultKnowledgeBaseId()
  }
}

const resolveDefaultKnowledgeBaseId = () => {
  return ALL_KNOWLEDGE_BASE_ID
}

const handleSelectConversation = async (conversation: AiChatConversationVO) => {
  activeConversationId.value = conversation.id
  activeConversation.value = conversation
  formData.knowledgeBaseId = conversation.knowledgeBaseId
  formData.question = ''
  clearCitationState()
  await getMessageList(conversation.id)
}

const handleConversationCommand = async (
  command: string | number | object,
  conversation: AiChatConversationVO
) => {
  const action = command as ConversationCommand
  if (action === 'rename') {
    await handleRenameConversation(conversation)
    return
  }
  if (action === 'pin') {
    await handlePinConversation(conversation)
    return
  }
  if (action === 'archive') {
    await handleArchiveConversation(conversation)
    return
  }
  await handleDeleteConversation(conversation)
}

const handleRenameConversation = async (conversation: AiChatConversationVO) => {
  try {
    const result = await message.prompt('请输入新的会话名称', '重命名')
    const title = String(result.value || '').trim()
    if (!title) {
      message.warning('会话名称不能为空')
      return
    }
    if (title.length > 64) {
      message.warning('会话名称不能超过 64 个字符')
      return
    }
    await AiChatRecordApi.renameConversation({
      id: conversation.id,
      title
    })
    message.success('重命名成功')
    await getConversationList()
  } catch {}
}

const handlePinConversation = async (conversation: AiChatConversationVO) => {
  await AiChatRecordApi.pinConversation({
    id: conversation.id,
    pinned: !conversation.pinned
  })
  message.success(conversation.pinned ? '已取消置顶' : '已置顶')
  await getConversationList()
}

const handleArchiveConversation = async (conversation: AiChatConversationVO) => {
  try {
    await message.confirm('确认归档该会话吗？归档后将不再显示在历史对话列表中。')
    await AiChatRecordApi.archiveConversation(conversation.id)
    message.success('归档成功')
    if (activeConversationId.value === conversation.id) {
      handleNewConversation(false)
    }
    await getConversationList()
  } catch {}
}

const handleDeleteConversation = async (conversation: AiChatConversationVO) => {
  try {
    await message.delConfirm()
    await AiChatRecordApi.deleteConversation(conversation.id)
    message.success('删除成功')
    if (activeConversationId.value === conversation.id) {
      handleNewConversation(false)
    }
    await getConversationList()
  } catch {}
}

const getMessageList = async (conversationId: number) => {
  messageLoading.value = true
  try {
    messageList.value = await AiChatRecordApi.getMessageList(conversationId)
    const firstQuestion = currentQuestions.value[0]
    if (firstQuestion) {
      await scrollToMessage(firstQuestion.id, { block: 'start', highlight: false })
    } else {
      await scrollToBottom()
    }
  } finally {
    messageLoading.value = false
  }
}

const handleSend = async () => {
  if (loading.value) return
  const question = formData.question.trim()
  if (formData.knowledgeBaseId === undefined || formData.knowledgeBaseId === null) {
    message.warning('请先选择知识库')
    return
  }
  if (!question) {
    message.warning('请输入问题')
    return
  }

  loading.value = true
  const now = new Date().toISOString()
  const userTempId = tempMessageId--
  const assistantTempId = tempMessageId--
  messageList.value.push({
    id: userTempId,
    conversationId: activeConversationId.value || 0,
    role: 'user',
    content: question,
    createTime: now
  })
  messageList.value.push({
    id: assistantTempId,
    conversationId: activeConversationId.value || 0,
    role: 'assistant',
    content: '正在思考...',
    createTime: now
  })
  formData.question = ''
  await scrollToBottom()

  try {
    const data = await AiChatCompletionApi.completions({
      knowledgeBaseId: formData.knowledgeBaseId,
      conversationId: activeConversationId.value,
      question,
      stream: false
    })
    const responseIds = applyCompletionResponse(data, userTempId, assistantTempId)
    await getConversationList()
    await scrollToMessage(responseIds.assistantMessageId, {
      updateActiveQuestion: false,
      block: 'start',
      highlight: false
    })
  } catch {
    replaceMessage(assistantTempId, {
      content: '发送失败，请稍后重试。'
    })
  } finally {
    loading.value = false
  }
}

const applyCompletionResponse = (
  data: AiChatCompletionRespVO,
  userTempId: number,
  assistantTempId: number
): { userMessageId: number; assistantMessageId: number } => {
  const conversationId = data.conversationId || activeConversationId.value || 0
  activeConversationId.value = data.conversationId
  const userMessageId = data.userMessageId || userTempId
  if (data.userMessageId) {
    replaceMessage(userTempId, {
      id: data.userMessageId,
      conversationId
    })
  }

  const assistantMessageId = data.assistantMessageId || assistantTempId
  replaceMessage(assistantTempId, {
    id: assistantMessageId,
    conversationId,
    content: data.answer || FALLBACK_ANSWER,
    debugInfo: data.debugInfo
  })
  activeQuestionMessageId.value = userMessageId
  citationCacheMap[assistantMessageId] = normalizeCompletionCitations(data.citations || [])
  return { userMessageId, assistantMessageId }
}

const replaceMessage = (id: number, patch: Partial<DisplayChatMessage>) => {
  const index = messageList.value.findIndex((item) => item.id === id)
  if (index === -1) return
  messageList.value[index] = {
    ...messageList.value[index],
    ...patch
  }
}

const normalizeCompletionCitations = (
  citations: AiChatCompletionCitationVO[]
): DisplayCitation[] => {
  return citations.map((citation) => ({
    ...citation,
    contentSnapshot: citation.content || citation.quoteText || ''
  }))
}

const handleToggleCitations = async (messageItem: AiChatMessageVO) => {
  if (citationMap[messageItem.id]) {
    delete citationMap[messageItem.id]
    return
  }
  if (citationCacheMap[messageItem.id]) {
    citationMap[messageItem.id] = citationCacheMap[messageItem.id]
    return
  }
  citationLoadingMap[messageItem.id] = true
  try {
    citationCacheMap[messageItem.id] = await AiChatRecordApi.getCitationList(messageItem.id)
    citationMap[messageItem.id] = citationCacheMap[messageItem.id]
  } finally {
    citationLoadingMap[messageItem.id] = false
  }
}

const clearCitationState = () => {
  Object.keys(citationMap).forEach((key) => delete citationMap[Number(key)])
  Object.keys(citationCacheMap).forEach((key) => delete citationCacheMap[Number(key)])
  Object.keys(citationLoadingMap).forEach((key) => delete citationLoadingMap[Number(key)])
}

const scrollToBottom = async () => {
  await nextTick()
  messageScrollRef.value?.setScrollTop?.(999999)
}

type ScrollToMessageOptions = {
  updateActiveQuestion?: boolean
  block?: ScrollLogicalPosition
  highlight?: boolean
}

const scrollToMessage = async (messageId: number, options: ScrollToMessageOptions = {}) => {
  if (options.updateActiveQuestion !== false) {
    activeQuestionMessageId.value = messageId
    programmaticActiveQuestionId = messageId
    if (programmaticScrollTimer) {
      clearTimeout(programmaticScrollTimer)
    }
    programmaticScrollTimer = setTimeout(() => {
      programmaticActiveQuestionId = undefined
      updateActiveQuestionByViewport()
    }, PROGRAMMATIC_SCROLL_LOCK_MS)
  }
  await nextTick()
  const element = document.getElementById(getMessageAnchorId(messageId))
  if (!element) return
  element.scrollIntoView({ behavior: 'smooth', block: options.block || 'center' })
  if (options.highlight === false) {
    return
  }
  highlightedMessageId.value = messageId
  if (highlightTimer) {
    clearTimeout(highlightTimer)
  }
  highlightTimer = setTimeout(() => {
    if (highlightedMessageId.value === messageId) {
      highlightedMessageId.value = undefined
    }
  }, 1600)
}

const handleMessageScroll = () => {
  if (programmaticActiveQuestionId !== undefined) {
    activeQuestionMessageId.value = programmaticActiveQuestionId
    return
  }
  updateActiveQuestionByViewport()
}

const updateActiveQuestionByViewport = () => {
  const questions = currentQuestions.value
  if (questions.length === 0) {
    activeQuestionMessageId.value = undefined
    return
  }
  const wrap = messageScrollRef.value?.wrapRef as HTMLElement | undefined
  const wrapRect = wrap?.getBoundingClientRect()
  const checkpoint = wrapRect
    ? wrapRect.top + Math.min(180, wrapRect.height * 0.35)
    : window.innerHeight * 0.35
  let selectedId = questions[0].id
  let nearestAboveDistance = Number.MAX_SAFE_INTEGER
  let nearestAnyId = questions[0].id
  let nearestAnyDistance = Number.MAX_SAFE_INTEGER
  for (const question of questions) {
    const element = document.getElementById(getMessageAnchorId(question.id))
    if (!element) continue
    const distance = Math.abs(element.getBoundingClientRect().top - checkpoint)
    if (distance < nearestAnyDistance) {
      nearestAnyDistance = distance
      nearestAnyId = question.id
    }
    const aboveDistance = checkpoint - element.getBoundingClientRect().top
    if (aboveDistance >= 0 && aboveDistance < nearestAboveDistance) {
      nearestAboveDistance = aboveDistance
      selectedId = question.id
    }
  }
  if (nearestAboveDistance === Number.MAX_SAFE_INTEGER) {
    selectedId = nearestAnyId
  }
  activeQuestionMessageId.value = selectedId
}

const getMessageAnchorId = (messageId: number) => {
  return `chat-message-${messageId}`
}

const getKnowledgeName = (knowledgeBaseId?: number) => {
  if (!knowledgeBaseId || knowledgeBaseId === ALL_KNOWLEDGE_BASE_ID) {
    return '全部知识库'
  }
  return (
    knowledgeOptions.value.find((item) => item.id === knowledgeBaseId)?.name ||
    knowledgeBaseId ||
    '-'
  )
}

const getConversationKnowledgeName = (conversation: AiChatConversationVO) => {
  if (conversation.displayKnowledgeBaseName) {
    return conversation.displayKnowledgeBaseName
  }
  if (conversation.displayKnowledgeBaseId) {
    return getKnowledgeName(conversation.displayKnowledgeBaseId)
  }
  return getKnowledgeName(conversation.knowledgeBaseId)
}

const isUserMessage = (messageItem: AiChatMessageVO) => {
  return messageItem.role === 'user'
}

const isAssistantMessage = (messageItem: AiChatMessageVO) => {
  return messageItem.role === 'assistant'
}

const getRoleLabel = (role?: string) => {
  if (role === 'user') return '用户'
  if (role === 'assistant') return '理文科技AI助手'
  if (role === 'system') return '系统'
  return role || '-'
}

const getCitationContent = (citation: DisplayCitation) => {
  return citation.contentSnapshot || citation.content || citation.quoteText || '-'
}

const isCitationExpanded = (messageId: number) => {
  return Boolean(citationMap[messageId])
}

const getCitationCount = (messageId: number) => {
  if (citationMap[messageId]) {
    return citationMap[messageId].length
  }
  if (citationCacheMap[messageId]) {
    return citationCacheMap[messageId].length
  }
  return undefined
}

const formatDateTime = (value?: string) => {
  return value ? formatDate(new Date(value)) : '-'
}

const formatScore = (score?: number | string) => {
  if (score === undefined || score === null || score === '') {
    return '-'
  }
  return Number(score).toFixed(4)
}

onMounted(async () => {
  try {
    await getKnowledgeOptions()
    await getConversationList()
    if (formData.knowledgeBaseId === undefined || formData.knowledgeBaseId === null) {
      formData.knowledgeBaseId = resolveDefaultKnowledgeBaseId()
    }
  } catch {
    message.error('初始化问答测试页面失败')
  }
})

onBeforeUnmount(() => {
  if (highlightTimer) {
    clearTimeout(highlightTimer)
  }
  if (programmaticScrollTimer) {
    clearTimeout(programmaticScrollTimer)
  }
})
</script>

<style scoped>
.chat-test-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  width: 100%;
  height: calc(100vh - 156px);
  min-height: 640px;
}

.chat-sidebar,
.chat-main {
  min-height: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  padding: 14px;
}

.sidebar-top {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.new-chat-btn {
  width: 100%;
}

.conversation-scroll {
  flex: 1;
  min-height: 0;
}

.conversation-empty {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-item {
  position: relative;
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 5px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.16s ease,
    border-color 0.16s ease;
}

.conversation-item:hover {
  background: var(--el-fill-color-light);
}

.conversation-item.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.conversation-item-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  padding-right: 28px;
}

.conversation-title-row {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 6px;
}

.conversation-title {
  display: -webkit-box;
  flex: 1;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.conversation-knowledge,
.conversation-time {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.conversation-item:hover .conversation-actions,
.conversation-item.active .conversation-actions,
.conversation-item:focus-within .conversation-actions {
  opacity: 1;
}

.conversation-more {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.conversation-more:hover {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.chat-main {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 16px 20px;
}

.assistant-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.assistant-avatar,
.message-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  font-weight: 700;
}

.assistant-avatar {
  width: 42px;
  height: 42px;
  background: #14b8a6;
  color: #fff;
}

.assistant-name {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
}

.assistant-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}

.chat-header-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.knowledge-select {
  width: 260px;
}

.question-summary {
  position: absolute;
  top: 50%;
  right: 18px;
  z-index: 5;
  width: 42px;
  max-width: min(360px, calc(100% - 48px));
  overflow: hidden;
  border-radius: 16px;
  background: var(--el-bg-color);
  box-shadow: 0 10px 30px rgb(31 41 55 / 10%);
  padding: 12px 0;
  transform: translateY(-50%);
  transition:
    width 0.2s ease,
    padding 0.2s ease;
}

.question-summary:hover {
  width: min(360px, calc(100% - 48px));
  padding: 16px 18px;
}

.question-summary-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 28px;
  cursor: pointer;
  transition: color 0.16s ease;
}

.question-summary-item + .question-summary-item {
  margin-top: 4px;
}

.question-summary-item.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.question-summary:hover .question-summary-item {
  justify-content: space-between;
  gap: 16px;
  text-align: left;
}

.question-summary-text {
  width: 0;
  min-width: 0;
  overflow: hidden;
  opacity: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: opacity 0.14s ease;
}

.question-summary:hover .question-summary-text {
  width: auto;
  flex: 1;
  opacity: 1;
}

.question-summary-minus {
  flex-shrink: 0;
  color: inherit;
  font-weight: 700;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 20px;
}

.empty-chat {
  display: flex;
  height: 100%;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.empty-chat-title {
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
}

.empty-chat-desc {
  margin-top: 8px;
  font-size: 14px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px 0;
}

.message-row {
  display: flex;
  gap: 12px;
  scroll-margin: 120px;
  transition:
    filter 0.2s ease,
    transform 0.2s ease;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-row.highlighted {
  filter: drop-shadow(0 0 12px rgb(64 158 255 / 30%));
  transform: translateY(-1px);
}

.message-avatar {
  width: 34px;
  height: 34px;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.message-row.assistant .message-avatar {
  background: #14b8a6;
  color: #fff;
}

.message-card {
  max-width: min(760px, calc(100% - 48px));
  border-radius: 8px;
  background: var(--el-fill-color-light);
  padding: 12px 14px;
}

.message-row.user .message-card {
  background: var(--el-color-primary-light-9);
}

.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.message-content {
  white-space: pre-wrap;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 24px;
}

.citation-area {
  margin-top: 10px;
}

.debug-area {
  margin-top: 10px;
}

.debug-content {
  max-height: 360px;
  overflow: auto;
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  padding: 12px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 20px;
  white-space: pre-wrap;
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
}

.citation-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  padding: 10px;
}

.citation-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.citation-doc {
  display: flex;
  min-width: 0;
  align-items: center;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.citation-doc span:first-of-type {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-chunk {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.citation-content {
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 22px;
}

.chat-composer {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 14px 20px 16px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .chat-test-page {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .knowledge-select {
    width: 220px;
  }
}
</style>
