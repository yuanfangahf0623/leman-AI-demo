<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="780">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="120px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="所属知识库" prop="knowledgeBaseId">
            <el-select
              v-model="formData.knowledgeBaseId"
              placeholder="请选择知识库"
              filterable
              class="!w-1/1"
            >
              <el-option
                v-for="item in knowledgeOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据源名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入数据源名称" maxlength="128" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="数据源类型" prop="sourceType">
            <el-select
              v-model="formData.sourceType"
              placeholder="请选择数据源类型"
              class="!w-1/1"
            >
              <el-option
                v-for="item in sourceTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="同步模式" prop="syncMode">
            <el-select v-model="formData.syncMode" placeholder="请选择同步模式" class="!w-1/1">
              <el-option
                v-for="item in syncModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="启用同步" prop="syncEnabled">
            <el-switch v-model="formData.syncEnabled" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-select v-model="formData.status" placeholder="请选择状态" class="!w-1/1">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="配置 JSON" prop="configJson">
        <el-input
          v-model="formData.configJson"
          type="textarea"
          :rows="7"
          placeholder='FILE 示例：{"files":["D:/data/demo.txt"],"directories":["D:/data/docs"]}'
          maxlength="4000"
          show-word-limit
        />
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
import { CommonStatusEnum } from '@/utils/constants'
import { AiKnowledgeVO } from '@/api/ai/knowledge'
import { AiDataSourceApi, AiDataSourceVO } from '@/api/ai/datasource'

defineOptions({ name: 'AiDataSourceForm' })

defineProps<{
  knowledgeOptions: AiKnowledgeVO[]
}>()

const { t } = useI18n()
const message = useMessage()

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
  { label: '开启', value: CommonStatusEnum.ENABLE },
  { label: '停用', value: CommonStatusEnum.DISABLE }
]

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<Partial<AiDataSourceVO>>({})
const formRef = ref()

const formRules = reactive<FormRules>({
  knowledgeBaseId: [{ required: true, message: '所属知识库不能为空', trigger: 'change' }],
  name: [{ required: true, message: '数据源名称不能为空', trigger: 'blur' }],
  sourceType: [{ required: true, message: '数据源类型不能为空', trigger: 'change' }],
  syncMode: [{ required: true, message: '同步模式不能为空', trigger: 'change' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number, knowledgeBaseId?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm(knowledgeBaseId)
  if (id) {
    formLoading.value = true
    try {
      const data = await AiDataSourceApi.getDataSource(id)
      formData.value = {
        ...formData.value,
        ...data,
        syncEnabled: data.syncEnabled ?? true,
        status: data.status ?? CommonStatusEnum.ENABLE
      }
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  const valid = await formRef.value.validate()
  if (!valid) return
  formLoading.value = true
  try {
    const data = formData.value as AiDataSourceVO
    if (formType.value === 'create') {
      await AiDataSourceApi.createDataSource(data)
      message.success(t('common.createSuccess'))
    } else {
      await AiDataSourceApi.updateDataSource(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = (knowledgeBaseId?: number) => {
  formData.value = {
    id: undefined,
    knowledgeBaseId,
    name: '',
    sourceType: 'FILE',
    syncMode: 'MANUAL',
    configJson: '',
    syncEnabled: true,
    status: CommonStatusEnum.ENABLE
  }
  formRef.value?.clearValidate()
}
</script>
