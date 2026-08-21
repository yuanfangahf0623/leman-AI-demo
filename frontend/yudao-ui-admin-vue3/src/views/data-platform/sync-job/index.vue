<template>
  <ContentWrap>
    <el-alert
      title="任务由现有 SeaTunnel 执行；字段映射会自动控制源字段顺序和 Doris 写入字段，旧任务仍可使用手工 SQL。"
      type="info"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <el-form ref="queryRef" :model="query" inline class="-mb-15px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" />
      </el-form-item>
      <el-form-item label="模式" prop="syncMode">
        <el-select v-model="query.syncMode" clearable class="!w-160px">
          <el-option label="全量" value="FULL" />
          <el-option label="增量" value="INCREMENTAL" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="search"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain @click="open()" v-hasPermi="['data-platform:sync-job:create']">
          <Icon icon="ep:plus" class="mr-5px" />新增任务
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="源数据源" width="170">
        <template #default="scope">{{ sourceName(scope.row.sourceDataSourceId) }}</template>
      </el-table-column>
      <el-table-column label="目标" min-width="190">
        <template #default="scope">{{ scope.row.targetDatabase }}.{{ scope.row.targetTable }}</template>
      </el-table-column>
      <el-table-column label="字段映射" width="100">
        <template #default="scope">
          <el-tag :type="enabledMappingCount(scope.row) ? 'success' : 'info'">
            {{ enabledMappingCount(scope.row) || '手工 SQL' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="syncMode" label="模式" width="110">
        <template #default="scope"><el-tag>{{ scope.row.syncMode }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="parallelism" label="并行度" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">
            {{ scope.row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="success" @click="execute(scope.row.id)" v-hasPermi="['data-platform:sync-job:execute']">执行</el-button>
          <el-button link type="primary" @click="open(scope.row.id)" v-hasPermi="['data-platform:sync-job:update']">编辑</el-button>
          <el-button link type="danger" @click="remove(scope.row.id)" v-hasPermi="['data-platform:sync-job:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <Dialog v-model="visible" :title="form.id ? '编辑同步任务' : '新增同步任务'" width="1180px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="源数据源" prop="sourceDataSourceId">
            <el-select v-model="form.sourceDataSourceId" filterable class="!w-100%" @change="onSourceChange">
              <el-option v-for="item in sources" :key="item.id" :label="`${item.name} (${item.type})`" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标 Doris" prop="targetDataSourceId">
            <el-select v-model="form.targetDataSourceId" filterable class="!w-100%">
              <el-option v-for="item in dorisSources" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标分层" prop="targetDatabase">
            <el-select v-model="form.targetDatabase" class="!w-100%" @change="onTargetDatabaseChange">
              <el-option v-for="item in layers" :key="item" :label="item.toUpperCase()" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标表" prop="targetTable">
            <el-select
              v-model="form.targetTable"
              filterable
              allow-create
              default-first-option
              class="!w-100%"
              placeholder="选择已有表或输入表名"
              @change="onTargetTableChange"
            >
              <el-option v-for="item in targetTables" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="同步模式" prop="syncMode">
            <el-radio-group v-model="form.syncMode"><el-radio value="FULL">全量</el-radio><el-radio value="INCREMENTAL">增量</el-radio></el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="并行度"><el-input-number v-model="form.parallelism" :min="1" :max="16" /></el-form-item></el-col>
        <el-col v-if="form.syncMode === 'INCREMENTAL'" :span="12"><el-form-item label="水位字段" prop="watermarkColumn"><el-input v-model="form.watermarkColumn" /></el-form-item></el-col>
        <el-col v-if="form.syncMode === 'INCREMENTAL'" :span="12"><el-form-item label="当前水位"><el-input v-model="form.watermarkValue" /></el-form-item></el-col>
        <el-col :span="24">
          <el-form-item label="Source SQL" prop="sourceSql">
            <el-input v-model="form.sourceSql" type="textarea" :rows="5" placeholder="只允许单条 SELECT，例如：SELECT employee_id, employee_name FROM employee" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">字段映射</el-divider>
      <div class="mb-12px flex flex-wrap items-center gap-8px">
        <el-button type="primary" plain :loading="readingSource" @click="readSourceFields"><Icon icon="ep:download" class="mr-5px" />读取源字段</el-button>
        <el-button plain :loading="readingTarget" @click="readTargetFields(true)"><Icon icon="ep:download" class="mr-5px" />读取目标字段并匹配</el-button>
        <el-button plain :disabled="!sourceColumns.length || !targetColumns.length" @click="autoMatch"><Icon icon="ep:connection" class="mr-5px" />自动同名匹配</el-button>
        <el-button plain @click="addMapping"><Icon icon="ep:plus" class="mr-5px" />增加一行</el-button>
        <el-tag type="info">源字段 {{ sourceColumns.length }}</el-tag>
        <el-tag type="success">已启用 {{ enabledMappingCount(form) }}</el-tag>
      </div>

      <el-alert
        v-if="selectedSource?.category === 'API'"
        title="API 数据源的接口对象与字段读取将在下一步接入；当前可先手工维护字段映射。"
        type="warning"
        show-icon
        :closable="false"
        class="mb-12px"
      />

      <el-table :data="form.fieldMappings" border max-height="390px" empty-text="读取源字段和目标字段后自动生成映射">
        <el-table-column label="启用" width="70" align="center"><template #default="scope"><el-switch v-model="scope.row.enabled" /></template></el-table-column>
        <el-table-column label="源字段" min-width="190">
          <template #default="scope">
            <el-select v-model="scope.row.sourceField" filterable clearable class="!w-100%" @change="syncSourceType(scope.row)">
              <el-option v-for="column in sourceColumns" :key="column.name" :label="`${column.name} (${column.type})`" :value="column.name" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="源类型" width="120" show-overflow-tooltip />
        <el-table-column label="转换" width="110">
          <template #default="scope">
            <el-select v-model="scope.row.transform" class="!w-100%"><el-option label="不转换" value="NONE" /><el-option label="去空格" value="TRIM" /><el-option label="二进制转 HEX" value="HEX" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标字段" min-width="190">
          <template #default="scope">
            <el-select v-model="scope.row.targetField" filterable allow-create clearable class="!w-100%" @change="syncTargetMeta(scope.row)">
              <el-option v-for="column in targetColumns" :key="column.name" :label="`${column.name} (${column.type})`" :value="column.name" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="目标类型" width="120" show-overflow-tooltip />
        <el-table-column label="必填" width="65" align="center">
          <template #default="scope"><el-tag v-if="scope.row.required" type="danger" size="small">是</el-tag><span v-else>否</span></template>
        </el-table-column>
        <el-table-column label="空值默认值" min-width="135"><template #default="scope"><el-input v-model="scope.row.defaultValue" clearable placeholder="不处理" /></template></el-table-column>
        <el-table-column label="操作" width="65" align="center"><template #default="scope"><el-button link type="danger" @click="form.fieldMappings?.splice(scope.$index, 1)">删除</el-button></template></el-table-column>
      </el-table>

      <el-row :gutter="16" class="mt-16px">
        <el-col :span="24">
          <el-form-item label="Sink SQL" prop="sinkSql">
            <el-input v-model="form.sinkSql" type="textarea" :rows="4" :readonly="enabledMappingCount(form) > 0" :placeholder="enabledMappingCount(form) ? '根据字段映射自动生成' : '未使用字段映射时必须手工填写'" />
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <template #footer><el-button type="primary" :loading="saving" @click="save">保存</el-button><el-button @click="visible = false">取消</el-button></template>
  </Dialog>
</template>

<script setup lang="ts">
import {
  DataPlatformApi,
  DataSourceColumnVO,
  DataSourceVO,
  SyncFieldMappingVO,
  SyncJobVO,
  WarehouseColumnVO
} from '@/api/data-platform'

defineOptions({ name: 'DataPlatformSyncJob' })
const message = useMessage()
const layers = ['ods', 'dwd', 'dim', 'dws', 'ads', 'tmp']
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const readingSource = ref(false)
const readingTarget = ref(false)
const list = ref<SyncJobVO[]>([])
const sources = ref<DataSourceVO[]>([])
const sourceColumns = ref<DataSourceColumnVO[]>([])
const targetColumns = ref<WarehouseColumnVO[]>([])
const targetTables = ref<string[]>([])
const total = ref(0)
const queryRef = ref()
const formRef = ref()
const query = reactive({ pageNo: 1, pageSize: 10, name: undefined, syncMode: undefined })
const emptyForm = (): SyncJobVO => ({ name: '', code: '', sourceDataSourceId: 0, sourceSql: '', targetDataSourceId: 0, targetDatabase: 'ods', targetTable: '', sinkSql: '', fieldMappings: [], syncMode: 'FULL', parallelism: 1, status: 0 })
const form = reactive<SyncJobVO>(emptyForm())
const dorisSources = computed(() => sources.value.filter((item) => item.type === 'DORIS'))
const selectedSource = computed(() => sources.value.find((item) => item.id === form.sourceDataSourceId))
const enabledMappingCount = (job: SyncJobVO) => (job.fieldMappings || []).filter((item) => item.enabled !== false).length
const rules = {
  name: [{ required: true, message: '请输入名称' }],
  code: [{ required: true, pattern: /^[a-z][a-z0-9_]{1,63}$/, message: '编码格式不正确' }],
  sourceDataSourceId: [{ required: true, message: '请选择源数据源' }],
  targetDataSourceId: [{ required: true, message: '请选择目标 Doris' }],
  targetDatabase: [{ required: true, message: '请选择目标分层' }],
  targetTable: [{ required: true, message: '请选择或输入目标表' }],
  sourceSql: [{ required: true, message: '请输入只读 Source SQL' }],
  sinkSql: [{ validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => { if (!enabledMappingCount(form) && !value?.trim()) callback(new Error('请配置字段映射或填写 Sink SQL')); else callback() }, trigger: 'blur' }]
}

const loadSources = async () => { const data = await DataPlatformApi.getDataSourcePage({ pageNo: 1, pageSize: 100, status: 0 }); sources.value = data.list }
const load = async () => { loading.value = true; try { const data = await DataPlatformApi.getSyncJobPage(query); list.value = data.list; total.value = data.total } finally { loading.value = false } }
const loadTargetTables = async () => { if (!form.targetDatabase) return; try { const data = await DataPlatformApi.getWarehouseTables(form.targetDatabase); targetTables.value = data.map((item: { table: string }) => item.table) } catch { targetTables.value = [] } }
const search = () => { query.pageNo = 1; load() }
const reset = () => { queryRef.value?.resetFields(); search() }
const sourceName = (id: number) => sources.value.find((item) => item.id === id)?.name || id

const open = async (id?: number) => {
  Object.assign(form, emptyForm())
  sourceColumns.value = []
  targetColumns.value = []
  if (id) {
    const data = await DataPlatformApi.getSyncJob(id)
    Object.assign(form, data, { fieldMappings: data.fieldMappings || [] })
    sourceColumns.value = uniqueSourceColumns(data.fieldMappings || [])
    targetColumns.value = uniqueTargetColumns(data.fieldMappings || [])
  }
  await loadTargetTables()
  visible.value = true
}

const readSourceFields = async () => {
  if (!form.sourceDataSourceId || !form.sourceSql.trim()) { message.warning('请先选择源数据源并填写 Source SQL'); return }
  if (selectedSource.value?.category === 'API') { message.warning('API 数据源暂不支持通过 SQL 读取字段，请先手工增加映射行'); return }
  readingSource.value = true
  try {
    sourceColumns.value = await DataPlatformApi.getDataSourceQueryColumns(form.sourceDataSourceId, form.sourceSql)
    if (targetColumns.value.length) autoMatch()
    message.success(`已读取 ${sourceColumns.value.length} 个源字段`)
  } finally { readingSource.value = false }
}

const readTargetFields = async (matchAfterRead = false) => {
  if (!form.targetDatabase || !form.targetTable) { message.warning('请先选择目标分层和目标表'); return }
  readingTarget.value = true
  try {
    targetColumns.value = await DataPlatformApi.getWarehouseColumns(form.targetDatabase, form.targetTable)
    if (matchAfterRead && sourceColumns.value.length) autoMatch()
    message.success(`已读取 ${targetColumns.value.length} 个目标字段`)
  } finally { readingTarget.value = false }
}

const normalizedField = (value: string) => value.replace(/[_\-\s]/g, '').toLowerCase()
const autoMatch = () => {
  if (!sourceColumns.value.length || !targetColumns.value.length) { message.warning('请先读取源字段和目标字段'); return }
  const oldMappings = new Map((form.fieldMappings || []).map((item) => [item.targetField, item]))
  form.fieldMappings = targetColumns.value.map((target) => {
    const source = sourceColumns.value.find((item) => normalizedField(item.name) === normalizedField(target.name))
    const old = oldMappings.get(target.name)
    return { sourceField: source?.name || old?.sourceField || '', sourceType: source?.type || old?.sourceType || '', targetField: target.name, targetType: target.type, enabled: Boolean(source || old?.sourceField) && old?.enabled !== false, required: !target.nullable && target.defaultValue == null, defaultValue: old?.defaultValue, transform: old?.transform || 'NONE' }
  })
  updateSinkSql()
}

const addMapping = () => { form.fieldMappings ||= []; form.fieldMappings.push({ sourceField: '', sourceType: '', targetField: '', targetType: '', enabled: true, required: false, transform: 'NONE' }) }
const syncSourceType = (mapping: SyncFieldMappingVO) => { mapping.sourceType = sourceColumns.value.find((item) => item.name === mapping.sourceField)?.type || ''; updateSinkSql() }
const syncTargetMeta = (mapping: SyncFieldMappingVO) => { const target = targetColumns.value.find((item) => item.name === mapping.targetField); mapping.targetType = target?.type || mapping.targetType || ''; mapping.required = target ? !target.nullable && target.defaultValue == null : false; updateSinkSql() }
const updateSinkSql = () => {
  const mappings = (form.fieldMappings || []).filter((item) => item.enabled !== false && item.targetField)
  if (!mappings.length || !form.targetDatabase || !form.targetTable) return
  const fields = mappings.map((item) => `\`${item.targetField}\``).join(', ')
  form.sinkSql = `INSERT INTO \`${form.targetDatabase}\`.\`${form.targetTable}\` (${fields}) VALUES (${mappings.map(() => '?').join(', ')})`
}

const uniqueSourceColumns = (mappings: SyncFieldMappingVO[]) => Array.from(new Map(mappings.filter((item) => item.sourceField).map((item, index) => [item.sourceField, { name: item.sourceField, label: item.sourceField, type: item.sourceType || '', jdbcType: 0, nullable: true, ordinal: index + 1 }])).values())
const uniqueTargetColumns = (mappings: SyncFieldMappingVO[]) => Array.from(new Map(mappings.filter((item) => item.targetField).map((item) => [item.targetField, { name: item.targetField, type: item.targetType || '', nullable: !item.required, defaultValue: item.defaultValue }])).values())
const onSourceChange = () => { sourceColumns.value = [] }
const onTargetDatabaseChange = async () => { form.targetTable = ''; targetColumns.value = []; targetTables.value = []; await loadTargetTables() }
const onTargetTableChange = () => { targetColumns.value = []; updateSinkSql() }

const save = async () => {
  updateSinkSql()
  await formRef.value.validate()
  const enabledMappings = (form.fieldMappings || []).filter((item) => item.enabled !== false)
  if (form.fieldMappings?.length && !enabledMappings.length) { message.warning('字段映射至少启用一个字段，或清空映射后使用手工 Sink SQL'); return }
  if (enabledMappings.some((item) => !item.sourceField || !item.targetField)) { message.warning('启用的映射行必须选择源字段和目标字段'); return }
  saving.value = true
  try { form.id ? await DataPlatformApi.updateSyncJob(form) : await DataPlatformApi.createSyncJob(form); message.success('保存成功'); visible.value = false; await load() } finally { saving.value = false }
}
const execute = async (id?: number) => { if (!id) return; try { await message.confirm('确认立即执行该同步任务？'); const runId = await DataPlatformApi.executeSyncJob(id); message.success(`任务已提交，运行编号 ${runId}`) } catch {} }
const remove = async (id?: number) => { if (!id) return; try { await message.delConfirm(); await DataPlatformApi.deleteSyncJob(id); message.success('删除成功'); await load() } catch {} }
watch(() => form.fieldMappings, () => updateSinkSql(), { deep: true })
onMounted(async () => { await loadSources(); await load() })
</script>
