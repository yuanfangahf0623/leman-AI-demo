<template>
  <ContentWrap>
    <el-form ref="queryRef" :model="query" inline class="-mb-15px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="query.type" clearable class="!w-180px">
          <el-option-group
            v-for="group in typeGroups"
            :key="group.category"
            :label="group.label"
          >
            <el-option
              v-for="item in group.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="search"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          @click="open()"
          v-hasPermi="['data-platform:datasource:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增数据源
        </el-button>
        <el-button
          type="success"
          plain
          @click="openTwoHaoHr()"
          v-hasPermi="['data-platform:datasource:create']"
        >
          <Icon icon="ep:connection" class="mr-5px" />新增2号人事部
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">
          <el-tag :type="row.category === 'API' ? 'warning' : 'success'">
            {{ categoryLabel(row.category || categoryByType(row.type)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="150">
        <template #default="{ row }">
          <el-tag>{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="连接地址" min-width="260">
        <template #default="{ row }">{{ addressText(row) }}</template>
      </el-table-column>
      <el-table-column prop="username" :label="accountColumnLabel" width="150" />
      <el-table-column label="密钥" width="90">
        <template #default="{ row }">{{ row.passwordConfigured ? '已配置' : '未配置' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" @click="test(row)">测试</el-button>
          <el-button
            link
            type="primary"
            @click="open(row.id)"
            v-hasPermi="['data-platform:datasource:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="remove(row.id)"
            v-hasPermi="['data-platform:datasource:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="visible" :title="form.id ? '编辑数据源' : '新增数据源'" width="760px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" class="!w-100%" @change="setDefaultTypeConfig">
              <el-option-group
                v-for="group in typeGroups"
                :key="group.category"
                :label="group.label"
              >
                <el-option
                  v-for="item in group.options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-option-group>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio :value="0">启用</el-radio>
              <el-radio :value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item :label="isTwoHaoHr ? '接口地址' : '主机'" prop="host">
            <el-input
              v-model="form.host"
              :placeholder="isTwoHaoHr ? 'https://openapi.2haohr.com' : ''"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" class="!w-100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="isTwoHaoHr ? '企业 ID' : '数据库'" prop="databaseName">
            <el-input
              v-model="form.databaseName"
              :placeholder="isTwoHaoHr ? 'corp_id' : ''"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="isTwoHaoHr ? '应用 ID' : '用户名'" prop="username">
            <el-input v-model="form.username" :placeholder="isTwoHaoHr ? 'app_id' : ''" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="passwordLabel" :prop="form.id ? '' : 'password'">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :placeholder="form.id ? '留空则保持原密钥' : ''"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item :label="isTwoHaoHr ? '高级配置' : 'JDBC 参数'">
            <el-input
              v-model="form.jdbcParams"
              :type="isTwoHaoHr ? 'textarea' : 'text'"
              :rows="isTwoHaoHr ? 5 : undefined"
              :placeholder="paramsPlaceholder"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :loading="testing" @click="test(form)">测试连接</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      <el-button @click="visible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { DataPlatformApi, DataSourceVO } from '@/api/data-platform'

defineOptions({ name: 'DataPlatformDatasource' })

const message = useMessage()

const typeGroups = [
  {
    label: '数据库',
    category: 'DATABASE',
    options: [
      { label: 'MySQL', value: 'MYSQL' },
      { label: 'PostgreSQL', value: 'POSTGRESQL' },
      { label: 'SQL Server', value: 'SQLSERVER' },
      { label: 'Doris', value: 'DORIS' }
    ]
  },
  {
    label: 'API',
    category: 'API',
    options: [{ label: '2号人事部 API', value: 'TWO_HAO_HR' }]
  }
]
const typeOptions = typeGroups.flatMap((group) =>
  group.options.map((item) => ({ ...item, category: group.category }))
)
const ports: Record<string, number> = {
  MYSQL: 3306,
  POSTGRESQL: 5432,
  SQLSERVER: 1433,
  DORIS: 9030,
  TWO_HAO_HR: 443
}
const twoHaoHrParams = JSON.stringify(
  {
    syncObjects: ['departments', 'employees'],
    pageSize: 100,
    maxPages: 10
  },
  null,
  2
)

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const visible = ref(false)
const list = ref<DataSourceVO[]>([])
const total = ref(0)
const queryRef = ref()
const formRef = ref()
const query = reactive({ pageNo: 1, pageSize: 10, name: undefined, type: undefined })
const emptyForm = (): DataSourceVO => ({
  name: '',
  code: '',
  type: 'MYSQL',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  status: 0
})
const form = reactive<DataSourceVO>(emptyForm())
const isTwoHaoHr = computed(() => form.type === 'TWO_HAO_HR')
const passwordLabel = computed(() => (isTwoHaoHr.value ? '应用密钥' : form.id ? '新密码' : '密码'))
const paramsPlaceholder = computed(() =>
  isTwoHaoHr.value ? '可选 JSON，例如同步对象、分页大小；不要填写密钥或 token' : '可选，不要填写密码'
)
const accountColumnLabel = computed(() => '账号')

const rules = {
  name: [{ required: true, message: '请输入名称' }],
  code: [
    { required: true, pattern: /^[a-z][a-z0-9_]{1,63}$/, message: '编码格式不正确' }
  ],
  type: [{ required: true, message: '请选择类型' }],
  host: [{ required: true, message: '请输入主机或接口地址' }],
  port: [{ required: true, message: '请输入端口' }],
  databaseName: [{ required: true, message: '请输入数据库或企业 ID' }],
  username: [{ required: true, message: '请输入用户名或应用 ID' }]
}

const typeLabel = (type: string) => typeOptions.find((item) => item.value === type)?.label || type
const categoryByType = (type: string) =>
  typeOptions.find((item) => item.value === type)?.category || 'DATABASE'
const categoryLabel = (category: string) => (category === 'API' ? 'API' : '数据库')
const addressText = (row: DataSourceVO) =>
  row.type === 'TWO_HAO_HR'
    ? `${row.host} / 企业ID：${row.databaseName || '-'}`
    : `${row.host}:${row.port}/${row.databaseName}`

const load = async () => {
  loading.value = true
  try {
    const data = await DataPlatformApi.getDataSourcePage(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  queryRef.value?.resetFields()
  search()
}
const open = async (id?: number) => {
  Object.assign(form, emptyForm())
  if (id) {
    Object.assign(form, await DataPlatformApi.getDataSource(id), { password: '' })
  }
  visible.value = true
}
const openTwoHaoHr = () => {
  Object.assign(form, emptyForm(), {
    name: '2号人事部',
    code: 'twohao_hr_api',
    type: 'TWO_HAO_HR',
    host: 'https://openapi.2haohr.com',
    port: 443,
    jdbcParams: twoHaoHrParams
  })
  visible.value = true
}
const setDefaultTypeConfig = (type: string) => {
  form.port = ports[type]
  if (type === 'TWO_HAO_HR') {
    form.host = form.host || 'https://openapi.2haohr.com'
    form.jdbcParams = form.jdbcParams || twoHaoHrParams
  }
}
const test = async (row: DataSourceVO) => {
  testing.value = true
  try {
    await DataPlatformApi.testDataSource(row)
    message.success('连接成功')
  } finally {
    testing.value = false
  }
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await DataPlatformApi.updateDataSource(form) : await DataPlatformApi.createDataSource(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (id?: number) => {
  if (!id) return
  try {
    await message.delConfirm()
    await DataPlatformApi.deleteDataSource(id)
    message.success('删除成功')
    await load()
  } catch {}
}

onMounted(load)
</script>
