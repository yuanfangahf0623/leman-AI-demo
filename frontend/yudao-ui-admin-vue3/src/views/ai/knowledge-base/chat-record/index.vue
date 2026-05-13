<template>
  <div class="flex gap-20px w-full">
    <ContentWrap class="w-360px shrink-0">
      <div class="mb-14px">
        <div class="text-18px font-600">问答会话</div>
        <div class="mt-6px text-13px text-gray-500">按知识库筛选历史会话。</div>
      </div>

      <el-form ref="queryFormRef" :model="queryParams" label-width="70px">
        <el-form-item label="知识库" prop="knowledgeBaseId">
          <el-select
            v-model="queryParams.knowledgeBaseId"
            placeholder="全部知识库"
            clearable
            filterable
            class="!w-1/1"
            @change="handleKnowledgeChange"
          >
            <el-option
              v-for="item in knowledgeOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-skeleton v-if="conversationLoading" :rows="6" animated />
      <el-empty v-else-if="conversationList.length === 0" description="暂无会话" />
      <div v-else class="flex flex-col gap-10px">
        <div
          v-for="conversation in conversationList"
          :key="conversation.id"
          class="cursor-pointer rounded-4px border border-solid p-12px transition-colors"
          :class="
            activeConversationId === conversation.id
              ? 'border-blue-400 bg-blue-50'
              : 'border-gray-200 hover:bg-gray-50'
          "
          @click="handleSelectConversation(conversation)"
        >
          <div class="mb-8px line-clamp-2 text-14px font-600">
            {{ conversation.title || `会话 ${conversation.id}` }}
          </div>
          <div class="text-12px text-gray-500">
            知识库：{{ getKnowledgeName(conversation.knowledgeBaseId) }}
          </div>
          <div class="mt-4px text-12px text-gray-500">
            {{ formatDateTime(conversation.lastMessageTime || conversation.createTime) }}
          </div>
        </div>
      </div>

      <Pagination
        :total="conversationTotal"
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        small
        layout="prev, pager, next"
        @pagination="getConversationList"
      />
    </ContentWrap>

    <ContentWrap class="min-w-0 flex-1">
      <div class="mb-14px flex items-center justify-between">
        <div>
          <div class="text-18px font-600">消息详情</div>
          <div class="mt-6px text-13px text-gray-500">点击 assistant 消息可查看引用来源。</div>
        </div>
        <el-tag v-if="activeConversationId" type="info">会话 {{ activeConversationId }}</el-tag>
      </div>

      <el-skeleton v-if="messageLoading" :rows="8" animated />
      <el-empty v-else-if="!activeConversationId" description="请选择左侧会话" />
      <el-empty v-else-if="messageList.length === 0" description="暂无消息" />
      <div v-else class="flex flex-col gap-14px">
        <div
          v-for="messageItem in messageList"
          :key="messageItem.id"
          class="rounded-4px border border-solid border-gray-200 p-14px"
        >
          <div class="mb-10px flex items-center justify-between gap-12px">
            <div class="flex items-center gap-8px">
              <el-tag :type="getRoleTagType(messageItem.role)">
                {{ getRoleLabel(messageItem.role) }}
              </el-tag>
              <span class="text-12px text-gray-500">
                {{ formatDateTime(messageItem.createTime) }}
              </span>
            </div>
            <div class="flex items-center gap-10px text-12px text-gray-500">
              <span v-if="messageItem.model">模型：{{ messageItem.model }}</span>
              <span v-if="messageItem.totalTokens !== undefined">Token：{{ messageItem.totalTokens }}</span>
              <span v-if="messageItem.latencyMs !== undefined">耗时：{{ messageItem.latencyMs }}ms</span>
            </div>
          </div>

          <div class="whitespace-pre-wrap rounded-4px bg-gray-50 p-12px text-14px leading-24px">
            {{ messageItem.content || '-' }}
          </div>

          <div
            v-if="isAssistantMessage(messageItem)"
            class="mt-12px flex items-center justify-between"
          >
            <el-button
              link
              type="primary"
              :loading="citationLoading && activeCitationMessageId === messageItem.id"
              @click="handleShowCitations(messageItem)"
            >
              查看引用来源
            </el-button>
            <el-tag v-if="activeCitationMessageId === messageItem.id" size="small" type="success">
              {{ citationList.length }} 条引用
            </el-tag>
          </div>

          <div
            v-if="activeCitationMessageId === messageItem.id"
            class="mt-12px border-t border-solid border-gray-200 pt-12px"
          >
            <el-empty
              v-if="!citationLoading && citationList.length === 0"
              description="暂无引用来源"
            />
            <div v-else class="flex flex-col gap-10px">
              <div
                v-for="citation in citationList"
                :key="citation.id"
                class="rounded-4px border border-solid border-gray-200 p-12px"
              >
                <div class="mb-8px flex items-center justify-between gap-12px">
                  <div class="flex min-w-0 items-center text-14px font-600">
                    <Icon icon="ep:document" class="mr-6px shrink-0" />
                    <span class="truncate">{{ citation.documentTitle || '未知文档' }}</span>
                  </div>
                  <el-tag size="small" type="success">score: {{ formatScore(citation.score) }}</el-tag>
                </div>
                <div class="whitespace-pre-wrap rounded-4px bg-gray-50 p-10px text-13px leading-22px">
                  {{ citation.contentSnapshot || '-' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { AiKnowledgeApi, AiKnowledgeVO } from '@/api/ai/knowledge'
import {
  AiChatCitationVO,
  AiChatConversationVO,
  AiChatMessageVO,
  AiChatRecordApi
} from '@/api/ai/chat/record'

defineOptions({ name: 'AiKnowledgeChatRecord' })

const knowledgeOptions = ref<AiKnowledgeVO[]>([])
const conversationLoading = ref(false)
const messageLoading = ref(false)
const citationLoading = ref(false)
const conversationList = ref<AiChatConversationVO[]>([])
const conversationTotal = ref(0)
const messageList = ref<AiChatMessageVO[]>([])
const citationList = ref<AiChatCitationVO[]>([])
const activeConversationId = ref<number>()
const activeCitationMessageId = ref<number>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  knowledgeBaseId: undefined as number | undefined
})

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
    if (!activeConversationId.value && data.list.length > 0) {
      await handleSelectConversation(data.list[0])
    }
    if (activeConversationId.value && !data.list.some((item) => item.id === activeConversationId.value)) {
      clearMessageState()
    }
  } finally {
    conversationLoading.value = false
  }
}

