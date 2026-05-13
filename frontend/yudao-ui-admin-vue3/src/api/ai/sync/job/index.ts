import request from '@/config/axios'

export interface AiSyncJobCreateReqVO {
  knowledgeBaseId: number
  dataSourceId: number
  jobType: string
}

export interface AiSyncJobRecentVO extends AiSyncJobCreateReqVO {
  id: number
  status?: number
  createTime?: string
  executeTime?: string
  errorMessage?: string
}

export const AiSyncJobApi = {
  // 创建同步任务
  createSyncJob: async (data: AiSyncJobCreateReqVO) => {
    return await request.post<number>({ url: '/ai/sync/job/create', data })
  },

  // 执行同步任务
  executeSyncJob: async (id: number) => {
    return await request.post<boolean>({ url: '/ai/sync/job/execute?id=' + id })
  }
}
