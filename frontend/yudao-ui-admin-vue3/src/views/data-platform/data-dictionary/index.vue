<template>
  <ContentWrap>
    <el-alert
      title="字段字典采用“扫描生成、业务确认”机制：结构信息来自源库；带“待确认”的中文名是规则建议，确认后再次扫描不会覆盖。"
      type="info"
      show-icon
      :closable="false"
    />
    <el-form :inline="true" class="mt-16px">
      <el-form-item label="数据源">
        <el-select v-model="selectedDataSourceId" filterable class="!w-240px" @change="changeDataSource">
          <el-option v-for="item in dataSources" :key="item.id" :label="item.name" :value="item.id!" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          v-hasPermi="['data-platform:data-dictionary:refresh']"
          type="primary"
          :loading="refreshing"
          @click="refreshDictionary"
        >
          <Icon icon="ep:refresh" class="mr-5px" />扫描源库
        </el-button>
      </el-form-item>
      <el-form-item><span class="text-12px text-gray-500">扫描只读取元数据，不读取业务数据，也不会覆盖已确认定义</span></el-form-item>
    </el-form>
  </ContentWrap>

  <el-row :gutter="12" class="mb-12px">
    <el-col v-for="card in summaryCards" :key="card.label" :span="4">
      <el-card shadow="never"><div class="text-13px text-gray-500">{{ card.label }}</div><div class="mt-8px text-24px font-600">{{ card.value }}</div></el-card>
    </el-col>
  </el-row>

  <el-row :gutter="12">
    <el-col :span="9">
      <ContentWrap title="业务表">
        <el-form :inline="true">
          <el-form-item>
            <el-input v-model="tableQuery.keyword" clearable placeholder="表名/中文名" class="!w-180px" @keyup.enter="searchTables" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="tableQuery.definitionStatus" clearable placeholder="定义状态" class="!w-125px" @change="searchTables">
              <el-option label="待确认" value="GENERATED" /><el-option label="已确认" value="CONFIRMED" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button @click="searchTables"><Icon icon="ep:search" /></el-button></el-form-item>
        </el-form>
        <el-table v-loading="tableLoading" :data="tables" highlight-current-row height="560" @row-click="selectTable">
          <el-table-column label="源表/业务名称" min-width="220">
            <template #default="scope">
              <div class="font-500">{{ scope.row.sourceTable }}</div>
              <div class="text-12px text-gray-500">{{ scope.row.businessName || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="businessDomain" label="业务域" width="90" />
          <el-table-column label="确认" width="75" align="center">
            <template #default="scope">{{ scope.row.confirmedFieldCount }}/{{ scope.row.fieldCount }}</template>
          </el-table-column>
          <el-table-column label="操作" width="62" align="center">
            <template #default="scope"><el-button link type="primary" @click.stop="openTableEdit(scope.row)">维护</el-button></template>
          </el-table-column>
        </el-table>
        <Pagination v-model:page="tableQuery.pageNo" v-model:limit="tableQuery.pageSize" :total="tableTotal" @pagination="loadTables" />
      </ContentWrap>
    </el-col>

    <el-col :span="15">
      <ContentWrap :title="selectedTable ? `${selectedTable.sourceTable} 字段字典` : '请选择业务表'">
        <el-form v-if="selectedTable" :inline="true">
          <el-form-item><el-input v-model="fieldQuery.keyword" clearable placeholder="字段/中文名/口径" class="!w-190px" @keyup.enter="searchFields" /></el-form-item>
          <el-form-item>
            <el-select v-model="fieldQuery.definitionStatus" clearable placeholder="定义状态" class="!w-125px" @change="searchFields">
              <el-option label="待确认" value="GENERATED" /><el-option label="已确认" value="CONFIRMED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="fieldQuery.sensitivityLevel" clearable placeholder="敏感等级" class="!w-125px" @change="searchFields">
              <el-option label="公开" value="PUBLIC" /><el-option label="内部" value="INTERNAL" />
              <el-option label="敏感" value="SENSITIVE" /><el-option label="受限" value="RESTRICTED" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button @click="searchFields"><Icon icon="ep:search" /></el-button></el-form-item>
        </el-form>
        <el-table v-loading="fieldLoading" :data="fields" height="560" stripe>
          <el-table-column prop="ordinalPosition" label="#" width="50" />
          <el-table-column label="字段" min-width="150">
            <template #default="scope"><span class="font-500">{{ scope.row.sourceColumn }}</span><el-tag v-if="scope.row.primaryKey" size="small" type="warning" class="ml-5px">PK</el-tag></template>
          </el-table-column>
          <el-table-column prop="businessName" label="业务中文名" min-width="145" show-overflow-tooltip />
          <el-table-column label="类型" min-width="115"><template #default="scope">{{ formatType(scope.row) }}</template></el-table-column>
          <el-table-column label="定义" width="78">
            <template #default="scope"><el-tag size="small" :type="scope.row.definitionStatus === 'CONFIRMED' ? 'success' : 'info'">{{ scope.row.definitionStatus === 'CONFIRMED' ? '已确认' : '待确认' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="敏感" width="72"><template #default="scope"><el-tag size="small" :type="sensitivityType(scope.row.sensitivityLevel)">{{ sensitivityLabel(scope.row.sensitivityLevel) }}</el-tag></template></el-table-column>
          <el-table-column label="增量" width="58" align="center"><template #default="scope">{{ scope.row.incrementalCandidate ? '是' : '-' }}</template></el-table-column>
          <el-table-column label="操作" width="60" fixed="right"><template #default="scope"><el-button link type="primary" @click="openFieldEdit(scope.row)">维护</el-button></template></el-table-column>
        </el-table>
        <Pagination v-if="selectedTable" v-model:page="fieldQuery.pageNo" v-model:limit="fieldQuery.pageSize" :total="fieldTotal" @pagination="loadFields" />
        <el-empty v-else description="在左侧选择一张表查看字段" />
      </ContentWrap>
    </el-col>
  </el-row>

  <Dialog v-model="tableDialog" title="维护业务表定义" width="620px">
    <el-form label-width="100px">
      <el-form-item label="源表">{{ tableForm.sourceSchema }}.{{ tableForm.sourceTable }}</el-form-item>
      <el-form-item label="业务中文名"><el-input v-model="tableForm.businessName" maxlength="128" /></el-form-item>
      <el-form-item label="业务域"><el-input v-model="tableForm.businessDomain" maxlength="64" /></el-form-item>
      <el-form-item label="业务说明"><el-input v-model="tableForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button @click="tableDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTable">确认定义</el-button></template>
  </Dialog>

  <Dialog v-model="fieldDialog" title="维护字段定义" width="680px">
    <el-form label-width="110px">
      <el-form-item label="源字段"><span class="font-500">{{ fieldForm.sourceColumn }}</span><span class="ml-10px text-gray-500">{{ formatType(fieldForm as MetadataFieldVO) }}</span></el-form-item>
      <el-form-item label="源库备注">{{ fieldForm.sourceComment || '源库无备注' }}</el-form-item>
      <el-form-item label="业务中文名" required><el-input v-model="fieldForm.businessName" maxlength="128" /></el-form-item>
      <el-form-item label="业务口径"><el-input v-model="fieldForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="说明字段含义、统计口径、值域或单位" /></el-form-item>
      <el-form-item label="数据分类"><el-input v-model="fieldForm.classification" maxlength="64" placeholder="例如：主数据、财务信息、联系信息" /></el-form-item>
      <el-form-item label="敏感等级" required><el-radio-group v-model="fieldForm.sensitivityLevel"><el-radio-button label="PUBLIC">公开</el-radio-button><el-radio-button label="INTERNAL">内部</el-radio-button><el-radio-button label="SENSITIVE">敏感</el-radio-button><el-radio-button label="RESTRICTED">受限</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="增量时间候选"><el-switch v-model="fieldForm.incrementalCandidate" /><span class="ml-8px text-12px text-gray-500">标记可用于定时增量同步的业务时间字段</span></el-form-item>
    </el-form>
    <template #footer><el-button @click="fieldDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveField">确认定义</el-button></template>
  </Dialog>
</template>

<script setup lang="ts">
import { DataPlatformApi, DataSourceVO, MetadataFieldVO, MetadataSummaryVO, MetadataTableVO } from '@/api/data-platform'

defineOptions({ name: 'DataPlatformDataDictionary' })
const message = useMessage()
const dataSources = ref<DataSourceVO[]>([])
const selectedDataSourceId = ref<number>()
const tables = ref<MetadataTableVO[]>([])
const fields = ref<MetadataFieldVO[]>([])
const selectedTable = ref<MetadataTableVO>()
const summary = ref<MetadataSummaryVO>({ tableCount: 0, fieldCount: 0, sourceCommentCount: 0, namedCount: 0, describedCount: 0, confirmedCount: 0, sensitiveCount: 0, confirmedCoverage: 0, descriptionCoverage: 0 })
const tableTotal = ref(0), fieldTotal = ref(0)
const tableLoading = ref(false), fieldLoading = ref(false), refreshing = ref(false), saving = ref(false)
const tableDialog = ref(false), fieldDialog = ref(false)
const tableQuery = reactive({ pageNo: 1, pageSize: 20, keyword: '', definitionStatus: '' })
const fieldQuery = reactive({ pageNo: 1, pageSize: 20, keyword: '', definitionStatus: '', sensitivityLevel: '' })
const tableForm = reactive<Partial<MetadataTableVO>>({})
const fieldForm = reactive<Partial<MetadataFieldVO>>({ sensitivityLevel: 'INTERNAL', incrementalCandidate: false })

const summaryCards = computed(() => [
  { label: '业务表', value: summary.value.tableCount.toLocaleString() },
  { label: '业务字段', value: summary.value.fieldCount.toLocaleString() },
  { label: '源库备注', value: summary.value.sourceCommentCount.toLocaleString() },
  { label: '已确认字段', value: summary.value.confirmedCount.toLocaleString() },
  { label: '确认覆盖率', value: `${Number(summary.value.confirmedCoverage || 0).toFixed(2)}%` },
  { label: '敏感/受限', value: summary.value.sensitiveCount.toLocaleString() }
])

const loadSummary = async () => { if (selectedDataSourceId.value) summary.value = await DataPlatformApi.getMetadataSummary(selectedDataSourceId.value) }
const loadTables = async () => {
  if (!selectedDataSourceId.value) return
  tableLoading.value = true
  try {
    const data = await DataPlatformApi.getMetadataTablePage({ ...tableQuery, dataSourceId: selectedDataSourceId.value })
    tables.value = data.list
    tableTotal.value = data.total
  } finally { tableLoading.value = false }
}
const loadFields = async () => {
  if (!selectedTable.value) return
  fieldLoading.value = true
  try {
    const data = await DataPlatformApi.getMetadataFieldPage({ ...fieldQuery, metadataTableId: selectedTable.value.id })
    fields.value = data.list
    fieldTotal.value = data.total
  } finally { fieldLoading.value = false }
}
const changeDataSource = async () => { selectedTable.value = undefined; fields.value = []; tableQuery.pageNo = 1; await Promise.all([loadSummary(), loadTables()]) }
const searchTables = async () => { tableQuery.pageNo = 1; await loadTables() }
const searchFields = async () => { fieldQuery.pageNo = 1; await loadFields() }
const selectTable = async (row: MetadataTableVO) => { selectedTable.value = row; fieldQuery.pageNo = 1; await loadFields() }
const refreshDictionary = async () => {
  if (!selectedDataSourceId.value) return
  await message.confirm('将只读扫描源库表和字段，并保留所有已确认的业务定义。是否继续？')
  refreshing.value = true
  try {
    const result = await DataPlatformApi.refreshMetadata(selectedDataSourceId.value)
    message.success(`扫描完成：${result.tableCount} 张表、${result.fieldCount} 个字段`)
    selectedTable.value = undefined; fields.value = []
    await Promise.all([loadSummary(), loadTables()])
  } finally { refreshing.value = false }
}
const openTableEdit = (row: MetadataTableVO) => { Object.assign(tableForm, row); tableDialog.value = true }
const openFieldEdit = (row: MetadataFieldVO) => { Object.assign(fieldForm, row); fieldDialog.value = true }
const saveTable = async () => {
  saving.value = true
  try {
    await DataPlatformApi.updateMetadataTable(tableForm as MetadataTableVO)
    message.success('业务表定义已确认'); tableDialog.value = false; await loadTables()
  } finally { saving.value = false }
}
const saveField = async () => {
  if (!fieldForm.businessName?.trim()) { message.warning('请填写业务中文名'); return }
  saving.value = true
  try {
    await DataPlatformApi.updateMetadataField(fieldForm as MetadataFieldVO)
    message.success('字段定义已确认'); fieldDialog.value = false; await Promise.all([loadFields(), loadSummary(), loadTables()])
  } finally { saving.value = false }
}
const formatType = (field: MetadataFieldVO) => field.columnSize && field.columnSize > 0 ? `${field.dataType}(${field.columnSize}${field.decimalDigits ? `,${field.decimalDigits}` : ''})` : field.dataType || '-'
const sensitivityLabel = (level: string) => ({ PUBLIC: '公开', INTERNAL: '内部', SENSITIVE: '敏感', RESTRICTED: '受限' }[level] || level)
const sensitivityType = (level: string) => ({ PUBLIC: 'success', INTERNAL: 'info', SENSITIVE: 'warning', RESTRICTED: 'danger' }[level] as any)

onMounted(async () => {
  const page = await DataPlatformApi.getDataSourcePage({ pageNo: 1, pageSize: 100, category: 'DATABASE', status: 0 })
  dataSources.value = page.list.filter((item: DataSourceVO) => item.category === 'DATABASE')
  const preferred = dataSources.value.find((item) => item.name === '工厂达人') || dataSources.value[0]
  if (preferred?.id) { selectedDataSourceId.value = preferred.id; await changeDataSource() }
})
</script>
