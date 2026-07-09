<template>
  <ContentWrap>
    <el-row :gutter="16">
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="客户数" :value="dashboard.customerTotal || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="目标客户" :value="dashboard.targetCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="有邮箱" :value="dashboard.withEmailCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="市场分类" :value="dashboard.marketCategoryCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="关键词" :value="dashboard.keywordCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="爬取历史" :value="dashboard.historyTotal || 0" />
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="市场配置" name="markets">
        <el-form
          ref="marketQueryFormRef"
          class="-mb-15px"
          :model="marketQuery"
          :inline="true"
          label-width="90px"
        >
          <el-form-item label="分类 ID" prop="categoryCode">
            <el-input
              v-model="marketQuery.categoryCode"
              placeholder="请输入分类 ID"
              clearable
              class="!w-220px"
              @keyup.enter="handleMarketQuery"
            />
          </el-form-item>
          <el-form-item label="分类名称" prop="categoryName">
            <el-input
              v-model="marketQuery.categoryName"
              placeholder="请输入分类名称"
              clearable
              class="!w-220px"
              @keyup.enter="handleMarketQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="enabled">
            <el-select v-model="marketQuery.enabled" placeholder="请选择状态" clearable class="!w-160px">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleMarketQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
            <el-button @click="resetMarketQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
            <el-button
              type="primary"
              plain
              @click="openMarketForm('create')"
              v-hasPermi="['ai:lead-agent:update']"
            >
              <Icon icon="ep:plus" class="mr-5px" /> 新增
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="mt-20px"
          v-loading="marketLoading"
          :data="marketList"
          :stripe="true"
          :show-overflow-tooltip="true"
        >
          <el-table-column label="分类 ID" align="center" prop="categoryCode" min-width="180" />
          <el-table-column label="分类名称" align="center" prop="categoryName" min-width="180" />
          <el-table-column label="权重" align="center" prop="weight" width="90" />
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.enabled ? 'success' : 'info'">
                {{ scope.row.enabled ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="国家" min-width="260">
            <template #default="scope">
              <el-tag
                v-for="item in previewList(scope.row.countries)"
                :key="item"
                class="mr-5px mb-5px"
              >
                {{ item }}
              </el-tag>
              <span v-if="scope.row.countries?.length > 4" class="text-gray-500">
                +{{ scope.row.countries.length - 4 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="关键词" min-width="360">
            <template #default="scope">
              <el-tag
                v-for="item in previewList(scope.row.keywords)"
                :key="item"
                type="warning"
                class="mr-5px mb-5px"
              >
                {{ item }}
              </el-tag>
              <span v-if="scope.row.keywords?.length > 4" class="text-gray-500">
                +{{ scope.row.keywords.length - 4 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" fixed="right" width="140">
            <template #default="scope">
              <el-button
                link
                type="primary"
                @click="openMarketForm('update', scope.row)"
                v-hasPermi="['ai:lead-agent:update']"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                @click="deleteMarket(scope.row.id)"
                v-hasPermi="['ai:lead-agent:update']"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="marketTotal"
          v-model:page="marketQuery.pageNo"
          v-model:limit="marketQuery.pageSize"
          @pagination="getMarketPage"
        />
      </el-tab-pane>

      <el-tab-pane label="客户结果" name="customers">
        <el-form
          ref="customerQueryFormRef"
          class="-mb-15px"
          :model="customerQuery"
          :inline="true"
          label-width="90px"
        >
          <el-form-item label="公司" prop="companyName">
            <el-input
              v-model="customerQuery.companyName"
              placeholder="请输入公司名"
              clearable
              class="!w-220px"
              @keyup.enter="handleCustomerQuery"
            />
          </el-form-item>
          <el-form-item label="域名" prop="domain">
            <el-input
              v-model="customerQuery.domain"
              placeholder="请输入域名"
              clearable
              class="!w-220px"
              @keyup.enter="handleCustomerQuery"
            />
          </el-form-item>
          <el-form-item label="分类" prop="matchedCategory">
            <el-select
              v-model="customerQuery.matchedCategory"
              placeholder="请选择分类"
              clearable
              filterable
              class="!w-220px"
            >
              <el-option
                v-for="item in marketOptions"
                :key="item.categoryCode"
                :label="item.categoryName"
                :value="item.categoryCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="最低分" prop="minScore">
            <el-input-number v-model="customerQuery.minScore" :min="0" :max="100" class="!w-150px" />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleCustomerQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
            <el-button @click="resetCustomerQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="mt-20px"
          v-loading="customerLoading"
          :data="customerList"
          :stripe="true"
          :show-overflow-tooltip="true"
        >
          <el-table-column label="等级" align="center" prop="grade" width="80">
            <template #default="scope">
              <el-tag :type="getGradeTagType(scope.row.grade)">{{ scope.row.grade }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分数" align="center" prop="score" width="80" />
          <el-table-column label="公司" prop="companyName" min-width="180">
            <template #default="scope">
              <el-link type="primary" :underline="false" @click="openCustomerDetail(scope.row)">
                {{ scope.row.companyName || scope.row.domain }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="国家" align="center" prop="country" width="130" />
          <el-table-column label="域名" align="center" prop="domain" min-width="180" />
          <el-table-column label="最佳邮箱" align="center" prop="bestEmail" min-width="220" />
          <el-table-column label="电话" align="center" prop="phone" min-width="140" />
          <el-table-column label="匹配分类" align="center" prop="matchedCategory" min-width="180" />
          <el-table-column label="目标客户" align="center" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.target ? 'success' : 'info'">
                {{ scope.row.target ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="采集时间"
            align="center"
            prop="collectedAt"
            :formatter="dateFormatter"
            width="180"
          />
        </el-table>
        <Pagination
          :total="customerTotal"
          v-model:page="customerQuery.pageNo"
          v-model:limit="customerQuery.pageSize"
          @pagination="getCustomerPage"
        />
      </el-tab-pane>

      <el-tab-pane label="爬取历史" name="history">
        <el-form
          ref="historyQueryFormRef"
          class="-mb-15px"
          :model="historyQuery"
          :inline="true"
          label-width="90px"
        >
          <el-form-item label="运行 ID" prop="runId">
            <el-input
              v-model="historyQuery.runId"
              placeholder="请输入运行 ID"
              clearable
              class="!w-220px"
              @keyup.enter="handleHistoryQuery"
            />
          </el-form-item>
          <el-form-item label="域名" prop="domain">
            <el-input
              v-model="historyQuery.domain"
              placeholder="请输入域名"
              clearable
              class="!w-220px"
              @keyup.enter="handleHistoryQuery"
            />
          </el-form-item>
          <el-form-item label="分类" prop="searchCategory">
            <el-select
              v-model="historyQuery.searchCategory"
              placeholder="请选择分类"
              clearable
              filterable
              class="!w-220px"
            >
              <el-option
                v-for="item in marketOptions"
                :key="item.categoryCode"
                :label="item.categoryName"
                :value="item.categoryCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleHistoryQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
            <el-button @click="resetHistoryQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="mt-20px"
          v-loading="historyLoading"
          :data="historyList"
          :stripe="true"
          :show-overflow-tooltip="true"
        >
          <el-table-column label="运行 ID" align="center" prop="runId" min-width="160" />
          <el-table-column label="搜索源" align="center" prop="searchProvider" width="110" />
          <el-table-column label="分类" align="center" prop="searchCategory" min-width="180" />
          <el-table-column label="国家" align="center" prop="searchCountry" width="130" />
          <el-table-column label="关键词" align="center" prop="searchKeyword" min-width="240" />
          <el-table-column label="域名" align="center" prop="domain" min-width="180" />
          <el-table-column label="状态" align="center" prop="crawlStatus" width="110" />
          <el-table-column label="页数" align="center" prop="pagesCrawled" width="80" />
          <el-table-column label="分数" align="center" prop="score" width="80" />
          <el-table-column
            label="采集时间"
            align="center"
            prop="collectedAt"
            :formatter="dateFormatter"
            width="180"
          />
        </el-table>
        <Pagination
          :total="historyTotal"
          v-model:page="historyQuery.pageNo"
          v-model:limit="historyQuery.pageSize"
          @pagination="getHistoryPage"
        />
      </el-tab-pane>

      <el-tab-pane label="过滤规则" name="filters">
        <el-form :model="filterRules" label-width="120px">
          <el-row :gutter="20">
            <el-col :xs="24" :md="12">
              <el-form-item label="屏蔽域名词">
                <el-input
                  v-model="filterRules.blockedDomainKeywordsText"
                  type="textarea"
                  :rows="16"
                  placeholder="每行一个域名关键词"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="屏蔽文件后缀">
                <el-input
                  v-model="filterRules.blockedFileExtensionsText"
                  type="textarea"
                  :rows="16"
                  placeholder="每行一个文件后缀"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item>
            <el-button
              type="primary"
              :loading="filterSaving"
              @click="saveFilterRules"
              v-hasPermi="['ai:lead-agent:update']"
            >
              <Icon icon="ep:check" class="mr-5px" /> 保存
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="导出规则" name="export">
        <el-form :model="exportRules" label-width="150px" class="!max-w-720px">
          <el-form-item label="最低分">
            <el-input-number v-model="exportRules.minScore" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="允许等级">
            <el-checkbox-group v-model="exportRules.allowedGrades">
              <el-checkbox label="A" />
              <el-checkbox label="B" />
              <el-checkbox label="C" />
              <el-checkbox label="D" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="只导出目标客户">
            <el-switch v-model="exportRules.includeTargetOnly" />
          </el-form-item>
          <el-form-item label="必须有邮箱">
            <el-switch v-model="exportRules.requireEmail" />
          </el-form-item>
          <el-form-item label="包含疑似重复">
            <el-switch v-model="exportRules.includePossibleDuplicates" />
          </el-form-item>
          <el-form-item label="生成拒绝文件">
            <el-switch v-model="exportRules.writeRejectedFile" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="exportSaving"
              @click="saveExportRules"
              v-hasPermi="['ai:lead-agent:update']"
            >
              <Icon icon="ep:check" class="mr-5px" /> 保存
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <el-dialog v-model="marketDialogVisible" :title="marketDialogTitle" width="720px">
    <el-form ref="marketFormRef" :model="marketForm" :rules="marketRules" label-width="110px">
      <el-form-item label="分类 ID" prop="categoryCode">
        <el-input v-model="marketForm.categoryCode" placeholder="例如 appliance_dealers" />
      </el-form-item>
      <el-form-item label="分类名称" prop="categoryName">
        <el-input v-model="marketForm.categoryName" placeholder="请输入分类名称" />
      </el-form-item>
      <el-form-item label="权重" prop="weight">
        <el-input-number v-model="marketForm.weight" :min="1" :max="1000" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="marketForm.enabled" />
      </el-form-item>
      <el-form-item label="国家" prop="countriesText">
        <el-input v-model="marketForm.countriesText" type="textarea" :rows="6" placeholder="每行一个国家" />
      </el-form-item>
      <el-form-item label="关键词" prop="keywordsText">
        <el-input v-model="marketForm.keywordsText" type="textarea" :rows="8" placeholder="每行一个搜索关键词" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="marketDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="marketSaving" @click="submitMarketForm">确定</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="customerDrawerVisible" title="客户详情" size="50%">
    <el-descriptions v-if="currentCustomer" :column="1" border>
      <el-descriptions-item label="公司">{{ currentCustomer.companyName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="网址">
        <el-link v-if="currentCustomer.website" :href="currentCustomer.website" target="_blank">
          {{ currentCustomer.website }}
        </el-link>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ currentCustomer.bestEmail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="主营产品">
        {{ currentCustomer.mainProducts?.join('；') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="评分原因">{{ currentCustomer.scoreReason || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开发信标题">
        {{ currentCustomer.developmentEmailSubject || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="开发信正文">
        <div class="whitespace-pre-wrap">{{ currentCustomer.developmentEmailBody || '-' }}</div>
      </el-descriptions-item>
      <el-descriptions-item label="合规备注">{{ currentCustomer.complianceNote || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as LeadAgentApi from '@/api/ai/lead-agent'

defineOptions({ name: 'AiLeadAgent' })

const message = useMessage()
const { t } = useI18n()

const activeTab = ref('markets')
const dashboard = ref<any>({})
const marketLoading = ref(false)
const customerLoading = ref(false)
const historyLoading = ref(false)
const filterSaving = ref(false)
const exportSaving = ref(false)
const marketSaving = ref(false)
const marketList = ref<any[]>([])
const marketOptions = ref<any[]>([])
const customerList = ref<any[]>([])
const historyList = ref<any[]>([])
const marketTotal = ref(0)
const customerTotal = ref(0)
const historyTotal = ref(0)
const marketDialogVisible = ref(false)
const customerDrawerVisible = ref(false)
const currentCustomer = ref<any>()
const marketFormRef = ref()
const marketQueryFormRef = ref()
const customerQueryFormRef = ref()
const historyQueryFormRef = ref()
const marketDialogType = ref<'create' | 'update'>('create')
const marketDialogTitle = computed(() => (marketDialogType.value === 'create' ? '新增市场分类' : '编辑市场分类'))

const marketQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  categoryCode: undefined as string | undefined,
  categoryName: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})
const customerQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  companyName: undefined as string | undefined,
  domain: undefined as string | undefined,
  country: undefined as string | undefined,
  matchedCategory: undefined as string | undefined,
  target: undefined as boolean | undefined,
  minScore: undefined as number | undefined,
  hasEmail: undefined as boolean | undefined
})
const historyQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  runId: undefined as string | undefined,
  domain: undefined as string | undefined,
  searchCategory: undefined as string | undefined,
  crawlStatus: undefined as string | undefined
})
const marketForm = reactive({
  id: undefined as number | undefined,
  categoryCode: '',
  categoryName: '',
  weight: 10,
  enabled: true,
  countriesText: '',
  keywordsText: ''
})
const filterRules = reactive({
  blockedDomainKeywordsText: '',
  blockedFileExtensionsText: ''
})
const exportRules = reactive({
  minScore: 0,
  includeTargetOnly: false,
  requireEmail: false,
  allowedGrades: ['A', 'B', 'C', 'D'],
  includePossibleDuplicates: true,
  writeRejectedFile: true
})
const marketRules = {
  categoryCode: [{ required: true, message: '分类 ID 不能为空', trigger: 'blur' }],
  countriesText: [{ required: true, message: '国家不能为空', trigger: 'blur' }],
  keywordsText: [{ required: true, message: '关键词不能为空', trigger: 'blur' }]
}

const getDashboard = async () => {
  dashboard.value = await LeadAgentApi.getDashboard()
}

const getMarketPage = async () => {
  marketLoading.value = true
  try {
    const data = await LeadAgentApi.getMarketPage(marketQuery)
    marketList.value = data.list
    marketTotal.value = data.total
  } finally {
    marketLoading.value = false
  }
}

const getMarketOptions = async () => {
  marketOptions.value = await LeadAgentApi.getMarketList()
}

const getCustomerPage = async () => {
  customerLoading.value = true
  try {
    const data = await LeadAgentApi.getCustomerPage(customerQuery)
    customerList.value = data.list
    customerTotal.value = data.total
  } finally {
    customerLoading.value = false
  }
}

const getHistoryPage = async () => {
  historyLoading.value = true
  try {
    const data = await LeadAgentApi.getHistoryPage(historyQuery)
    historyList.value = data.list
    historyTotal.value = data.total
  } finally {
    historyLoading.value = false
  }
}

const getFilterRules = async () => {
  const data = await LeadAgentApi.getFilterRules()
  filterRules.blockedDomainKeywordsText = (data.blockedDomainKeywords || []).join('\n')
  filterRules.blockedFileExtensionsText = (data.blockedFileExtensions || []).join('\n')
}

const getExportRules = async () => {
  const data = await LeadAgentApi.getExportRules()
  Object.assign(exportRules, data)
}

const handleTabChange = async (name: string) => {
  if (name === 'customers') await getCustomerPage()
  if (name === 'history') await getHistoryPage()
  if (name === 'filters') await getFilterRules()
  if (name === 'export') await getExportRules()
}

const handleMarketQuery = () => {
  marketQuery.pageNo = 1
  getMarketPage()
}

const resetMarketQuery = () => {
  marketQueryFormRef.value.resetFields()
  handleMarketQuery()
}

const handleCustomerQuery = () => {
  customerQuery.pageNo = 1
  getCustomerPage()
}

const resetCustomerQuery = () => {
  customerQueryFormRef.value.resetFields()
  handleCustomerQuery()
}

const handleHistoryQuery = () => {
  historyQuery.pageNo = 1
  getHistoryPage()
}

const resetHistoryQuery = () => {
  historyQueryFormRef.value.resetFields()
  handleHistoryQuery()
}

const openMarketForm = (type: 'create' | 'update', row?: any) => {
  marketDialogType.value = type
  marketForm.id = row?.id
  marketForm.categoryCode = row?.categoryCode || ''
  marketForm.categoryName = row?.categoryName || ''
  marketForm.weight = row?.weight || 10
  marketForm.enabled = row?.enabled ?? true
  marketForm.countriesText = (row?.countries || []).join('\n')
  marketForm.keywordsText = (row?.keywords || []).join('\n')
  marketDialogVisible.value = true
}

const submitMarketForm = async () => {
  await marketFormRef.value.validate()
  marketSaving.value = true
  try {
    const data = {
      id: marketForm.id,
      categoryCode: marketForm.categoryCode,
      categoryName: marketForm.categoryName,
      weight: marketForm.weight,
      enabled: marketForm.enabled,
      countries: splitLines(marketForm.countriesText),
      keywords: splitLines(marketForm.keywordsText)
    }
    if (marketDialogType.value === 'create') {
      await LeadAgentApi.createMarket(data)
      message.success(t('common.createSuccess'))
    } else {
      await LeadAgentApi.updateMarket(data)
      message.success(t('common.updateSuccess'))
    }
    marketDialogVisible.value = false
    await Promise.all([getMarketPage(), getMarketOptions(), getDashboard()])
  } finally {
    marketSaving.value = false
  }
}

const deleteMarket = async (id: number) => {
  try {
    await message.delConfirm()
    await LeadAgentApi.deleteMarket(id)
    message.success(t('common.delSuccess'))
    await Promise.all([getMarketPage(), getMarketOptions(), getDashboard()])
  } catch {}
}

const saveFilterRules = async () => {
  filterSaving.value = true
  try {
    await LeadAgentApi.updateFilterRules({
      blockedDomainKeywords: splitLines(filterRules.blockedDomainKeywordsText),
      blockedFileExtensions: splitLines(filterRules.blockedFileExtensionsText)
    })
    message.success('保存成功')
    await getDashboard()
  } finally {
    filterSaving.value = false
  }
}

const saveExportRules = async () => {
  exportSaving.value = true
  try {
    await LeadAgentApi.updateExportRules(exportRules)
    message.success('保存成功')
  } finally {
    exportSaving.value = false
  }
}

const openCustomerDetail = (row: any) => {
  currentCustomer.value = row
  customerDrawerVisible.value = true
}

const splitLines = (value: string) => {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const previewList = (items?: string[]) => {
  return (items || []).slice(0, 4)
}

const getGradeTagType = (grade?: string) => {
  if (grade === 'A') return 'success'
  if (grade === 'B') return 'primary'
  if (grade === 'C') return 'warning'
  return 'info'
}

onMounted(async () => {
  await Promise.all([getDashboard(), getMarketPage(), getMarketOptions()])
})
</script>