const handleKnowledgeChange = async () => {
  queryParams.pageNo = 1
  clearMessageState()
  await getConversationList()
}

const handleSelectConversation = async (conversation: AiChatConversationVO) => {
  activeConversationId.value = conversation.id
  activeCitationMessageId.value = undefined
  citationList.value = []
  await getMessageList(conversation.id)
}

const getMessageList = async (conversationId: number) => {
  messageLoading.value = true
  try {
    messageList.value = await AiChatRecordApi.getMessageList(conversationId)
  } finally {
    messageLoading.value = false
  }
}

const handleShowCitations = async (messageItem: AiChatMessageVO) => {
  if (activeCitationMessageId.value === messageItem.id) {
    activeCitationMessageId.value = undefined
    citationList.value = []
    return
  }
  activeCitationMessageId.value = messageItem.id
  citationLoading.value = true
  try {
    citationList.value = await AiChatRecordApi.getCitationList(messageItem.id)
  } finally {
    citationLoading.value = false
  }
}

const clearMessageState = () => {
  activeConversationId.value = undefined
  activeCitationMessageId.value = undefined
  messageList.value = []
  citationList.value = []
}

const getKnowledgeName = (knowledgeBaseId?: number) => {
  return knowledgeOptions.value.find((item) => item.id === knowledgeBaseId)?.name || knowledgeBaseId || '-'
}

const isAssistantMessage = (messageItem: AiChatMessageVO) => {
  return messageItem.role === 'assistant'
}

const getRoleLabel = (role?: string) => {
  if (role === 'user') return '用户'
  if (role === 'assistant') return '助手'
  if (role === 'system') return '系统'
  return role || '-'
}

const getRoleTagType = (role?: string) => {
  if (role === 'user') return 'primary'
  if (role === 'assistant') return 'success'
  if (role === 'system') return 'info'
  return 'info'
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
  await getKnowledgeOptions()
  await getConversationList()
})
</script>
