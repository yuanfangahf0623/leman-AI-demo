import request from '@/config/axios'

export interface DataSourceVO {
  id?: number
  name: string
  code: string
  category?: string
  type: string
  host: string
  port: number
  databaseName: string
  username: string
  password?: string
  passwordConfigured?: boolean
  jdbcParams?: string
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SyncJobVO {
  id?: number
  name: string
  code: string
  sourceDataSourceId: number
  sourceSql: string
  targetDataSourceId: number
  targetDatabase: string
  targetTable: string
  sinkSql: string
  syncMode: 'FULL' | 'INCREMENTAL'
  watermarkColumn?: string
  watermarkValue?: string
  parallelism: number
  status: number
  remark?: string
  createTime?: string
}

export interface JobRunVO {
  id: number
  jobId: number
  jobName: string
  batchId: string
  triggerType: string
  status: string
  startTime?: string
  endTime?: string
  exitCode?: number
  errorMessage?: string
  createTime?: string
}

export interface WarehouseColumnVO {
  name: string
  type: string
  nullable: boolean
  key?: string
  defaultValue?: string
  comment?: string
}

export const DataPlatformApi = {
  getDataSourcePage: (params: any) => request.get({ url: '/data-platform/datasource/page', params }),
  getDataSource: (id: number) => request.get({ url: '/data-platform/datasource/get', params: { id } }),
  createDataSource: (data: DataSourceVO) => request.post({ url: '/data-platform/datasource/create', data }),
  updateDataSource: (data: DataSourceVO) => request.put({ url: '/data-platform/datasource/update', data }),
  deleteDataSource: (id: number) => request.delete({ url: '/data-platform/datasource/delete', params: { id } }),
  testDataSource: (data: DataSourceVO) => request.post({ url: '/data-platform/datasource/test', data }),

  getSyncJobPage: (params: any) => request.get({ url: '/data-platform/sync-job/page', params }),
  getSyncJob: (id: number) => request.get({ url: '/data-platform/sync-job/get', params: { id } }),
  createSyncJob: (data: SyncJobVO) => request.post({ url: '/data-platform/sync-job/create', data }),
  updateSyncJob: (data: SyncJobVO) => request.put({ url: '/data-platform/sync-job/update', data }),
  deleteSyncJob: (id: number) => request.delete({ url: '/data-platform/sync-job/delete', params: { id } }),
  executeSyncJob: (id: number) => request.post({ url: '/data-platform/sync-job/execute', params: { id } }),
  getJobRunPage: (params: any) => request.get({ url: '/data-platform/sync-job/run/page', params }),
  getJobRunLog: (id: number, tailLines = 300) =>
    request.get({ url: '/data-platform/sync-job/run/log', params: { id, tailLines } }),

  getWarehouseHealth: () => request.get({ url: '/data-platform/warehouse/health' }),
  getWarehouseDatabases: () => request.get({ url: '/data-platform/warehouse/database/list' }),
  getWarehouseTables: (database: string) =>
    request.get({ url: '/data-platform/warehouse/table/list', params: { database } }),
  getWarehouseColumns: (database: string, table: string) =>
    request.get({ url: '/data-platform/warehouse/column/list', params: { database, table } })
}
