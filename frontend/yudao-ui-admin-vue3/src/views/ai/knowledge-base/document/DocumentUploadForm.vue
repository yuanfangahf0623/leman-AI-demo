<template>
  <Dialog v-model="dialogVisible" title="上传文档" width="560">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
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
      <el-form-item label="所属目录" prop="directoryId">
        <el-tree-select
          v-model="formData.directoryId"
          :data="directorySelectOptions"
          :props="defaultProps"
          check-strictly
          clearable
          filterable
          node-key="id"
          placeholder="请选择目录"
          class="!w-1/1"
        />
      </el-form-item>
      <el-alert class="mb-16px" :title="uploadTipText" type="info" show-icon :closable="false" />
      <el-form-item label="文档文件" prop="file">
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
          :accept="uploadAccept"
          drag
        >
          <Icon icon="ep:upload-filled" class="text-36px text-gray-400" />
          <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">{{ uploadTipText }}</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules, UploadFile, UploadUserFile } from 'element-plus'
import { defaultProps } from '@/utils/tree'
import { AiDocumentApi } from '@/api/ai/document'
import type { AiKnowledgeDirectoryVO, AiKnowledgeVO } from '@/api/ai/knowledge'

defineOptions({ name: 'AiDocumentUploadForm' })

const props = defineProps<{
  knowledgeOptions: AiKnowledgeVO[]
  directoryOptions: AiKnowledgeDirectoryVO[]
}>()

const emit = defineEmits<{
  success: []
  knowledgeChange: [knowledgeBaseId?: number]
}>()

const message = useMessage()

const uploadTipText =
  '支持 TXT、MD、PDF、Word（DOC、DOCX、WPS）、Excel（XLS、XLSX、XLSB）、PowerPoint（PPT、PPTX、PPTM），单个文件不超过 50MB。'
const allowedExtensions = [
  'txt',
  'md',
  'pdf',
  'doc',
  'docx',
  'wps',
  'xls',
  'xlsx',
  'xlsb',
  'ppt',
  'pptx',
  'pptm'
]
const uploadAccept = allowedExtensions.map((extension) => `.${extension}`).join(',')
const maxFileSize = 50 * 1024 * 1024

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const uploadRef = ref()
const fileList = ref<UploadUserFile[]>([])
const formData = ref<{
  knowledgeBaseId?: number
  directoryId?: number
}>({
  knowledgeBaseId: undefined,
  directoryId: undefined
})
const formRules = reactive<FormRules>({
  knowledgeBaseId: [{ required: true, message: '知识库不能为空', trigger: 'change' }]
})

const directorySelectOptions = computed(() => props.directoryOptions)

const open = (knowledgeBaseId?: number, directoryId?: number) => {
  dialogVisible.value = true
  resetForm()
  formData.value.knowledgeBaseId = knowledgeBaseId
  formData.value.directoryId = directoryId && directoryId > 0 ? directoryId : undefined
}
defineExpose({ open })

const submitForm = async () => {
  const valid = await formRef.value.validate()
  if (!valid) return
  const rawFile = fileList.value[0]?.raw as File | undefined
  if (!rawFile) {
    message.error('请上传文档文件')
    return
  }
  if (!validateFile(rawFile)) {
    return
  }
  formLoading.value = true
  try {
    await AiDocumentApi.uploadDocument(
      formData.value.knowledgeBaseId!,
      rawFile,
      formData.value.directoryId
    )
    message.success('上传完成，系统已自动尝试解析和向量化')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const handleKnowledgeChange = (value?: number) => {
  formData.value.directoryId = undefined
  emit('knowledgeChange', value)
}

const handleFileChange = (file: UploadFile) => {
  const rawFile = file.raw as File | undefined
  if (!rawFile || !validateFile(rawFile)) {
    fileList.value = []
    return
  }
  fileList.value = [file]
}

const validateFile = (file: File) => {
  const extension = getFileExtension(file.name)
  if (!allowedExtensions.includes(extension)) {
    message.error('仅支持 TXT、MD、PDF、Word、Excel、PowerPoint 文件')
    return false
  }
  if (file.size > maxFileSize) {
    message.error('文件大小不能超过 50MB')
    return false
  }
  return true
}

const getFileExtension = (filename: string) => {
  const index = filename.lastIndexOf('.')
  return index < 0 ? '' : filename.substring(index + 1).toLowerCase()
}

const handleExceed = () => {
  message.error('最多只能上传一个文件')
}

const resetForm = () => {
  formLoading.value = false
  formData.value = {
    knowledgeBaseId: undefined,
    directoryId: undefined
  }
  fileList.value = []
  uploadRef.value?.clearFiles()
  formRef.value?.resetFields()
}
</script>
