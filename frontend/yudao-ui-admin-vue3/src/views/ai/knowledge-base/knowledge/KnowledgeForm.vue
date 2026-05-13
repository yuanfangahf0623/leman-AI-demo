<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="860">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="120px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="知识库名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入知识库名称" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="知识库编码" prop="code">
            <el-input v-model="formData.code" placeholder="请输入知识库编码" maxlength="64" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="知识库描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          placeholder="请输入知识库描述"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="可见范围" prop="visibility">
            <el-select v-model="formData.visibility" placeholder="请选择可见范围" class="!w-1/1">
              <el-option
                v-for="item in visibilityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="向量库类型" prop="vectorStoreType">
            <el-select
              v-model="formData.vectorStoreType"
              placeholder="请选择向量库类型"
              class="!w-1/1"
            >
              <el-option
                v-for="item in vectorStoreTypeOptions"
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
          <el-form-item label="Embedding 模型" prop="embeddingModel">
            <el-input
              v-model="formData.embeddingModel"
              placeholder="请输入 Embedding 模型"
              maxlength="128"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Chat 模型" prop="chatModel">
            <el-input v-model="formData.chatModel" placeholder="请输入 Chat 模型" maxlength="128" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="切片大小" prop="chunkSize">
            <el-input-number
              v-model="formData.chunkSize"
              :min="1"
              :max="100000"
              :precision="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="切片重叠" prop="chunkOverlap">
            <el-input-number
              v-model="formData.chunkOverlap"
              :min="0"
              :max="100000"
              :precision="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="召回数量" prop="topK">
            <el-input-number
              v-model="formData.topK"
              :min="1"
              :max="100"
              :precision="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="相似度阈值" prop="scoreThreshold">
            <el-input-number
              v-model="formData.scoreThreshold"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="2"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>
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
import { AiKnowledgeApi, AiKnowledgeVO } from '@/api/ai/knowledge'

defineOptions({ name: 'AiKnowledgeForm' })

const { t } = useI18n()
const message = useMessage()

const visibilityOptions = [
  { label: '私有', value: 'private' },
  { label: '团队可见', value: 'team' },
  { label: '公开', value: 'public' }
]
const vectorStoreTypeOptions = [
  { label: 'PostgreSQL + pgvector', value: 'pgvector' },
  { label: 'Qdrant', value: 'qdrant' }
]

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<AiKnowledgeVO>({
  name: '',
  code: '',
  description: '',
  status: CommonStatusEnum.ENABLE,
  departmentIds: '*',
  visibility: 'public',
  embeddingModel: '',
  chatModel: '',
  vectorStoreType: 'pgvector',
  chunkSize: 800,
  chunkOverlap: 100,
  topK: 5,
  scoreThreshold: 0.1
})
const formRef = ref()

const validateChunkOverlap = (_rule: any, value: number, callback: (error?: Error) => void) => {
  if (value === undefined || value === null) {
    callback(new Error('切片重叠不能为空'))
    return
  }
  if (formData.value.chunkSize !== undefined && value >= formData.value.chunkSize) {
    callback(new Error('切片重叠必须小于切片大小'))
    return
  }
  callback()
}

const formRules = reactive<FormRules>({
  name: [{ required: true, message: '知识库名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '知识库编码不能为空', trigger: 'blur' }],
  visibility: [{ required: true, message: '可见范围不能为空', trigger: 'change' }],
  vectorStoreType: [{ required: true, message: '向量库类型不能为空', trigger: 'change' }],
  chunkSize: [{ required: true, message: '切片大小不能为空', trigger: 'blur' }],
  chunkOverlap: [{ required: true, validator: validateChunkOverlap, trigger: 'blur' }],
  topK: [{ required: true, message: '召回数量不能为空', trigger: 'blur' }],
  scoreThreshold: [{ required: true, message: '相似度阈值不能为空', trigger: 'blur' }]
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      const data = await AiKnowledgeApi.getKnowledge(id)
      formData.value = {
        ...formData.value,
        ...data,
        visibility: resolveVisibility(data),
        chatModel: data.chatModel || '',
        scoreThreshold: data.scoreThreshold ?? 0.1
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
    const data = buildSubmitData()
    if (formType.value === 'create') {
      await AiKnowledgeApi.createKnowledge(data)
      message.success(t('common.createSuccess'))
    } else {
      await AiKnowledgeApi.updateKnowledge(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const buildSubmitData = (): AiKnowledgeVO => {
  const data = { ...formData.value }
  if (data.visibility === 'public' && !data.departmentIds) {
    data.departmentIds = '*'
  }
  return data
}

const resolveVisibility = (data: Partial<AiKnowledgeVO>) => {
  if (data.visibility) {
    return data.visibility
  }
  return data.departmentIds && data.departmentIds !== '*' ? 'team' : 'public'
}

const resetForm = () => {
  formData.value = {
    id: undefined,
    name: '',
    code: '',
    description: '',
    status: CommonStatusEnum.ENABLE,
    departmentIds: '*',
    visibility: 'public',
    embeddingModel: '',
    chatModel: '',
    vectorStoreType: 'pgvector',
    chunkSize: 800,
    chunkOverlap: 100,
    topK: 5,
    scoreThreshold: 0.1
  }
  formRef.value?.resetFields()
}
</script>
