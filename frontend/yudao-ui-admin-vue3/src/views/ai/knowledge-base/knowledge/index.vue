<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      class="-mb-15px"
      :model="queryParams"
      :inline="true"
      label-width="90px"
    >
      <el-form-item label="知识库名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入知识库名称"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="知识库编码" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入知识库编码"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="向量库类型" prop="vectorStoreType">
        <el-select
          v-model="queryParams.vectorStoreType"
          placeholder="请选择向量库类型"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="item in vectorStoreTypeOptions"
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
          v-hasPermi="['ai:knowledge:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="编号" align="center" prop="id" width="90" />
      <el-table-column label="知识库名称" align="center" prop="name" min-width="140" />
      <el-table-column label="知识库编码" align="center" prop="code" min-width="140" />
      <el-table-column label="可见范围" align="center" min-width="100">
        <template #default="scope">
          <el-tag>{{ getVisibilityLabel(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="向量库类型" align="center" prop="vectorStoreType" min-width="160">
        <template #default="scope">
          {{ getVectorStoreTypeLabel(scope.row.vectorStoreType) }}
        </template>
      </el-table-column>
      <el-table-column label="Embedding 模型" align="center" prop="embeddingModel" min-width="180" />
      <el-table-column label="Chat 模型" align="center" prop="chatModel" min-width="160">
        <template #default="scope">
          {{ scope.row.chatModel || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="切片配置" align="center" min-width="120">
        <template #default="scope">
          {{ scope.row.chunkSize }} / {{ scope.row.chunkOverlap }}
        </template>
      </el-table-column>
      <el-table-column label="召回配置" align="center" min-width="120">
        <template #default="scope">
          {{ scope.row.topK }} / {{ scope.row.scoreThreshold ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="文档数" align="center" prop="documentCount" width="90" />
      <el-table-column label="切片数" align="center" prop="chunkCount" width="90" />
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
            @click="openDirectoryManager(scope.row)"
            v-hasPermi="['ai:knowledge:update']"
          >
            目录管理
          </el-button>
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['ai:knowledge:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:knowledge:delete']"
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

  <KnowledgeForm ref="formRef" @success="getList" />
  <KnowledgeDirectoryManager ref="directoryManagerRef" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { AiKnowledgeApi, AiKnowledgePageReqVO, AiKnowledgeVO } from '@/api/ai/knowledge'
import KnowledgeForm from './KnowledgeForm.vue'
import KnowledgeDirectoryManager from './KnowledgeDirectoryManager.vue'

defineOptions({ name: 'AiKnowledgeManage' })

const message = useMessage()
const { t } = useI18n()

const vectorStoreTypeOptions = [
  { label: 'PostgreSQL + pgvector', value: 'pgvector' },
  { label: 'Qdrant', value: 'qdrant' }
]
const visibilityOptions = [
  { label: '私有', value: 'private' },
  { label: '团队可见', value: 'team' },
  { label: '公开', value: 'public' }
]

const loading = ref(true)
const list = ref<AiKnowledgeVO[]>([])
const total = ref(0)
const queryParams = reactive<AiKnowledgePageReqVO>({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  code: undefined,
  vectorStoreType: undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await AiKnowledgeApi.getKnowledgePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
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
  formRef.value.open(type, id)
}

const directoryManagerRef = ref()
const openDirectoryManager = (row: AiKnowledgeVO) => {
  directoryManagerRef.value.open(row)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await AiKnowledgeApi.deleteKnowledge(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const getVectorStoreTypeLabel = (value?: string) => {
  return vectorStoreTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

const getVisibilityLabel = (row: AiKnowledgeVO) => {
  const visibility = row.visibility || (row.departmentIds && row.departmentIds !== '*' ? 'team' : 'public')
  return visibilityOptions.find((item) => item.value === visibility)?.label || '-'
}

onMounted(() => {
  getList()
})
</script>
