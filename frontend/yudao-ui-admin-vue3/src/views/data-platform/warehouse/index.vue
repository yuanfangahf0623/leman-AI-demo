<template>
  <ContentWrap>
    <el-descriptions :column="4" border>
      <el-descriptions-item label="引擎">{{ health.engine || 'Apache Doris' }}</el-descriptions-item>
      <el-descriptions-item label="状态"><el-tag :type="health.status === 'UP' ? 'success' : 'danger'">{{ health.status || 'UNKNOWN' }}</el-tag></el-descriptions-item>
      <el-descriptions-item label="版本">{{ health.version || '-' }}</el-descriptions-item>
      <el-descriptions-item label="分层数">{{ databases.length }}</el-descriptions-item>
    </el-descriptions>
  </ContentWrap>
  <el-row :gutter="16">
    <el-col :span="6"><ContentWrap title="数仓分层"><el-menu :default-active="selectedDatabase" @select="selectDatabase"><el-menu-item v-for="item in databases" :key="item" :index="item"><Icon icon="ep:coin" />{{ item.toUpperCase() }}</el-menu-item></el-menu></ContentWrap></el-col>
    <el-col :span="18"><ContentWrap :title="selectedDatabase ? `${selectedDatabase.toUpperCase()} 表` : '请选择数仓分层'">
      <el-table v-loading="loading" :data="tables" stripe @row-click="selectTable"><el-table-column prop="table" label="表名" min-width="260" /><el-table-column label="操作" width="100"><template #default="s"><el-button link type="primary" @click.stop="selectTable(s.row)">查看字段</el-button></template></el-table-column></el-table>
    </ContentWrap></el-col>
  </el-row>
  <Dialog v-model="columnVisible" :title="`${selectedDatabase}.${selectedTable} 字段`" width="900px"><el-table :data="columns" stripe><el-table-column prop="name" label="字段" min-width="160" /><el-table-column prop="type" label="类型" min-width="160" /><el-table-column prop="key" label="键" width="80" /><el-table-column label="可空" width="80"><template #default="s">{{ s.row.nullable ? '是' : '否' }}</template></el-table-column><el-table-column prop="defaultValue" label="默认值" min-width="120" /><el-table-column prop="comment" label="注释" min-width="180" /></el-table></Dialog>
</template>

<script setup lang="ts">
import { DataPlatformApi, WarehouseColumnVO } from '@/api/data-platform'
defineOptions({ name: 'DataPlatformWarehouse' })
const health = ref<any>({}), databases = ref<string[]>([]), tables = ref<any[]>([]), columns = ref<WarehouseColumnVO[]>([])
const selectedDatabase = ref(''), selectedTable = ref(''), loading = ref(false), columnVisible = ref(false)
const selectDatabase = async (database: string) => { selectedDatabase.value = database; loading.value = true; try { tables.value = await DataPlatformApi.getWarehouseTables(database) } finally { loading.value = false } }
const selectTable = async (row: any) => { selectedTable.value = row.table; columns.value = await DataPlatformApi.getWarehouseColumns(selectedDatabase.value, row.table); columnVisible.value = true }
onMounted(async () => { health.value = await DataPlatformApi.getWarehouseHealth(); databases.value = await DataPlatformApi.getWarehouseDatabases(); if (databases.value.length) await selectDatabase(databases.value[0]) })
</script>
