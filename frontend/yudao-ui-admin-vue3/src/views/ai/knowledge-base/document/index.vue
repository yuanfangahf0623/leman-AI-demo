<template>
  <div class="document-page">
    <ContentWrap class="directory-panel">
      <div class="knowledge-selector">
        <div class="selector-label">选择知识库</div>
        <el-select
          v-model="queryParams.knowledgeBaseId"
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
      </div>
      <div class="directory-header">
        <span>知识库目录</span>
        <el-button
          link
          type="primary"
          :disabled="!queryParams.knowledgeBaseId"
          @click="refreshDirectories"
        >
          刷新
        </el-button>
      </div>
      <el-empty v-if="!queryParams.knowledgeBaseId" :image-size="72" description="请先选择知识库" />
      <el-tree
        v-else
        ref="directoryTreeRef"
        :data="directoryTreeData"
        :props="defaultProps"
        node-key="id"
        default-expand-all
        highlight-current
        @node-click="handleDirectoryClick"
      />
    </ContentWrap>

    <div class="document-content">
      <ContentWrap>
        <el-form
          ref="queryFormRef"
          class="-mb-15px"
          :model="queryParams"
          :inline="true"
          label-width="90px"
        >
          <el-form-item label="文档标题" prop="title">
            <el-input
              v-model="queryParams.title"
              placeholder="请输入文档标题"
              clearable
              class="!w-240px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="解析状态" prop="parseStatus">
            <el-select
              v-model="queryParams.parseStatus"
              placeholder="请选择解析状态"
              clearable
              class="!w-180px"
            >
              <el-option
                v-for="item in parseStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="向量化状态" prop="embeddingStatus">
            <el-select
              v-model="queryParams.embeddingStatus"
              placeholder="请选择向量化状态"
              clearable
              class="!w-180px"
            >
              <el-option
                v-for="item in embeddingStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetQuery"
              ><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button
            >
            <el-button
              type="primary"
              plain
              @click="openUploadForm"
              v-hasPermi="['ai:document:upload']"
            >
              <Icon icon="ep:upload" class="mr-5px" /> 上传
            </el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>

      <ContentWrap>
        <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
          <el-table-column label="文件名" align="center" min-width="220">
            <template #default="scope">
              <el-button
                v-if="scope.row.fileName"
                link
                type="primary"
                class="document-file-link"
                @click="handlePreview(scope.row)"
              >
                {{ scope.row.fileName }}
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="文档标题" align="center" prop="title" min-width="180" />
          <el-table-column label="所属知识库" align="center" min-width="150">
            <template #default="scope">
              {{ getKnowledgeName(scope.row.knowledgeBaseId) }}
            </template>
          </el-table-column>
          <el-table-column label="知识库目录" align="center" min-width="150">
            <template #default="scope">
              {{ getDirectoryName(scope.row.directoryId) }}
            </template>
          </el-table-column>
          <el-table-column label="文档版本" align="center" prop="documentVersion" width="100">
            <template #default="scope">
              {{ scope.row.documentVersion || 'v1' }}
            </template>
          </el-table-column>
          <el-table-column label="文件类型" align="center" prop="fileType" width="100" />
          <el-table-column label="文件大小" align="center" width="110">
            <template #default="scope">
              {{ formatFileSize(scope.row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="解析状态" align="center" width="120">
            <template #default="scope">
              <el-tag :type="getParseStatusTagType(scope.row.parseStatus)">
                {{ getParseStatusLabel(scope.row.parseStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="向量化状态" align="center" width="130">
            <template #default="scope">
              <el-tag :type="getEmbeddingStatusTagType(scope.row.embeddingStatus)">
                {{ getEmbeddingStatusLabel(scope.row.embeddingStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="切片数" align="center" prop="chunkCount" width="90" />
          <el-table-column label="Token 数" align="center" prop="tokenCount" width="100" />
          <el-table-column
            label="创建时间"
            align="center"
            prop="createTime"
            :formatter="dateFormatter"
            width="180"
          />
          <el-table-column label="操作" align="center" fixed="right" width="330">
            <template #default="scope">
              <el-button
                link
                type="primary"
                @click="handleEdit(scope.row)"
                v-hasPermi="['ai:document:update']"
              >
                编辑
              </el-button>
              <el-button
                link
                type="primary"
                :loading="getActionLoading('parse', scope.row.id)"
                :disabled="!canManualParse(scope.row)"
                @click="handleParse(scope.row)"
                v-hasPermi="['ai:document:parse']"
              >
                解析
              </el-button>
              <el-button
                link
                type="primary"
                :loading="getActionLoading('embed', scope.row.id)"
                :disabled="!canManualEmbed(scope.row)"
                @click="handleEmbed(scope.row)"
                v-hasPermi="['ai:document:embed']"
              >
                向量化
              </el-button>
              <el-button
                link
                :type="scope.row.errorMessage ? 'warning' : 'info'"
                :disabled="!scope.row.errorMessage"
                @click="handleShowLog(scope.row)"
              >
                处理日志
              </el-button>
              <el-button
                link
                type="danger"
                @click="handleDelete(scope.row.id)"
                v-hasPermi="['ai:document:delete']"
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
    </div>
  </div>

  <el-dialog
    v-model="previewVisible"
    :fullscreen="previewFullscreen"
    width="82%"
    top="5vh"
    destroy-on-close
    append-to-body
    @closed="handlePreviewClosed"
  >
    <template #header="{ titleId, titleClass }">
      <div class="preview-dialog-header">
        <span :id="titleId" :class="titleClass">{{ previewTitle }}</span>
        <el-button link type="primary" @click="previewFullscreen = !previewFullscreen">
          <Icon :icon="previewFullscreen ? 'ep:copy-document' : 'ep:full-screen'" class="mr-5px" />
          {{ previewFullscreen ? '还原' : '最大化' }}
        </el-button>
      </div>
    </template>
    <div v-loading="previewLoading" class="preview-dialog-body">
      <iframe
        v-if="previewMode === 'iframe' && previewUrl"
        :src="previewUrl"
        class="preview-frame"
        :class="{ 'is-fullscreen': previewFullscreen }"
      ></iframe>
      <VueOfficeExcel
        v-else-if="previewMode === 'excel' && previewSource"
        :src="previewSource"
        class="office-preview"
        :class="{ 'is-fullscreen': previewFullscreen }"
        @error="handleOfficePreviewError"
      />
      <VueOfficeDocx
        v-else-if="previewMode === 'docx' && previewSource"
        :src="previewSource"
        class="office-preview"
        :class="{ 'is-fullscreen': previewFullscreen }"
        @error="handleOfficePreviewError"
      />
      <VueOfficePptx
        v-else-if="previewMode === 'pptx' && previewSource"
        :src="previewSource"
        class="office-preview"
        :class="{ 'is-fullscreen': previewFullscreen }"
        @error="handleOfficePreviewError"
      />
      <el-empty v-else-if="previewMode === 'unsupported'" :description="previewUnsupportedText" />
      <el-empty v-else description="暂无可预览内容" />
    </div>
  </el-dialog>

  <Dialog v-model="logVisible" title="文档处理日志" width="640">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="文件名">{{
        currentLogRow?.fileName || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="文档版本">
        {{ currentLogRow?.documentVersion || 'v1' }}
      </el-descriptions-item>
      <el-descriptions-item label="解析状态">
        {{ getParseStatusLabel(currentLogRow?.parseStatus) }}
      </el-descriptions-item>
      <el-descriptions-item label="向量化状态">
        {{ getEmbeddingStatusLabel(currentLogRow?.embeddingStatus) }}
      </el-descriptions-item>
      <el-descriptions-item label="错误信息">
        <el-input
          :model-value="currentLogRow?.errorMessage || '暂无错误信息'"
          type="textarea"
          :rows="6"
          readonly
        />
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <Dialog v-model="editVisible" title="编辑文档" width="560">
    <el-form
      ref="editFormRef"
      v-loading="editLoading"
      :model="editFormData"
      :rules="editRules"
      label-width="100px"
    >
      <el-form-item label="文档标题" prop="title">
        <el-input v-model="editFormData.title" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="文档版本" prop="documentVersion">
        <el-input v-model="editFormData.documentVersion" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="知识库目录" prop="directoryId">
        <el-tree-select
          v-model="editFormData.directoryId"
          :data="directoryOptions"
          :props="defaultProps"
          check-strictly
          clearable
          filterable
          node-key="id"
          placeholder="请选择目录"
          class="!w-1/1"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="editLoading" type="primary" @click="submitEdit">确 定</el-button>
      <el-button @click="editVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <DocumentUploadForm
    ref="uploadFormRef"
    :knowledge-options="knowledgeOptions"
    :directory-options="uploadDirectoryOptions"
    @knowledge-change="handleUploadKnowledgeChange"
    @success="handleUploadSuccess"
  />
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import VueOfficeDocx from '@vue-office/docx/lib/v3/vue-office-docx.mjs'
import VueOfficeExcel from '@vue-office/excel/lib/v3/vue-office-excel.mjs'
import VueOfficePptx from '@vue-office/pptx/lib/v3/vue-office-pptx.mjs'
import '@vue-office/docx/lib/v3/index.css'
import '@vue-office/excel/lib/v3/index.css'
import { dateFormatter } from '@/utils/formatTime'
import { defaultProps, handleTree } from '@/utils/tree'
import {
  AiDocumentApi,
  AiDocumentPageReqVO,
  AiDocumentUpdateReqVO,
  AiDocumentVO
} from '@/api/ai/document'
import {
  AiKnowledgeApi,
  AiKnowledgeDirectoryApi,
  AiKnowledgeDirectoryVO,
  AiKnowledgeVO
} from '@/api/ai/knowledge'
import DocumentUploadForm from './DocumentUploadForm.vue'

defineOptions({ name: 'AiKnowledgeDocumentManage' })

const DocumentParseStatusEnum = {
  PENDING: 0,
  RUNNING: 10,
  SUCCESS: 20,
  FAILED: 30
}
const DocumentEmbeddingStatusEnum = {
  PENDING: 0,
  RUNNING: 10,
  SUCCESS: 20,
  FAILED: 30
}
const parseStatusOptions = [
  { label: '等待解析', value: DocumentParseStatusEnum.PENDING, type: 'info' },
  { label: '解析中', value: DocumentParseStatusEnum.RUNNING, type: 'warning' },
  { label: '解析成功', value: DocumentParseStatusEnum.SUCCESS, type: 'success' },
  { label: '解析失败', value: DocumentParseStatusEnum.FAILED, type: 'danger' }
] as const
const embeddingStatusOptions = [
  { label: '等待向量化', value: DocumentEmbeddingStatusEnum.PENDING, type: 'info' },
  { label: '向量化中', value: DocumentEmbeddingStatusEnum.RUNNING, type: 'warning' },
  { label: '向量化成功', value: DocumentEmbeddingStatusEnum.SUCCESS, type: 'success' },
  { label: '向量化失败', value: DocumentEmbeddingStatusEnum.FAILED, type: 'danger' }
] as const

const route = useRoute()
const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const list = ref<AiDocumentVO[]>([])
const total = ref(0)
const knowledgeOptions = ref<AiKnowledgeVO[]>([])
const directoryOptions = ref<AiKnowledgeDirectoryVO[]>([])
const uploadDirectoryOptions = ref<AiKnowledgeDirectoryVO[]>([])
const selectedDirectoryKey = ref<string | number>('all')
const directoryTreeRef = ref()
const queryParams = reactive<AiDocumentPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  knowledgeBaseId: undefined,
  directoryId: undefined,
  parseStatus: undefined,
  embeddingStatus: undefined,
  title: undefined
})
const queryFormRef = ref()
const actionLoading = reactive<Record<string, boolean>>({})
const previewVisible = ref(false)
const previewFullscreen = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('文件预览')
const previewUrl = ref('')
const previewSource = shallowRef<Blob | ArrayBuffer | string>('')
const previewMode = ref<'iframe' | 'excel' | 'docx' | 'pptx' | 'unsupported'>('iframe')
const previewUnsupportedText = ref('当前文件格式暂不支持在线预览')
const logVisible = ref(false)
const currentLogRow = ref<AiDocumentVO>()
const editVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref()
const editFormData = reactive<AiDocumentUpdateReqVO>({
  id: undefined,
  directoryId: undefined,
  title: '',
  documentVersion: 'v1'
})
const editRules = reactive<FormRules>({
  title: [
    { required: true, message: '文档标题不能为空', trigger: 'blur' },
    { max: 255, message: '文档标题不能超过 255 个字符', trigger: 'blur' }
  ],
  documentVersion: [{ max: 64, message: '文档版本不能超过 64 个字符', trigger: 'blur' }]
})

const directoryTreeData = computed(() => {
  if (!queryParams.knowledgeBaseId) {
    return []
  }
  return [
    {
      id: 'all',
      name: '全部目录',
      children: directoryOptions.value
    }
  ]
})
const directoryNameMap = computed(() => {
  const map = new Map<number, string>()
  const visit = (items: AiKnowledgeDirectoryVO[]) => {
    items.forEach((item) => {
      if (item.id) {
        map.set(item.id, item.name)
      }
      if (item.children?.length) {
        visit(item.children)
      }
    })
  }
  visit(directoryOptions.value)
  return map
})

const getList = async () => {
  loading.value = true
  try {
    const data = await AiDocumentApi.getDocumentPage(queryParams)
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

const initDefaultKnowledge = () => {
  if (!knowledgeOptions.value.length) {
    queryParams.knowledgeBaseId = undefined
    return
  }
  const exists = knowledgeOptions.value.some((item) => item.id === queryParams.knowledgeBaseId)
  if (!queryParams.knowledgeBaseId || !exists) {
    queryParams.knowledgeBaseId = knowledgeOptions.value[0].id
  }
}

const getDirectoryOptions = async (knowledgeBaseId?: number) => {
  if (!knowledgeBaseId) {
    directoryOptions.value = []
    return
  }
  const data = await AiKnowledgeDirectoryApi.getDirectoryList({ knowledgeBaseId })
  directoryOptions.value = handleTree(data, 'id', 'parentId')
  nextTick(() => directoryTreeRef.value?.setCurrentKey(selectedDirectoryKey.value))
}

const refreshDirectories = async () => {
  await getDirectoryOptions(queryParams.knowledgeBaseId)
}

const handleKnowledgeChange = async (value?: number) => {
  queryParams.directoryId = undefined
  selectedDirectoryKey.value = 'all'
  await getDirectoryOptions(value)
  handleQuery()
}

const handleDirectoryClick = (node: AiKnowledgeDirectoryVO & { id: string | number }) => {
  selectedDirectoryKey.value = node.id
  queryParams.directoryId = node.id === 'all' ? undefined : Number(node.id)
  handleQuery()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = async () => {
  queryFormRef.value.resetFields()
  queryParams.directoryId = undefined
  selectedDirectoryKey.value = 'all'
  await getDirectoryOptions(queryParams.knowledgeBaseId)
  handleQuery()
}

const uploadFormRef = ref()
const openUploadForm = () => {
  uploadDirectoryOptions.value = directoryOptions.value
  uploadFormRef.value.open(
    queryParams.knowledgeBaseId,
    queryParams.directoryId && queryParams.directoryId > 0 ? queryParams.directoryId : undefined
  )
}

const handleUploadKnowledgeChange = async (knowledgeBaseId?: number) => {
  if (!knowledgeBaseId) {
    uploadDirectoryOptions.value = []
    return
  }
  if (knowledgeBaseId === queryParams.knowledgeBaseId) {
    uploadDirectoryOptions.value = directoryOptions.value
    return
  }
  const data = await AiKnowledgeDirectoryApi.getDirectoryList({ knowledgeBaseId })
  uploadDirectoryOptions.value = handleTree(data, 'id', 'parentId')
}

const handleUploadSuccess = async () => {
  queryParams.pageNo = 1
  await getList()
}

const handleEdit = (row: AiDocumentVO) => {
  editFormData.id = row.id
  editFormData.directoryId = row.directoryId && row.directoryId > 0 ? row.directoryId : undefined
  editFormData.title = row.title || row.fileName || ''
  editFormData.documentVersion = row.documentVersion || 'v1'
  editVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

const submitEdit = async () => {
  const valid = await editFormRef.value.validate()
  if (!valid) return
  editLoading.value = true
  try {
    await AiDocumentApi.updateDocument({
      id: editFormData.id,
      directoryId: editFormData.directoryId,
      title: editFormData.title.trim(),
      documentVersion: editFormData.documentVersion?.trim()
    })
    message.success('修改成功')
    editVisible.value = false
    await getList()
  } finally {
    editLoading.value = false
  }
}

const handleParse = async (row: AiDocumentVO) => {
  if (!row.id) return
  try {
    setActionLoading('parse', row.id, true)
    await AiDocumentApi.parseDocument(row.id)
    message.success('解析完成')
    await getList()
  } finally {
    setActionLoading('parse', row.id, false)
  }
}

const handleEmbed = async (row: AiDocumentVO) => {
  if (!row.id) return
  try {
    setActionLoading('embed', row.id, true)
    await AiDocumentApi.embedDocument(row.id)
    message.success('向量化完成')
    await getList()
  } finally {
    setActionLoading('embed', row.id, false)
  }
}

const handleShowLog = (row: AiDocumentVO) => {
  currentLogRow.value = row
  logVisible.value = true
}

const handleDelete = async (id?: number) => {
  if (!id) return
  try {
    await message.delConfirm()
    await AiDocumentApi.deleteDocument(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const handlePreview = async (row: AiDocumentVO) => {
  if (!row.id) return
  releasePreviewUrl()
  previewTitle.value = row.fileName || row.title || '文件预览'
  previewFullscreen.value = false
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewMode.value = resolvePreviewMode(row.fileType || row.fileName)
    if (previewMode.value === 'unsupported') {
      previewUnsupportedText.value = resolveUnsupportedPreviewText(row.fileType || row.fileName)
      return
    }
    const blob = await AiDocumentApi.previewDocument(row.id)
    if (previewMode.value === 'iframe') {
      previewUrl.value = URL.createObjectURL(blob)
    } else {
      previewSource.value = blob
    }
  } catch {
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

const handlePreviewClosed = () => {
  previewFullscreen.value = false
  releasePreviewUrl()
}

const releasePreviewUrl = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewSource.value = ''
  previewMode.value = 'iframe'
  previewUnsupportedText.value = '当前文件格式暂不支持在线预览'
}

const resolvePreviewMode = (fileTypeOrName?: string) => {
  const extension = getFileExtension(fileTypeOrName)
  if (['pdf', 'txt', 'md'].includes(extension)) {
    return 'iframe'
  }
  if (['xls', 'xlsx', 'xlsb'].includes(extension)) {
    return 'excel'
  }
  if (extension === 'docx') {
    return 'docx'
  }
  if (['pptx', 'pptm'].includes(extension)) {
    return 'pptx'
  }
  return 'unsupported'
}

const resolveUnsupportedPreviewText = (fileTypeOrName?: string) => {
  const extension = getFileExtension(fileTypeOrName).toUpperCase()
  if (['DOC', 'PPT', 'WPS'].includes(extension)) {
    return `${extension} 是旧版 Office 格式，当前在线预览组件暂不支持；请转换为 DOCX、PPTX 后预览。`
  }
  return '当前文件格式暂不支持在线预览'
}

const getFileExtension = (fileTypeOrName?: string) => {
  if (!fileTypeOrName) {
    return ''
  }
  const value = fileTypeOrName.toLowerCase()
  const dotIndex = value.lastIndexOf('.')
  return dotIndex >= 0 ? value.substring(dotIndex + 1) : value
}

const handleOfficePreviewError = () => {
  message.error('Office 文件预览失败，请确认文件格式和内容是否有效')
}

const setActionLoading = (action: string, id: number, loadingValue: boolean) => {
  actionLoading[`${action}-${id}`] = loadingValue
}

const getActionLoading = (action: string, id?: number) => {
  return id ? actionLoading[`${action}-${id}`] === true : false
}

const canManualParse = (row: AiDocumentVO) => {
  return (
    row.parseStatus !== DocumentParseStatusEnum.RUNNING &&
    row.parseStatus !== DocumentParseStatusEnum.SUCCESS
  )
}

const canManualEmbed = (row: AiDocumentVO) => {
  return (
    row.parseStatus === DocumentParseStatusEnum.SUCCESS &&
    row.embeddingStatus !== DocumentEmbeddingStatusEnum.RUNNING &&
    row.embeddingStatus !== DocumentEmbeddingStatusEnum.SUCCESS
  )
}

const getKnowledgeName = (knowledgeBaseId?: number) => {
  return (
    knowledgeOptions.value.find((item) => item.id === knowledgeBaseId)?.name ||
    knowledgeBaseId ||
    '-'
  )
}

const getDirectoryName = (directoryId?: number) => {
  if (!directoryId) {
    return '-'
  }
  return directoryNameMap.value.get(directoryId) || directoryId
}

const getParseStatusLabel = (status?: number) => {
  return parseStatusOptions.find((item) => item.value === status)?.label || '-'
}

const getParseStatusTagType = (status?: number) => {
  return parseStatusOptions.find((item) => item.value === status)?.type || 'info'
}

const getEmbeddingStatusLabel = (status?: number) => {
  return embeddingStatusOptions.find((item) => item.value === status)?.label || '-'
}

const getEmbeddingStatusTagType = (status?: number) => {
  return embeddingStatusOptions.find((item) => item.value === status)?.type || 'info'
}

const formatFileSize = (size?: number) => {
  if (!size && size !== 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
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
  initDefaultKnowledge()
  await getDirectoryOptions(queryParams.knowledgeBaseId)
  await getList()
})

onBeforeUnmount(() => {
  releasePreviewUrl()
})
</script>

<style scoped>
.document-page {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 12px;
}

.directory-panel {
  min-height: calc(100vh - 180px);
}

.knowledge-selector {
  margin-bottom: 14px;
}

.selector-label {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.directory-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
}

.document-content {
  min-width: 0;
}

.document-file-link {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 32px;
}

.preview-dialog-body {
  min-height: 420px;
}

.preview-frame {
  width: 100%;
  height: 72vh;
  border: 0;
  background: #fff;
}

.preview-frame.is-fullscreen {
  height: calc(100vh - 118px);
}

.office-preview {
  width: 100%;
  height: 72vh;
  overflow: auto;
  background: #fff;
}

.office-preview.is-fullscreen {
  height: calc(100vh - 118px);
}
</style>
