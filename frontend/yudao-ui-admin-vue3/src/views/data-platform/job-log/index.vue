<template>
  <ContentWrap>
    <el-form ref="queryRef" :model="query" inline class="-mb-15px"><el-form-item label="任务编号" prop="jobId"><el-input-number v-model="query.jobId" :min="1" controls-position="right" /></el-form-item><el-form-item label="状态" prop="status"><el-select v-model="query.status" clearable class="!w-180px"><el-option v-for="item in statuses" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item><el-button @click="search"><Icon icon="ep:search" class="mr-5px" />查询</el-button><el-button @click="reset"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button></el-form-item></el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe><el-table-column prop="id" label="运行编号" width="100" /><el-table-column prop="jobName" label="任务" min-width="150" /><el-table-column prop="batchId" label="批次" min-width="230" /><el-table-column prop="triggerType" label="触发" width="90" /><el-table-column label="状态" width="110"><template #default="s"><el-tag :type="statusType(s.row.status)">{{ s.row.status }}</el-tag></template></el-table-column><el-table-column prop="startTime" label="开始时间" width="180" /><el-table-column prop="endTime" label="结束时间" width="180" /><el-table-column prop="errorMessage" label="错误摘要" min-width="180" /><el-table-column label="操作" width="90" fixed="right"><template #default="s"><el-button link type="primary" @click="showLog(s.row.id)">日志</el-button></template></el-table-column></el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>
  <Dialog v-model="visible" title="SeaTunnel 脱敏运行日志" width="900px"><pre class="log-view">{{ logText || '暂无日志' }}</pre><template #footer><el-button @click="visible = false">关闭</el-button></template></Dialog>
</template>

<script setup lang="ts">
import { DataPlatformApi, JobRunVO } from '@/api/data-platform'
defineOptions({ name: 'DataPlatformJobLog' })
const statuses = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT']
const loading = ref(false), visible = ref(false), list = ref<JobRunVO[]>([]), total = ref(0), logText = ref(''), queryRef = ref()
const query = reactive({ pageNo: 1, pageSize: 10, jobId: undefined as number | undefined, status: undefined as string | undefined })
const load = async () => { loading.value = true; try { const data = await DataPlatformApi.getJobRunPage(query); list.value = data.list; total.value = data.total } finally { loading.value = false } }
const search = () => { query.pageNo = 1; load() }
const reset = () => { queryRef.value?.resetFields(); search() }
const showLog = async (id: number) => { logText.value = await DataPlatformApi.getJobRunLog(id); visible.value = true }
const statusType = (status: string) => ({ SUCCESS: 'success', FAILED: 'danger', TIMEOUT: 'danger', RUNNING: 'warning' }[status] || 'info')
onMounted(load)
</script>
<style scoped>.log-view { min-height: 360px; max-height: 65vh; overflow: auto; margin: 0; padding: 16px; color: #d4d4d4; background: #1e1e1e; white-space: pre-wrap; word-break: break-all; font: 12px/1.6 Consolas, monospace; }</style>
