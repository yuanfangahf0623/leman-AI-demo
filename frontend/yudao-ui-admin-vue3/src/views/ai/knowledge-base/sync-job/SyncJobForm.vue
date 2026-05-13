<template>
  <Dialog v-model="dialogVisible" title="创建同步任务" width="640">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item label="所属知识库" prop="knowledgeBaseId">
        <el-select
          v-model="formData.knowledgeBaseId"
          placeholder="请选择知识库"
          filterable
          class="!w-1/1"
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
          v-model="formData.dataSourceId"
          placeholder="请选择数据源"
          filterable
          class="!w-1/1"
          :disabled="!formData.knowledgeBaseId"
        >
          <el-option
            v-for="item in dataSourceOptions"
            :key="item.id"
            :label="`${item.name}（${getSourceTypeLabel(item.sourceType)}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="任务类型" prop="jobType">
        <el-radio-group v-model="formData.jobType">
          <el-radio-button
            v-for="item in jobTypeOptions"
            :key="item.value"
            :label="item.value"
          >
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { AiKnowledgeVO } from '@/api/ai/knowledge'
import { AiDataSourceApi, AiDataSourceVO } from '@/api/ai/datasource'
import { AiSyncJobApi, AiSyncJobRecentVO } from '@/api/ai/sync/job'

defineOptions({ name: 'AiSyncJobForm' })

defineProps<{
  knowledgeOptions: AiKnowledgeVO[]
}>()

const message = useMessage()

const sourceTypeOptions = [
  { label: '文件', value: 'FILE' },
  { label: '数据库', value: 'DATABASE' },
  { label: 'API', value: 'API' },
  { label: 'Wiki', value: 'WIKI' },
  { label: 'Git', value: 'GIT' }
]
const jobTypeOptions = [
  { label: '全量同步', value: 'FULL' },
  { label: '增量同步', value: 'INCREMENTAL' }
]
const SyncJobStatusEnum = {
  PENDING: 0
}

const dialogVisible = ref(false)
const formLoading = ref(false)
const dataSourceOptions = ref<AiDataSourceVO[]>([])
const formData = ref({
  knowledgeBaseId: undefined as number | undefined,
  dataSourceId: undefined as number | undefined,
  jobType: 'INCREMENTAL'
})
const formRef = ref()

const formRules = reactive<FormRules>({
  knowledgeBaseId: [{ required: true, message: '所属知识库不能为空', trigger: 'change' }],
  dataSourceId: [{ required: true, message: '数据源不能为空', trigger: 'change' }],
  jobType: [{ required: true, message: '任务类型不能为空', trigger: 'change' }]
})

const open = async (knowledgeBaseId?: number) => {
  dialogVisible.value = true
  resetForm(knowledgeBaseId)
  if (knowledgeBaseId) {
    await getDataSourceOptions(knowledgeBaseId)
  }
}
defineExpose({ open })

const emit = defineEmits<{
  success: [job: AiSyncJobRecentVO]
}>()

const submitForm = async () => {
  const valid = await formRef.value.validate()
  if (!valid) return
  if (!formData.value.knowledgeBaseId || !formData.value.dataSourceId) return
  formLoading.value = true
  try {
    const submitData = {
      knowledgeBaseId: formData.value.knowledgeBaseId,
      dataSourceId: formData.value.dataSourceId,
      jobType: formData.value.jobType
    }
    const id = await AiSyncJobApi.createSyncJob(submitData)
    message.success('同步任务创建成功')
    dialogVisible.value = false
    emit('success', {
      id,
      ...submitData,
      status: SyncJobStatusEnum.PENDING,
      createTime: new Date().toISOString()
    })
  } finally {
    formLoading.value = false
  }
}

const handleKnowledgeChange = async () => {
  formData.value.dataSourceId = undefined
  if (!formData.value.knowledgeBaseId) {
    dataSourceOptions.value = []
    return
  }
  await getDataSourceOptions(formData.value.knowledgeBaseId)
}

const getDataSourceOptions = async (knowledgeBaseId: number) => {
  const data = await AiDataSourceApi.getDataSourcePage({
    pageNo: 1,
    pageSize: 100,
    knowledgeBaseId
  })
  dataSourceOptions.value = data.list
}

const getSourceTypeLabel = (value?: string) => {
  return sourceTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

const resetForm = (knowledgeBaseId?: number) => {
  formData.value = {
    knowledgeBaseId,
    dataSourceId: undefined,
    jobType: 'INCREMENTAL'
  }
  dataSourceOptions.value = []
  formRef.value?.clearValidate()
}
</script>
