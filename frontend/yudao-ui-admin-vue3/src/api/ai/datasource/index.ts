import request from '@/config/axios'

export interface AiDataSourceVO {
  id?: number
  knowledgeBaseId: number
  name: string
  sourceType: string
  syncMode: string
  configJson?: string
  syncEnabled?: boolean
  lastSyncTime?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface AiDataSourcePageReqVO extends PageParam {
  knowledgeBaseId?: number
  name?: string
  sourceType?: string
  syncMode?: string
  status?: number
}

export interface AiDataSourceRawRecordVO {
  id: number
  knowledgeBaseId: number
  dataSourceId: number
  syncJobId?: number
  provider: string
  moduleName: string
  objectType: string
  externalId: string
  sourceUri?: string
  payloadJson: string
  payloadHash: string
  recordTime?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface AiDataSourceRawRecordPageReqVO extends PageParam {
  knowledgeBaseId?: number
  dataSourceId?: number
  syncJobId?: number
  provider?: string
  moduleName?: string
  objectType?: string
  externalId?: string
}

export const AiDataSourceApi = {
  // 查询数据源分页
  getDataSourcePage: async (params: AiDataSourcePageReqVO) => {
    return await request.get<PageResult<AiDataSourceVO[]>>({ url: '/ai/datasource/page', params })
  },

  // 查询数据源详情
  getDataSource: async (id: number) => {
    return await request.get<AiDataSourceVO>({ url: '/ai/datasource/get?id=' + id })
  },

  // 新增数据源
  createDataSource: async (data: AiDataSourceVO) => {
    return await request.post<number>({ url: '/ai/datasource/create', data })
  },

  // 修改数据源
  updateDataSource: async (data: AiDataSourceVO) => {
    return await request.put({ url: '/ai/datasource/update', data })
  },

  // 删除数据源
  deleteDataSource: async (id: number) => {
    return await request.delete({ url: '/ai/datasource/delete?id=' + id })
  },

  // 查询数据源原始记录，后端返回值已经脱敏
  getRawRecordPage: async (params: AiDataSourceRawRecordPageReqVO) => {
    return await request.get<PageResult<AiDataSourceRawRecordVO[]>>({
      url: '/ai/datasource/raw-record/page',
      params
    })
  }
}
