import request from '@/config/axios'

export interface AiChatCompletionReqVO {
  knowledgeBaseId: number
  conversationId?: number
  question: string
  topK?: number
  scoreThreshold?: number
  stream: boolean
}

export interface AiChatCompletionCitationVO {
  knowledgeBaseId?: number
  documentId?: number
  chunkId?: number
  chunkNo?: number
  documentTitle?: string
  score?: number
  content?: string
  quoteText?: string
}

export interface AiChatCompletionRespVO {
  conversationId?: number
  userMessageId?: number
  assistantMessageId?: number
  answer: string
  noContext?: boolean
  debugInfo?: string
  citations: AiChatCompletionCitationVO[]
}

export const AiChatCompletionApi = {
  // 非流式知识库问答
  completions: async (data: AiChatCompletionReqVO) => {
    return await request.post<AiChatCompletionRespVO>({
      url: '/ai/chat/completions',
      data
    })
  }
}
