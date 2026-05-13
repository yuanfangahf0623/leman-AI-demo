import request from '@/config/axios'

export interface AiDocumentVO {
  id?: number
  knowledgeBaseId: number
  directoryId?: number
  dataSourceId?: number
  documentVersion?: string
  title: string
  fileName?: string
  fileType?: string
  fileSize?: number
  objectKey?: string
  sourceUri?: string
  contentHash?: string
  parseStatus?: number
  embeddingStatus?: number
  chunkCount?: number
  tokenCount?: number
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface AiDocumentPageReqVO extends PageParam {
  knowledgeBaseId?: number
  directoryId?: number
  parseStatus?: number
  embeddingStatus?: number
  title?: string
}

export interface AiDocumentUpdateReqVO {
  id?: number
  directoryId?: number
  title: string
  documentVersion?: string
}

export const AiDocumentApi = {
  // 查询文档分页
  getDocumentPage: async (params: AiDocumentPageReqVO) => {
    return await request.get<PageResult<AiDocumentVO[]>>({ url: '/ai/document/page', params })
  },

  // 查询文档详情
  getDocument: async (id: number) => {
    return await request.get<AiDocumentVO>({ url: '/ai/document/get?id=' + id })
  },

  // 编辑文档元数据
  updateDocument: async (data: AiDocumentUpdateReqVO) => {
    return await request.put({ url: '/ai/document/update', data })
  },

  // 预览文档原文件
  previewDocument: async (id: number) => {
    return await request.download<Blob>({ url: '/ai/document/preview?id=' + id })
  },

  // 上传文档
  uploadDocument: async (knowledgeBaseId: number, file: File, directoryId?: number) => {
    const formData = new FormData()
    formData.append('knowledgeBaseId', String(knowledgeBaseId))
    if (directoryId && directoryId > 0) {
      formData.append('directoryId', String(directoryId))
    }
    formData.append('file', file)
    return await request.post<number>({
      url: '/ai/document/upload',
      data: formData,
      headersType: 'multipart/form-data'
    })
  },

  // 解析文档
  parseDocument: async (id: number) => {
    return await request.post({ url: '/ai/document/parse?id=' + id })
  },

  // 向量化文档
  embedDocument: async (id: number) => {
    return await request.post({ url: '/ai/document/embed?id=' + id })
  },

  // 删除文档
  deleteDocument: async (id: number) => {
    return await request.delete({ url: '/ai/document/delete?id=' + id })
  }
}
