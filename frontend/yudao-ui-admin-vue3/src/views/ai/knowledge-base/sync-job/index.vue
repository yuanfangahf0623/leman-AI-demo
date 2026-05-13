<template>
  <ContentWrap>
    <el-alert
      title="当前后端已提供同步任务创建和执行接口；分页查询接口尚未提供，本页面展示最近创建的任务并支持执行。"
      type="info"
      show-icon
      :closable="false"
      class="mb-16px"
    />

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
      <el-form-item label="数据源" prop="dataSourceId">
        <el-select
          v-model="queryParams.dataSourceId"
          placeholder="请选择数据源"
          clearable
          filterable
          class="!w-240px"
        >
          <el-option
            v-for="item in filteredDataSourceOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="任务类型" prop="jobType">
        <el-select v-model="queryParams.jobType" placeholder="请选择类型" clearable class="!w-180px">
          <el-option
            v-for="item in jobTypeOptions"
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
          @click="openForm"
          v-hasPermi="['ai:sync-job:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 创建任务
        </el-button>
        <el-button plain @click="handleClearRecent">
          <Icon icon="ep:delete" class="mr-5px" /> 清空本地记录
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="filteredRecentJobList"
      :stripe="true"
      :show-overflow-tooltip="true"
    >
      <el-table-column label="任务编号" align="center" prop="id" width="100" />
      <el-table-column label="所属知识库" align="center" min-width="150">
        <template #default="scope">
          {{ getKnowledgeName(scope.row.knowledgeBaseId) }}
        </template>
      </el-table-column>
      <el-table-column label="数据源" align="center" min-width="150">
        <template #default="scope">
          {{ getDataSourceName(scope.row.dataSourceId) }}
        </template>
      </el-table-column>
      <el-table-column label="任务类型" align="center" width="120">
        <template #default="scope">
          {{ getJobTypeLabel(scope.row.jobType) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="120">
        <template #default="scope">
          <el-tag :type="getStatusTagType(scope.row.status)">
            {{ getStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误信息" align="center" prop="errorMessage" min-width="180" />
      <el-table-column label="创建时间" align="center" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="最近执行时间" align="center" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.executeTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="110">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :loading="getActionLoading(scope.row.id)"
            :disabled="scope.row.status === SyncJobStatusEnum.RUNNING"
            @click="handleExecute(scope.row)"
            v-hasPermi="['ai:sync-job:execute']"
          >
            执行
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <SyncJobForm
    ref="formRef"
    :knowledge-options="knowledgeOptions"
    @success="handleCreateSuccess"
  />
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { AiKnowledgeApi, AiKnowledgeVO } from '@/api/ai/knowledge'
import { AiDataSourceApi, AiDataSourceVO } from '@/api/ai/datasource'
import { AiSyncJobApi, AiSyncJobRecentVO } from '@/api/ai/sync/job'
import SyncJobForm from './SyncJobForm.vue'

defineOptions({ name: 'AiKnowledgeSyncJob' })

const RECENT_STORAGE_KEY = 'ai-sync-job-recent-list'
const SyncJobStatusEnum = {
  PENDING: 0,
  RUNNING: 10,
  SUCCESS: 20,
  FAILED: 30,
  CANCELED: 40
}
const jobTypeOptions = [
  { label: '全量同步', value: 'FULL' },
  { label: '增量同步', value: 'INCREMENTAL' }
]
const statusOptions = [
  { label: '待执行', value: SyncJobStatusEnum.PENDING, type: 'info' },
  { label: '运行中', value: SyncJobStatusEnum.RUNNING, type: 'warning' },
  { label: '成功', value: SyncJobStatusEnum.SUCCESS, type: 'success' },
  { label: '失败', value: SyncJobStatusEnum.FAILED, type: 'danger' },
  { label: '已取消', value: SyncJobStatusEnum.CANCELED, type: 'info' }
] as const

const route = useRoute()
const message = useMessage()

const loading = ref(false)
const knowledgeOptions = ref<AiKnowledgeVO[]>([])
const dataSourceOptions = ref<AiDataSourceVO[]>([])
const recentJobList = ref<AiSyncJobRecentVO[]>([])
const queryParams = reactive({
  knowledgeBaseId: undefined as number | undefined,
  dataSourceId: undefined as number | undefined,
  jobType: undefined as string | undefined
})
const queryFormRef = ref()
const actionLoading = reactive<Record<number, boolean>>({})

const filteredDataSourceOptions = computed(() => {
  if (!queryParams.knowledgeBaseId) {
    return dataSourceOptions.value
  }
  return dataSourceOptions.value.filter((item) => item.knowledgeBaseId === queryParams.knowledgeBaseId)
})

const filteredRecentJobList = computed(() => {
  return recentJobList.value.filter((item) => {
    if (queryParams.knowledgeBaseId && item.knowledgeBaseId !== queryParams.knowledgeBaseId) return false
    if (queryParams.dataSourceId && item.dataSourceId !== queryParams.dataSourceId) return false
    if (queryParams.jobType && item.jobType !== queryParams.jobType) return false
    return true
  })
})

const getKnowledgeOptions = async () => {
  const data = await AiKnowledgeApi.getKnowledgePage({
    pageNo: 1,
    pageSize: 100
  })
  knowledgeOptions.value = data.list
}

const getDataSourceOptions = async () => {
  const data = await AiDataSourceApi.getDataSourcePage({
    pageNo: 1,
    pageSize: 200
  })
  dataSourceOptions.value = data.list
}

const handleKnowledgeChange = () => {
  if (
    queryParams.dataSourceId &&
    !filteredDataSourceOptions.value.some((item) => item.id === queryParams.dataSourceId)
  ) {
    queryParams.dataSourceId = undefined
  }
}

const handleQuery = () => {
  handleKnowledgeChange()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = () => {
  formRef.value.open(queryParams.knowledgeBaseId)
}

const handleCreateSuccess = async (job: AiSyncJobRecentVO) => {
  recentJobList.value = [job, ...recentJobList.value.filter((item) => item.id !== job.id)].slice(0, 50)
  saveRecentJobs()
  await getDataSourceOptions()
}

const handleExecute = async (row: AiSyncJobRecentVO) => {
  actionLoading[row.id] = true
  row.status = SyncJobStatusEnum.RUNNING
  row.errorMessage = undefined
  saveRecentJobs()
  try {
    await AiSyncJobApi.executeSyncJob(row.id)
    row.status = SyncJobStatusEnum.SUCCESS
    row.executeTime = new Date().toISOString()
    message.success('同步任务执行完成')
  } catch (error) {
    row.status = SyncJobStatusEnum.FAILED
    row.executeTime = new Date().toISOString()
    row.errorMessage = '执行失败，请查看后端任务记录或服务日志'
    message.error(row.errorMessage)
  } finally {
    actionLoading[row.id] = false
    saveRecentJobs()
  }
}

const handleClearRecent = async () => {
  if (recentJobList.value.length === 0) return
  try {
    await message.confirm('确认清空本地最近创建任务记录？')
    recentJobList.value = []
    saveRecentJobs()
  } catch {}
}

const getActionLoading = (id: number) => {
  return actionLoading[id] === true
}

const getKnowledgeName = (knowledgeBaseId?: number) => {
  return knowledgeOptions.value.find((item) => item.id === knowledgeBaseId)?.name || knowledgeBaseId || '-'
}

const getDataSourceName = (dataSourceId?: number) => {
  return dataSourceOptions.value.find((item) => item.id === dataSourceId)?.name || dataSourceId || '-'
}

const getJobTypeLabel = (value?: string) => {
  return jobTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

const getStatusLabel = (status?: number) => {
  return statusOptions.find((item) => item.value === status)?.label || '-'
}

const getStatusTagType = (status?: number) => {
  return statusOptions.find((item) => item.value === status)?.type || 'info'
}

const formatDateTime = (value?: string) => {
  return value ? formatDate(new Date(value)) : '-'
}

const loadRecentJobs = () => {
  const value = localStorage.getItem(RECENT_STORAGE_KEY)
  if (!value) return
  try {
    const jobs = JSON.parse(value)
    recentJobList.value = Array.isArray(jobs) ? jobs : []
  } catch {
    recentJobList.value = []
  }
}

const saveRecentJobs = () => {
  localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(recentJobList.value))
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
  loading.value = true
  try {
    initRouteQuery()
    loadRecentJobs()
    await Promise.all([getKnowledgeOptions(), getDataSourceOptions()])
    handleKnowledgeChange()
  } finally {
    loading.value = false
  }
})
</script>
