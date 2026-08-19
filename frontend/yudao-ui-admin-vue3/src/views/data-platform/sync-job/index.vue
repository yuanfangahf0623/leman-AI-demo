<template>
  <ContentWrap>
    <el-alert title="任务由现有 SeaTunnel 执行；目标只允许 Doris 数仓分层。SQL 中的 ? 为 JDBC 参数占位符。" type="info" show-icon :closable="false" class="mb-16px" />
    <el-form ref="queryRef" :model="query" inline class="-mb-15px">
      <el-form-item label="名称" prop="name"><el-input v-model="query.name" clearable class="!w-220px" /></el-form-item>
      <el-form-item label="模式" prop="syncMode"><el-select v-model="query.syncMode" clearable class="!w-160px"><el-option label="全量" value="FULL" /><el-option label="增量" value="INCREMENTAL" /></el-select></el-form-item>
      <el-form-item><el-button @click="search"><Icon icon="ep:search" class="mr-5px" />查询</el-button><el-button @click="reset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button><el-button type="primary" plain @click="open()" v-hasPermi="['data-platform:sync-job:create']"><Icon icon="ep:plus" class="mr-5px" />新增任务</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="源数据源" width="150"><template #default="s">{{ sourceName(s.row.sourceDataSourceId) }}</template></el-table-column>
      <el-table-column label="目标" min-width="180"><template #default="s">{{ s.row.targetDatabase }}.{{ s.row.targetTable }}</template></el-table-column>
      <el-table-column prop="syncMode" label="模式" width="110"><template #default="s"><el-tag>{{ s.row.syncMode }}</el-tag></template></el-table-column>
      <el-table-column prop="parallelism" label="并行度" width="90" />
      <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.status === 0 ? 'success' : 'info'">{{ s.row.status === 0 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230" fixed="right"><template #default="s"><el-button link type="success" @click="execute(s.row.id)" v-hasPermi="['data-platform:sync-job:execute']">执行</el-button><el-button link type="primary" @click="open(s.row.id)" v-hasPermi="['data-platform:sync-job:update']">编辑</el-button><el-button link type="danger" @click="remove(s.row.id)" v-hasPermi="['data-platform:sync-job:delete']">删除</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <Dialog v-model="visible" :title="form.id ? '编辑同步任务' : '新增同步任务'" width="900px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="编码" prop="code"><el-input v-model="form.code" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="源数据源" prop="sourceDataSourceId"><el-select v-model="form.sourceDataSourceId" filterable class="!w-100%"><el-option v-for="item in sources" :key="item.id" :label="`${item.name} (${item.type})`" :value="item.id" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="目标 Doris" prop="targetDataSourceId"><el-select v-model="form.targetDataSourceId" filterable class="!w-100%"><el-option v-for="item in dorisSources" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="目标分层" prop="targetDatabase"><el-select v-model="form.targetDatabase" class="!w-100%"><el-option v-for="item in layers" :key="item" :label="item.toUpperCase()" :value="item" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="目标表" prop="targetTable"><el-input v-model="form.targetTable" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="同步模式" prop="syncMode"><el-radio-group v-model="form.syncMode"><el-radio value="FULL">全量</el-radio><el-radio value="INCREMENTAL">增量</el-radio></el-radio-group></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="并行度"><el-input-number v-model="form.parallelism" :min="1" :max="16" /></el-form-item></el-col>
        <el-col v-if="form.syncMode === 'INCREMENTAL'" :span="12"><el-form-item label="水位字段" prop="watermarkColumn"><el-input v-model="form.watermarkColumn" /></el-form-item></el-col>
        <el-col v-if="form.syncMode === 'INCREMENTAL'" :span="12"><el-form-item label="当前水位"><el-input v-model="form.watermarkValue" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="Source SQL" prop="sourceSql"><el-input v-model="form.sourceSql" type="textarea" :rows="6" placeholder="SELECT col1, col2 FROM table WHERE update_time >= '2026-01-01'" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="Sink SQL" prop="sinkSql"><el-input v-model="form.sinkSql" type="textarea" :rows="5" placeholder="INSERT INTO ods.table_name(col1,col2) VALUES(?,?)" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <template #footer><el-button type="primary" :loading="saving" @click="save">保存</el-button><el-button @click="visible = false">取消</el-button></template>
  </Dialog>
</template>

<script setup lang="ts">
import { DataPlatformApi, DataSourceVO, SyncJobVO } from '@/api/data-platform'

defineOptions({ name: 'DataPlatformSyncJob' })
const message = useMessage()
const layers = ['ods', 'dwd', 'dim', 'dws', 'ads', 'tmp']
const loading = ref(false), saving = ref(false), visible = ref(false), list = ref<SyncJobVO[]>([]), sources = ref<DataSourceVO[]>([]), total = ref(0), queryRef = ref(), formRef = ref()
const query = reactive({ pageNo: 1, pageSize: 10, name: undefined, syncMode: undefined })
const emptyForm = (): SyncJobVO => ({ name: '', code: '', sourceDataSourceId: 0, sourceSql: '', targetDataSourceId: 0, targetDatabase: 'ods', targetTable: '', sinkSql: '', syncMode: 'FULL', parallelism: 1, status: 0 })
const form = reactive<SyncJobVO>(emptyForm())
const dorisSources = computed(() => sources.value.filter((item) => item.type === 'DORIS'))
const rules = { name: [{ required: true, message: '请输入名称' }], code: [{ required: true, pattern: /^[a-z][a-z0-9_]{1,63}$/, message: '编码格式不正确' }], sourceDataSourceId: [{ required: true, message: '请选择源数据源' }], targetDataSourceId: [{ required: true, message: '请选择目标 Doris' }], targetDatabase: [{ required: true }], targetTable: [{ required: true }], sourceSql: [{ required: true }], sinkSql: [{ required: true }] }
const loadSources = async () => { const data = await DataPlatformApi.getDataSourcePage({ pageNo: 1, pageSize: 100, status: 0 }); sources.value = data.list }
const load = async () => { loading.value = true; try { const data = await DataPlatformApi.getSyncJobPage(query); list.value = data.list; total.value = data.total } finally { loading.value = false } }
const search = () => { query.pageNo = 1; load() }
const reset = () => { queryRef.value?.resetFields(); search() }
const sourceName = (id: number) => sources.value.find((item) => item.id === id)?.name || id
const open = async (id?: number) => { Object.assign(form, emptyForm()); if (id) Object.assign(form, await DataPlatformApi.getSyncJob(id)); visible.value = true }
const save = async () => { await formRef.value.validate(); saving.value = true; try { form.id ? await DataPlatformApi.updateSyncJob(form) : await DataPlatformApi.createSyncJob(form); message.success('保存成功'); visible.value = false; await load() } finally { saving.value = false } }
const execute = async (id?: number) => { if (!id) return; try { await message.confirm('确认立即执行该同步任务？'); const runId = await DataPlatformApi.executeSyncJob(id); message.success(`任务已提交，运行编号 ${runId}`) } catch {} }
const remove = async (id?: number) => { if (!id) return; try { await message.delConfirm(); await DataPlatformApi.deleteSyncJob(id); message.success('删除成功'); await load() } catch {} }
onMounted(async () => { await loadSources(); await load() })
</script>
