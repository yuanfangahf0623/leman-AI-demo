<template>
  <Dialog v-model="dialogVisible" title="部门导入" width="520">
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :auto-upload="false"
      :disabled="formLoading"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
      accept=".xlsx,.xls"
      drag
    >
      <Icon icon="ep:upload" />
      <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip text-center">
          <span>仅允许导入 xls、xlsx 格式文件。</span>
          <el-link
            :underline="false"
            class="ml-8px"
            style="font-size: 12px; vertical-align: baseline"
            type="primary"
            @click="downloadTemplate"
          >
            下载模板
          </el-link>
        </div>
      </template>
    </el-upload>

    <el-alert
      v-if="importResult"
      :closable="false"
      class="mt-16px"
      show-icon
      title="导入结果"
      type="success"
    >
      <template #default>
        <span>正确导入数量：{{ importResult.successCount }}</span>
        <span class="ml-16px">
          导入异常数量：
          <el-link
            v-if="importResult.failureCount > 0"
            type="danger"
            :underline="false"
            @click="downloadFailureRows"
          >
            {{ importResult.failureCount }}
          </el-link>
          <span v-else>0</span>
        </span>
      </template>
    </el-alert>

    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { UploadFile, UploadFiles } from 'element-plus'
import * as DeptApi from '@/api/system/dept'
import download from '@/utils/download'

defineOptions({ name: 'SystemDeptImportForm' })

interface DeptImportFailureRow {
  rowNo: number
  name?: string
  parentId?: number
  leaderUserId?: number
  phone?: string
  email?: string
  sort?: number
  status?: number
  errorMessage?: string
}

interface DeptImportResult {
  successCount: number
  failureCount: number
  failureRows: DeptImportFailureRow[]
}

const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const uploadRef = ref()
const fileList = ref<UploadFiles>([])
const importResult = ref<DeptImportResult>()

const open = () => {
  dialogVisible.value = true
  fileList.value = []
  importResult.value = undefined
  resetForm()
}
defineExpose({ open })

const emit = defineEmits(['success'])

const submitForm = async () => {
  const file = fileList.value[0]
  if (!file?.raw) {
    message.error('请上传部门导入文件')
    return
  }
  const formData = new FormData()
  formData.append('file', file.raw)
  formLoading.value = true
  try {
    importResult.value = (await DeptApi.importDept(formData)) as DeptImportResult
    message.success(
      `导入完成，正确导入 ${importResult.value.successCount} 条，异常 ${importResult.value.failureCount} 条`
    )
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const handleFileChange = (_file: UploadFile, files: UploadFiles) => {
  fileList.value = files.slice(-1)
  importResult.value = undefined
}

const handleExceed = () => {
  message.error('最多只能上传一个文件')
}

const resetForm = async () => {
  formLoading.value = false
  await nextTick()
  uploadRef.value?.clearFiles()
}

const downloadTemplate = async () => {
  const res = await DeptApi.importDeptTemplate()
  download.excel(res, '部门导入模板.xlsx')
}

const downloadFailureRows = () => {
  if (!importResult.value?.failureRows?.length) {
    return
  }
  const headers = [
    '行号',
    '部门名称',
    '上级部门编号',
    '负责人用户编号',
    '联系电话',
    '邮箱',
    '显示排序',
    '状态',
    '异常原因'
  ]
  const rows = importResult.value.failureRows.map((row) => [
    row.rowNo,
    row.name,
    row.parentId,
    row.leaderUserId,
    row.phone,
    row.email,
    row.sort,
    row.status,
    row.errorMessage
  ])
  const tableRows = [headers, ...rows]
    .map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('')
  const html = `<html><head><meta charset="UTF-8" /></head><body><table>${tableRows}</table></body></html>`
  const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '部门导入异常数据.xls'
  link.click()
  URL.revokeObjectURL(url)
}

const escapeHtml = (value: unknown) => {
  const text = value === undefined || value === null ? '' : String(value)
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
</script>
