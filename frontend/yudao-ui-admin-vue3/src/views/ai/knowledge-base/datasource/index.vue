<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      class="-mb-15px"
      :model="queryParams"
      :inline="true"
      label-width="90px"
    >
      <el-form-item label="所属知识库" prop="knowledgeBaseId">
        <el-select
          v-model="queryParams.knowledgeBaseId"
          placeholder="请选择知识库"
          clearable
          filterable
          class="!w-240px"
        >
          <el-option
            v-for="item in knowledgeOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="数据源名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入数据源名称"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="数据源类型" prop="sourceType">
        <el-select
          v-model="queryParams.sourceType"
          placeholder="请选择类型"
          clearable
          class="!w-180px"
        >
          <el-option
            v-for="item in sourceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="同步模式" prop="syncMode">
        <el-select
          v-model="queryParams.syncMode"
          placeholder="请选择模式"
          clearable
          class="!w-180px"
        >
          <el-option
            v-for="item in syncModeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['ai:datasource:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="编号" align="center" prop="id" width="90" />
      <el-table-column label="所属知识库" align="center" min-width="150">
        <template #default="scope">
          {{ getKnowledgeName(scope.row.knowledgeBaseId) }}
        </template>
      </el-table-column>
      <el-table-column label="数据源名称" align="center" prop="name" min-width="160" />
      <el-table-column label="数据源类型" align="center" width="120">
        <template #default="scope">
          <el-tag>{{ getSourceTypeLabel(scope.row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="同步模式" align="center" width="120">
        <template #default="scope">
          {{ getSyncModeLabel(scope.row.syncMode) }}
        </template>
      </el-table-column>
      <el-table-column label="启用同步" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.syncEnabled ? 'success' : 'info'">
            {{ scope.row.syncEnabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="getStatusTagType(scope.row.status)">
            {{ getStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配置 JSON" align="center" prop="configJson" min-width="220" />
      <el-table-column
        label="最近同步时间"
        align="center"
        prop="lastSyncTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" fixed="right" width="220">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openRawRecordDialog(scope.row)"
            v-hasPermi="['ai:datasource:query']"
          >
            原始数据
          </el-button>
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['ai:datasource:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:datasource:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <DataSourceForm
    ref="formRef"
    :knowledge-options="knowledgeOptions"
    @success="handleFormSuccess"
  />

  <el-dialog v-model="rawRecordDialogVisible" title="接口原始数据（已脱敏）" width="80%" top="5vh">
    <el-table
      v-loading="rawRecordLoading"
      :data="rawRecordList"
      :stripe="true"
      :show-overflow-tooltip="true"
      max-height="560"
    >
      <el-table-column label="模块" align="center" prop="moduleName" width="120" />
      <el-table-column label="对象类型" align="center" prop="objectType" min-width="180" />
      <el-table-column label="外部编号" align="center" prop="externalId" min-width="180" />
      <el-table-column label="同步任务" align="center" prop="syncJobId" width="110" />
      <el-table-column
        label="记录时间"
        align="center"
        prop="recordTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="脱敏 JSON" min-width="420">
        <template #default="scope">
          <pre class="raw-record-json">{{ formatMaskedPayload(scope.row.payloadJson) }}</pre>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="rawRecordTotal"
      v-model:page="rawRecordQuery.pageNo"
      v-model:limit="rawRecordQuery.pageSize"
      @pagination="getRawRecordList"
    />
  </el-dialog>
</template>

<script lang="ts" setup>
import { CommonStatusEnum } from '@/utils/constants'
import { dateFormatter } from '@/utils/formatTime'
import { AiKnowledgeApi, AiKnowledgeVO } from '@/api/ai/knowledge'
import {
  AiDataSourceApi,
  AiDataSourcePageReqVO,
  AiDataSourceRawRecordVO,
  AiDataSourceVO
} from '@/api/ai/datasource'
import DataSourceForm from './DataSourceForm.vue'

defineOptions({ name: 'AiKnowledgeDataSourceManage' })

const sourceTypeOptions = [
  { label: '文件', value: 'FILE' },
  { label: '数据库', value: 'DATABASE' },
  { label: 'API', value: 'API' },
  { label: 'Wiki', value: 'WIKI' },
  { label: 'Git', value: 'GIT' }
]
const syncModeOptions = [
  { label: '手动同步', value: 'MANUAL' },
  { label: '定时同步', value: 'SCHEDULED' },
  { label: '增量同步', value: 'INCREMENTAL' }
]
const statusOptions = [
  { label: '开启', value: CommonStatusEnum.ENABLE, type: 'success' },
  { label: '停用', value: CommonStatusEnum.DISABLE, type: 'info' }
] as const

const route = useRoute()
const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const list = ref<AiDataSourceVO[]>([])
const total = ref(0)
const knowledgeOptions = ref<AiKnowledgeVO[]>([])
const rawRecordDialogVisible = ref(false)
const rawRecordLoading = ref(false)
const rawRecordList = ref<AiDataSourceRawRecordVO[]>([])
const rawRecordTotal = ref(0)
const rawRecordQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  dataSourceId: undefined as number | undefined
})
const queryParams = reactive<AiDataSourcePageReqVO>({
  pageNo: 1,
  pageSize: 10,
  knowledgeBaseId: undefined,
  name: undefined,
  sourceType: undefined,
  syncMode: undefined,
  status: undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await AiDataSourceApi.getDataSourcePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const getKnowledgeOptions = async () => {
  const data = await AiKnowledgeApi.getKnowledgePage({
    pageNo: 1,
    pageSize: 100
  })
  knowledgeOptions.value = data.list
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id, queryParams.knowledgeBaseId)
}

const handleFormSuccess = async () => {
  queryParams.pageNo = 1
  await getList()
}

const handleDelete = async (id?: number) => {
  if (!id) return
  try {
    await message.delConfirm()
    await AiDataSourceApi.deleteDataSource(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const openRawRecordDialog = async (row: AiDataSourceVO) => {
  rawRecordQuery.pageNo = 1
  rawRecordQuery.dataSourceId = row.id
  rawRecordDialogVisible.value = true
  await getRawRecordList()
}

const getRawRecordList = async () => {
  if (!rawRecordQuery.dataSourceId) return
  rawRecordLoading.value = true
  try {
    const data = await AiDataSourceApi.getRawRecordPage(rawRecordQuery)
    rawRecordList.value = data.list
    rawRecordTotal.value = data.total
  } finally {
    rawRecordLoading.value = false
  }
}

const formatMaskedPayload = (payload?: string) => {
  if (!payload) return '-'
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

const getKnowledgeName = (knowledgeBaseId?: number) => {
  return knowledgeOptions.value.find((item) => item.id === knowledgeBaseId)?.name || knowledgeBaseId || '-'
}

const getSourceTypeLabel = (value?: string) => {
  return sourceTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

const getSyncModeLabel = (value?: string) => {
  return syncModeOptions.find((item) => item.value === value)?.label || value || '-'
}

const getStatusLabel = (status?: number) => {
  return statusOptions.find((item) => item.value === status)?.label || '-'
}

const getStatusTagType = (status?: number) => {
  return statusOptions.find((item) => item.value === status)?.type || 'info'
}

const initRouteQuery = () => {
  const value = route.query.knowledgeBaseId || route.query.knowledgeId
  const rawValue = Array.isArray(value) ? value[0] : value
  if (!rawValue) return
  const knowledgeBaseId = Number(rawValue)
  if (!Number.isNaN(knowledgeBaseId)) {
    queryParams.knowledgeBaseId = knowledgeBaseId
  }
}

onMounted(async () => {
  initRouteQuery()
  await getKnowledgeOptions()
  await getList()
})
</script>

<style scoped>
.raw-record-json {
  max-height: 320px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}
</style>
