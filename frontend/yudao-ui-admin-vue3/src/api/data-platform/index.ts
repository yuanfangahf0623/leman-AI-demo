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
  fieldMappings?: SyncFieldMappingVO[]
  syncMode: 'FULL' | 'INCREMENTAL'
  watermarkColumn?: string
  watermarkValue?: string
  parallelism: number
  status: number
  remark?: string
  createTime?: string
}

export interface SyncFieldMappingVO {
  sourceField: string
  sourceType?: string
  targetField: string
  targetType?: string
  enabled: boolean
  required?: boolean
  defaultValue?: string
  transform: 'NONE' | 'TRIM' | 'HEX'
}

export interface DataSourceColumnVO {
  name: string
  label?: string
  type: string
  jdbcType: number
  nullable: boolean
  ordinal: number
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

export interface MetadataTableVO {
  id: number
  dataSourceId: number
  dataSourceName?: string
  sourceSchema: string
  sourceTable: string
  businessName?: string
  businessDomain?: string
  description?: string
  targetDatabase?: string
  targetTable?: string
  fieldCount: number
  commentedFieldCount: number
  confirmedFieldCount: number
  definitionStatus: 'GENERATED' | 'CONFIRMED'
  lastScanTime?: string
}

export interface MetadataFieldVO {
  id: number
  metadataTableId: number
  sourceColumn: string
  targetColumn?: string
  dataType: string
  jdbcType?: number
  columnSize?: number
  decimalDigits?: number
  nullable: boolean
  primaryKey: boolean
  ordinalPosition: number
  sourceComment?: string
  businessName?: string
  description?: string
  classification?: string
  sensitivityLevel: 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'RESTRICTED'
  incrementalCandidate: boolean
  definitionStatus: 'GENERATED' | 'CONFIRMED'
  lastScanTime?: string
}

export interface MetadataSummaryVO {
  tableCount: number
  fieldCount: number
  sourceCommentCount: number
  namedCount: number
  describedCount: number
  confirmedCount: number
  sensitiveCount: number
  confirmedCoverage: number
  descriptionCoverage: number
}

export const DataPlatformApi = {
  getDataSourcePage: (params: any) => request.get({ url: '/data-platform/datasource/page', params }),
  getDataSource: (id: number) => request.get({ url: '/data-platform/datasource/get', params: { id } }),
  createDataSource: (data: DataSourceVO) => request.post({ url: '/data-platform/datasource/create', data }),
  updateDataSource: (data: DataSourceVO) => request.put({ url: '/data-platform/datasource/update', data }),
  deleteDataSource: (id: number) => request.delete({ url: '/data-platform/datasource/delete', params: { id } }),
  testDataSource: (data: DataSourceVO) => request.post({ url: '/data-platform/datasource/test', data }),
  getDataSourceQueryColumns: (dataSourceId: number, sourceSql: string) =>
    request.post({ url: '/data-platform/datasource/metadata/columns', data: { dataSourceId, sourceSql } }),

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
    request.get({ url: '/data-platform/warehouse/column/list', params: { database, table } }),

  getMetadataTablePage: (params: any) =>
    request.get({ url: '/data-platform/data-dictionary/table/page', params }),
  getMetadataFieldPage: (params: any) =>
    request.get({ url: '/data-platform/data-dictionary/field/page', params }),
  getMetadataSummary: (dataSourceId: number) =>
    request.get({ url: '/data-platform/data-dictionary/summary', params: { dataSourceId } }),
  refreshMetadata: (dataSourceId: number) =>
    request.post({ url: '/data-platform/data-dictionary/refresh', params: { dataSourceId } }),
  updateMetadataTable: (data: Pick<MetadataTableVO, 'id' | 'businessName' | 'businessDomain' | 'description'>) =>
    request.put({ url: '/data-platform/data-dictionary/table/update', data }),
  updateMetadataField: (data: Pick<MetadataFieldVO, 'id' | 'businessName' | 'description' | 'classification' | 'sensitivityLevel' | 'incrementalCandidate'>) =>
    request.put({ url: '/data-platform/data-dictionary/field/update', data })
}
