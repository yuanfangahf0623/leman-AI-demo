import request from '@/config/axios'

export interface AiChatConversationVO {
  id: number
  knowledgeBaseId: number
  displayKnowledgeBaseId?: number
  displayKnowledgeBaseName?: string
  userId?: number
  departmentId?: number
  title?: string
  status?: number
  pinned?: boolean
  pinnedTime?: string
  lastMessageTime?: string
  createTime?: string
}

export interface AiChatConversationPageReqVO extends PageParam {
  knowledgeBaseId?: number
}

export interface AiChatConversationRenameReqVO {
  id: number
  title: string
}

export interface AiChatConversationPinReqVO {
  id: number
  pinned: boolean
}

export interface AiChatMessageVO {
  id: number
  conversationId: number
  role: string
  content: string
  model?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
  status?: number
  errorMessage?: string
  createTime?: string
}

export interface AiChatCitationVO {
  id: number
  messageId: number
  knowledgeBaseId: number
  documentId?: number
  chunkId?: number
  documentTitle?: string
  score?: number | string
  sortOrder?: number
  contentSnapshot?: string
  createTime?: string
}

export const AiChatRecordApi = {
  // 查询问答会话分页
  getConversationPage: async (params: AiChatConversationPageReqVO) => {
    return await request.get<PageResult<AiChatConversationVO[]>>({
      url: '/ai/chat/conversation/page',
      params
    })
  },

  // 重命名问答会话
  renameConversation: async (data: AiChatConversationRenameReqVO) => {
    return await request.put<boolean>({
      url: '/ai/chat/conversation/rename',
      data
    })
  },

  // 置顶或取消置顶问答会话
  pinConversation: async (data: AiChatConversationPinReqVO) => {
    return await request.put<boolean>({
      url: '/ai/chat/conversation/pin',
      data
    })
  },

  // 归档问答会话
  archiveConversation: async (id: number) => {
    return await request.put<boolean>({
      url: '/ai/chat/conversation/archive?id=' + id
    })
  },

  // 删除问答会话
  deleteConversation: async (id: number) => {
    return await request.delete<boolean>({
      url: '/ai/chat/conversation/delete?id=' + id
    })
  },

  // 查询会话消息列表
  getMessageList: async (conversationId: number) => {
    return await request.get<AiChatMessageVO[]>({
      url: '/ai/chat/message/list?conversationId=' + conversationId
    })
  },

  // 查询消息引用来源
  getCitationList: async (messageId: number) => {
    return await request.get<AiChatCitationVO[]>({
      url: '/ai/chat/citation/list?messageId=' + messageId
    })
  }
}
