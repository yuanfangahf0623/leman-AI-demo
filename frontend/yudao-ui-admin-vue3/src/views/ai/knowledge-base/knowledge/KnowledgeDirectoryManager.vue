<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="900">
    <div class="directory-toolbar">
      <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['ai:knowledge:update']">
        <Icon icon="ep:plus" class="mr-5px" /> 新增根目录
      </el-button>
      <el-button @click="getList"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="directoryTree"
      row-key="id"
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column label="目录名称" prop="name" min-width="220" />
      <el-table-column label="排序" prop="sort" width="100" />
      <el-table-column label="状态" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === CommonStatusEnum.ENABLE ? 'success' : 'info'">
            {{ scope.row.status === CommonStatusEnum.ENABLE ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
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
            @click="openForm('create', undefined, scope.row.id)"
            v-hasPermi="['ai:knowledge:update']"
          >
            新增子目录
          </el-button>
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row)"
            v-hasPermi="['ai:knowledge:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['ai:knowledge:update']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Dialog v-model="formVisible" :title="formTitle" width="520">
      <el-form
        ref="formRef"
        v-loading="formLoading"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="上级目录" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentOptions"
            :props="defaultProps"
            check-strictly
            node-key="id"
            class="!w-1/1"
          />
        </el-form-item>
        <el-form-item label="目录名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入目录名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="formData.sort" :min="0" :max="9999" :precision="0" class="!w-1/1" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="CommonStatusEnum.ENABLE">启用</el-radio>
            <el-radio :label="CommonStatusEnum.DISABLE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="formVisible = false">取 消</el-button>
      </template>
    </Dialog>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { dateFormatter } from '@/utils/formatTime'
import { defaultProps, handleTree } from '@/utils/tree'
import { CommonStatusEnum } from '@/utils/constants'
import {
  AiKnowledgeDirectoryApi,
  AiKnowledgeDirectoryVO,
  AiKnowledgeVO
} from '@/api/ai/knowledge'

defineOptions({ name: 'AiKnowledgeDirectoryManager' })

const message = useMessage()
const { t } = useI18n()

const dialogVisible = ref(false)
const loading = ref(false)
const currentKnowledge = ref<AiKnowledgeVO>()
const directoryTree = ref<AiKnowledgeDirectoryVO[]>([])
const formVisible = ref(false)
const formLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const formData = ref<AiKnowledgeDirectoryVO>({
  knowledgeBaseId: 0,
  parentId: 0,
  name: '',
  sort: 0,
  status: CommonStatusEnum.ENABLE
})
const formRules = reactive<FormRules>({
  parentId: [{ required: true, message: '上级目录不能为空', trigger: 'change' }],
  name: [{ required: true, message: '目录名称不能为空', trigger: 'blur' }]
})

const dialogTitle = computed(() => `目录管理 - ${currentKnowledge.value?.name || ''}`)
const formTitle = computed(() => (formType.value === 'create' ? '新增目录' : '编辑目录'))
const parentOptions = computed(() => [
  {
    id: 0,
    name: '根目录',
    children: directoryTree.value
  }
])

const open = async (knowledge: AiKnowledgeVO) => {
  currentKnowledge.value = knowledge
  dialogVisible.value = true
  await getList()
}
defineExpose({ open })

const getList = async () => {
  if (!currentKnowledge.value?.id) return
  loading.value = true
  try {
    const data = await AiKnowledgeDirectoryApi.getDirectoryList({
      knowledgeBaseId: currentKnowledge.value.id
    })
    directoryTree.value = handleTree(data, 'id', 'parentId')
  } finally {
    loading.value = false
  }
}

const openForm = (type: 'create' | 'update', row?: AiKnowledgeDirectoryVO, parentId = 0) => {
  formVisible.value = true
  formType.value = type
  resetForm()
  if (type === 'create') {
    formData.value.parentId = parentId
    return
  }
  formData.value = {
    ...formData.value,
    ...row,
    parentId: row?.parentId ?? 0,
    status: row?.status ?? CommonStatusEnum.ENABLE
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate()
  if (!valid || !currentKnowledge.value?.id) return
  formLoading.value = true
  try {
    const data = {
      ...formData.value,
      knowledgeBaseId: currentKnowledge.value.id,
      parentId: formData.value.parentId ?? 0
    }
    if (formType.value === 'create') {
      await AiKnowledgeDirectoryApi.createDirectory(data)
      message.success(t('common.createSuccess'))
    } else {
      await AiKnowledgeDirectoryApi.updateDirectory(data)
      message.success(t('common.updateSuccess'))
    }
    formVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async (id?: number) => {
  if (!id) return
  try {
    await message.delConfirm()
    await AiKnowledgeDirectoryApi.deleteDirectory(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const resetForm = () => {
  formData.value = {
    id: undefined,
    knowledgeBaseId: currentKnowledge.value?.id || 0,
    parentId: 0,
    name: '',
    sort: 0,
    status: CommonStatusEnum.ENABLE
  }
  formRef.value?.resetFields()
}
</script>

<style scoped>
.directory-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
