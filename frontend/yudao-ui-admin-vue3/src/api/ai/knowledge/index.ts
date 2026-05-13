import request from '@/config/axios'

export interface AiKnowledgeVO {
  id?: number
  name: string
  code: string
  description?: string
  status?: number
  departmentIds?: string
  visibility?: string
  embeddingModel?: string
  chatModel?: string
  vectorStoreType: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  scoreThreshold?: number
  documentCount?: number
  chunkCount?: number
  createTime?: string
  updateTime?: string
}

export interface AiKnowledgePageReqVO extends PageParam {
  name?: string
  code?: string
  status?: number
  vectorStoreType?: string
}

export interface AiKnowledgeDirectoryVO {
  id?: number
  knowledgeBaseId: number
  parentId?: number
  name: string
  sort?: number
  status?: number
  createTime?: string
  updateTime?: string
  children?: AiKnowledgeDirectoryVO[]
}

export interface AiKnowledgeDirectoryListReqVO {
  knowledgeBaseId: number
}

export const AiKnowledgeApi = {
  // 查询知识库分页
  getKnowledgePage: async (params: AiKnowledgePageReqVO) => {
    return await request.get<PageResult<AiKnowledgeVO[]>>({ url: '/ai/knowledge/page', params })
  },

  // 查询知识库详情
  getKnowledge: async (id: number) => {
    return await request.get<AiKnowledgeVO>({ url: '/ai/knowledge/get?id=' + id })
  },

  // 新增知识库
  createKnowledge: async (data: AiKnowledgeVO) => {
    return await request.post({ url: '/ai/knowledge/create', data })
  },

  // 修改知识库
  updateKnowledge: async (data: AiKnowledgeVO) => {
    return await request.put({ url: '/ai/knowledge/update', data })
  },

  // 删除知识库
  deleteKnowledge: async (id: number) => {
    return await request.delete({ url: '/ai/knowledge/delete?id=' + id })
  }
}

export const AiKnowledgeDirectoryApi = {
  // 查询知识库目录列表
  getDirectoryList: async (params: AiKnowledgeDirectoryListReqVO) => {
    return await request.get<AiKnowledgeDirectoryVO[]>({ url: '/ai/knowledge-directory/list', params })
  },

  // 查询知识库目录详情
  getDirectory: async (id: number) => {
    return await request.get<AiKnowledgeDirectoryVO>({ url: '/ai/knowledge-directory/get?id=' + id })
  },

  // 新增知识库目录
  createDirectory: async (data: AiKnowledgeDirectoryVO) => {
    return await request.post({ url: '/ai/knowledge-directory/create', data })
  },

  // 修改知识库目录
  updateDirectory: async (data: AiKnowledgeDirectoryVO) => {
    return await request.put({ url: '/ai/knowledge-directory/update', data })
  },

  // 删除知识库目录
  deleteDirectory: async (id: number) => {
    return await request.delete({ url: '/ai/knowledge-directory/delete?id=' + id })
  }
}
