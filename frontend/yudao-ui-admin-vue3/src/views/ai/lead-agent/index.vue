<template>
  <ContentWrap>
    <div class="lead-agent-header">
      <div>
        <div class="lead-agent-title">lead_agent</div>
        <div class="lead-agent-subtitle">公开网页线索采集、评分、社媒验证与导出</div>
      </div>
      <div class="lead-agent-actions">
        <el-button type="primary" @click="openRunDialog" v-hasPermi="['ai:lead-agent:execute']">
          <Icon icon="ep:video-play" class="mr-5px" />
          启动采集
        </el-button>
        <el-button @click="refreshCurrentTab">
          <Icon icon="ep:refresh" class="mr-5px" />
          刷新
        </el-button>
      </div>
    </div>
    <el-row :gutter="16" class="mt-16px">
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="客户总数" :value="dashboard.customerTotal || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="目标客户" :value="dashboard.targetCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="有邮箱客户" :value="dashboard.withEmailCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="市场分类" :value="dashboard.marketCategoryCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="关键词" :value="dashboard.keywordCount || 0" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-statistic title="采集历史" :value="dashboard.historyTotal || 0" />
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="采集任务" name="jobs">
        <el-form
          ref="jobQueryFormRef"
          :model="jobQuery"
          :inline="true"
          label-width="80px"
          class="-mb-15px"
        >
          <el-form-item label="运行ID" prop="runId">
            <el-input
              v-model="jobQuery.runId"
              clearable
              class="!w-220px"
              placeholder="输入运行ID"
              @keyup.enter="handleJobQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="jobQuery.status" clearable class="!w-160px" placeholder="选择状态">
              <el-option label="等待中" value="PENDING" />
              <el-option label="运行中" value="RUNNING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类" prop="categoryCode">
            <el-select
              v-model="jobQuery.categoryCode"
              clearable
              filterable
              class="!w-220px"
              placeholder="选择分类"
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
            <el-button @click="handleJobQuery">
              <Icon icon="ep:search" class="mr-5px" />
              搜索
            </el-button>
            <el-button @click="resetJobQuery">
              <Icon icon="ep:refresh" class="mr-5px" />
              重置
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="mt-20px"
          v-loading="jobLoading"
          :data="jobList"
          :stripe="true"
          :show-overflow-tooltip="true"
        >
          <el-table-column label="状态" align="center" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.status)">{{ getStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="运行ID" prop="runId" min-width="190" />
          <el-table-column label="分类" prop="categoryCode" min-width="180" />
          <el-table-column label="国家" prop="country" width="130" />
          <el-table-column label="搜索源" prop="searchProvider" width="100" />
          <el-table-column label="候选" prop="totalCandidates" width="80" align="center" />
          <el-table-column label="已采集" width="160">
            <template #default="{ row }">
              <el-progress
                :percentage="getJobProgress(row)"
                :text-inside="true"
                :stroke-width="18"
              />
            </template>
          </el-table-column>
          <el-table-column label="客户" prop="leadCount" width="80" align="center" />
          <el-table-column label="可导出" prop="exportedCount" width="90" align="center" />
          <el-table-column
            label="开始时间"
            prop="startedAt"
            :formatter="dateFormatter"
            width="180"
            align="center"
          />
          <el-table-column
            label="结束时间"
            prop="finishedAt"
            :formatter="dateFormatter"
            width="180"
            align="center"
          />
          <el-table-column label="错误" prop="errorMessage" min-width="220" />
        </el-table>
        <Pagination
          :total="jobTotal"
          v-model:page="jobQuery.pageNo"
          v-model:limit="jobQuery.pageSize"
          @pagination="getJobPage"
        />
      </el-tab-pane>

      <el-tab-pane label="客户结果" name="customers">
        <el-form
          ref="customerQueryFormRef"
          :model="customerQuery"
          :inline="true"
          label-width="80px"
          class="-mb-15px"
        >
          <el-form-item label="公司" prop="companyName">
            <el-input
              v-model="customerQuery.companyName"
              clearable
              class="!w-220px"
              placeholder="输入公司名"
              @keyup.enter="handleCustomerQuery"
            />
          </el-form-item>
          <el-form-item label="域名" prop="domain">
            <el-input
              v-model="customerQuery.domain"
              clearable
              class="!w-200px"
              placeholder="输入域名"
              @keyup.enter="handleCustomerQuery"
            />
          </el-form-item>
          <el-form-item label="分类" prop="matchedCategory">
            <el-select
              v-model="customerQuery.matchedCategory"
              clearable
              filterable
              class="!w-220px"
              placeholder="选择分类"
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
          <el-form-item label="邮箱" prop="hasEmail">
            <el-select v-model="customerQuery.hasEmail" clearable class="!w-140px" placeholder="不限">
              <el-option label="有邮箱" :value="true" />
              <el-option label="不限" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleCustomerQuery">
              <Icon icon="ep:search" class="mr-5px" />
              搜索
            </el-button>
            <el-button @click="resetCustomerQuery">
              <Icon icon="ep:refresh" class="mr-5px" />
              重置
            </el-button>
            <el-button
              type="success"
              plain
              :loading="exporting"
              @click="exportCustomerExcel"
              v-hasPermi="['ai:lead-agent:export']"
            >
              <Icon icon="ep:download" class="mr-5px" />
              导出Excel
            </el-button>
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
            <template #default="{ row }">
              <el-tag :type="getGradeTagType(row.grade)">{{ row.grade }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分数" align="center" prop="score" width="80" />
          <el-table-column label="公司" prop="companyName" min-width="190">
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openCustomerDetail(row)">
                {{ row.companyName || row.domain }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="国家" prop="country" width="130" align="center" />
          <el-table-column label="域名" prop="domain" min-width="180" />
          <el-table-column label="最佳邮箱" prop="bestEmail" min-width="220" />
          <el-table-column label="邮箱类型" prop="emailType" min-width="130" />
          <el-table-column label="匹配分类" prop="matchedCategory" min-width="180" />
          <el-table-column label="目标客户" align="center" width="100">
            <template #default="{ row }">
              <el-tag :type="row.target ? 'success' : 'info'">{{ row.target ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="采集时间"
            prop="collectedAt"
            :formatter="dateFormatter"
            width="180"
            align="center"
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
          :model="historyQuery"
          :inline="true"
          label-width="80px"
          class="-mb-15px"
        >
          <el-form-item label="运行ID" prop="runId">
            <el-input
              v-model="historyQuery.runId"
              clearable
              class="!w-220px"
              placeholder="输入运行ID"
              @keyup.enter="handleHistoryQuery"
            />
          </el-form-item>
          <el-form-item label="域名" prop="domain">
            <el-input
              v-model="historyQuery.domain"
              clearable
              class="!w-200px"
              placeholder="输入域名"
              @keyup.enter="handleHistoryQuery"
            />
          </el-form-item>
          <el-form-item label="分类" prop="searchCategory">
            <el-select
              v-model="historyQuery.searchCategory"
              clearable
              filterable
              class="!w-220px"
              placeholder="选择分类"
            >
              <el-option
                v-for="item in marketOptions"
                :key="item.categoryCode"
                :label="item.categoryName"
                :value="item.categoryCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="crawlStatus">
            <el-select v-model="historyQuery.crawlStatus" clearable class="!w-140px" placeholder="不限">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleHistoryQuery">
              <Icon icon="ep:search" class="mr-5px" />
              搜索
            </el-button>
            <el-button @click="resetHistoryQuery">
              <Icon icon="ep:refresh" class="mr-5px" />
              重置
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="mt-20px"
          v-loading="historyLoading"
          :data="historyList"
          :stripe="true"
          :show-overflow-tooltip="true"
        >
          <el-table-column label="运行ID" prop="runId" min-width="190" />
          <el-table-column label="搜索源" prop="searchProvider" width="100" align="center" />
          <el-table-column label="分类" prop="searchCategory" min-width="180" />
          <el-table-column label="国家" prop="searchCountry" width="130" align="center" />
          <el-table-column label="关键词" prop="searchKeyword" min-width="260" />
          <el-table-column label="域名" prop="domain" min-width="180" />
          <el-table-column label="状态" prop="crawlStatus" width="100" align="center" />
          <el-table-column label="页数" prop="pagesCrawled" width="80" align="center" />
          <el-table-column label="分数" prop="score" width="80" align="center" />
          <el-table-column
            label="采集时间"
            prop="collectedAt"
            :formatter="dateFormatter"
            width="180"
            align="center"
          />
        </el-table>
        <Pagination
          :total="historyTotal"
          v-model:page="historyQuery.pageNo"
          v-model:limit="historyQuery.pageSize"
          @pagination="getHistoryPage"
        />
      </el-tab-pane>

      <el-tab-pane label="市场配置" name="markets">
        <el-form
          ref="marketQueryFormRef"
          :model="marketQuery"
          :inline="true"
          label-width="80px"
          class="-mb-15px"
        >
          <el-form-item label="分类ID" prop="categoryCode">
            <el-input
              v-model="marketQuery.categoryCode"
              clearable
              class="!w-220px"
              placeholder="输入分类ID"
              @keyup.enter="handleMarketQuery"
            />
          </el-form-item>
          <el-form-item label="名称" prop="categoryName">
            <el-input
              v-model="marketQuery.categoryName"
              clearable
              class="!w-220px"
              placeholder="输入分类名称"
              @keyup.enter="handleMarketQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="enabled">
            <el-select v-model="marketQuery.enabled" clearable class="!w-140px" placeholder="不限">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleMarketQuery">
              <Icon icon="ep:search" class="mr-5px" />
              搜索
            </el-button>
            <el-button @click="resetMarketQuery">
              <Icon icon="ep:refresh" class="mr-5px" />
              重置
            </el-button>
            <el-button
              type="primary"
              plain
              @click="openMarketForm('create')"
              v-hasPermi="['ai:lead-agent:update']"
            >
              <Icon icon="ep:plus" class="mr-5px" />
              新增
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
          <el-table-column label="分类ID" prop="categoryCode" min-width="180" />
          <el-table-column label="分类名称" prop="categoryName" min-width="180" />
          <el-table-column label="权重" prop="weight" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="国家" min-width="260">
            <template #default="{ row }">
              <el-tag v-for="item in previewList(row.countries)" :key="item" class="mr-5px mb-5px">
                {{ item }}
              </el-tag>
              <span v-if="row.countries?.length > 4" class="text-gray-500">
                +{{ row.countries.length - 4 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="关键词" min-width="360">
            <template #default="{ row }">
              <el-tag
                v-for="item in previewList(row.keywords)"
                :key="item"
                type="warning"
                class="mr-5px mb-5px"
              >
                {{ item }}
              </el-tag>
              <span v-if="row.keywords?.length > 4" class="text-gray-500">
                +{{ row.keywords.length - 4 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="140" align="center">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="openMarketForm('update', row)"
                v-hasPermi="['ai:lead-agent:update']"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                @click="deleteMarket(row.id)"
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
              <Icon icon="ep:check" class="mr-5px" />
              保存
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
          <el-form-item label="保留拒绝结果">
            <el-switch v-model="exportRules.writeRejectedFile" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="exportSaving"
              @click="saveExportRules"
              v-hasPermi="['ai:lead-agent:update']"
            >
              <Icon icon="ep:check" class="mr-5px" />
              保存
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <el-dialog v-model="runDialogVisible" title="启动 Lead Agent 采集" width="620px">
    <el-form ref="runFormRef" :model="runForm" :rules="runRules" label-width="150px">
      <el-form-item label="市场分类" prop="categoryCode">
        <el-select
          v-model="runForm.categoryCode"
          clearable
          filterable
          class="w-100%"
          placeholder="全部启用分类"
        >
          <el-option
            v-for="item in marketOptions"
            :key="item.categoryCode"
            :label="item.categoryName"
            :value="item.categoryCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="国家" prop="country">
        <el-select v-model="runForm.country" clearable filterable class="w-100%" placeholder="全部国家">
          <el-option v-for="item in countryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="搜索源" prop="searchProvider">
        <el-segmented v-model="runForm.searchProvider" :options="searchProviderOptions" />
      </el-form-item>
      <el-form-item label="候选上限" prop="maxResults">
        <el-input-number v-model="runForm.maxResults" :min="1" :max="500" />
      </el-form-item>
      <el-form-item label="单站页数" prop="maxPagesPerSite">
        <el-input-number v-model="runForm.maxPagesPerSite" :min="1" :max="10" />
      </el-form-item>
      <el-form-item label="单页超时秒数" prop="crawlTimeoutSeconds">
        <el-input-number v-model="runForm.crawlTimeoutSeconds" :min="5" :max="60" />
      </el-form-item>
      <el-form-item label="社媒验证">
        <el-switch
          v-model="runForm.skipSocialVerification"
          inline-prompt
          active-text="跳过"
          inactive-text="启用"
        />
      </el-form-item>
      <el-form-item label="LLM复核">
        <el-switch v-model="runForm.enableAiReview" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="runDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="runSubmitting" @click="submitRunForm">启动</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="marketDialogVisible" :title="marketDialogTitle" width="720px">
    <el-form ref="marketFormRef" :model="marketForm" :rules="marketRules" label-width="110px">
      <el-form-item label="分类ID" prop="categoryCode">
        <el-input v-model="marketForm.categoryCode" placeholder="例如 appliance_dealers" />
      </el-form-item>
      <el-form-item label="分类名称" prop="categoryName">
        <el-input v-model="marketForm.categoryName" placeholder="输入分类名称" />
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
      <el-descriptions-item label="网站">
        <el-link v-if="currentCustomer.website" :href="currentCustomer.website" target="_blank">
          {{ currentCustomer.website }}
        </el-link>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="最佳邮箱">{{ currentCustomer.bestEmail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="全部邮箱">{{ formatArray(currentCustomer.emails) }}</el-descriptions-item>
      <el-descriptions-item label="主营产品">{{ formatArray(currentCustomer.mainProducts) }}</el-descriptions-item>
      <el-descriptions-item label="社媒验证">{{ currentCustomer.socialActivitySummary || '-' }}</el-descriptions-item>
      <el-descriptions-item label="评分原因">{{ currentCustomer.scoreReason || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开发信标题">{{ currentCustomer.developmentEmailSubject || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开发信正文">
        <div class="whitespace-pre-wrap">{{ currentCustomer.developmentEmailBody || '-' }}</div>
      </el-descriptions-item>
      <el-descriptions-item label="合规备注">{{ currentCustomer.complianceNote || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>

<script lang="ts" setup>
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import * as LeadAgentApi from '@/api/ai/lead-agent'

defineOptions({ name: 'LeadAgentWorkbench' })

const message = useMessage()
const { t } = useI18n()

const activeTab = ref('jobs')
const dashboard = ref<any>({})
const jobLoading = ref(false)
const marketLoading = ref(false)
const customerLoading = ref(false)
const historyLoading = ref(false)
const filterSaving = ref(false)
const exportSaving = ref(false)
const exporting = ref(false)
const runSubmitting = ref(false)
const marketSaving = ref(false)
const jobList = ref<any[]>([])
const marketList = ref<any[]>([])
const marketOptions = ref<any[]>([])
const customerList = ref<any[]>([])
const historyList = ref<any[]>([])
const jobTotal = ref(0)
const marketTotal = ref(0)
const customerTotal = ref(0)
const historyTotal = ref(0)
const runDialogVisible = ref(false)
const marketDialogVisible = ref(false)
const customerDrawerVisible = ref(false)
const currentCustomer = ref<any>()
const runFormRef = ref()
const marketFormRef = ref()
const jobQueryFormRef = ref()
const marketQueryFormRef = ref()
const customerQueryFormRef = ref()
const historyQueryFormRef = ref()
const marketDialogType = ref<'create' | 'update'>('create')
const pollingTimer = ref<number>()
const searchProviderOptions = ['auto', 'mock', 'bing', 'serpapi']
const statusTextMap: Record<string, string> = {
  PENDING: '等待中',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败'
}

const marketDialogTitle = computed(() =>
  marketDialogType.value === 'create' ? '新增市场分类' : '编辑市场分类'
)

const jobQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  runId: undefined as string | undefined,
  status: undefined as string | undefined,
  categoryCode: undefined as string | undefined
})

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

const runForm = reactive({
  categoryCode: undefined as string | undefined,
  country: undefined as string | undefined,
  searchProvider: 'auto',
  enableAiReview: false,
  skipSocialVerification: false,
  maxResults: 20,
  maxPagesPerSite: 5,
  crawlTimeoutSeconds: 15
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

const runRules = {
  maxResults: [{ required: true, message: '请输入候选上限', trigger: 'blur' }],
  maxPagesPerSite: [{ required: true, message: '请输入单站页数', trigger: 'blur' }],
  crawlTimeoutSeconds: [{ required: true, message: '请输入超时时间', trigger: 'blur' }]
}

const marketRules = {
  categoryCode: [{ required: true, message: '分类ID不能为空', trigger: 'blur' }],
  countriesText: [{ required: true, message: '国家不能为空', trigger: 'blur' }],
  keywordsText: [{ required: true, message: '关键词不能为空', trigger: 'blur' }]
}

const countryOptions = computed(() => {
  const selected = marketOptions.value.find((item) => item.categoryCode === runForm.categoryCode)
  const countries = selected
    ? selected.countries || []
    : marketOptions.value.flatMap((item) => item.countries || [])
  return Array.from(new Set(countries)).sort()
})

watch(
  () => runForm.categoryCode,
  () => {
    if (runForm.country && !countryOptions.value.includes(runForm.country)) {
      runForm.country = undefined
    }
  }
)

const getDashboard = async () => {
  dashboard.value = await LeadAgentApi.getDashboard()
}

const getJobPage = async () => {
  jobLoading.value = true
  try {
    const data = await LeadAgentApi.getJobPage(jobQuery)
    jobList.value = data.list
    jobTotal.value = data.total
    syncPolling()
  } finally {
    jobLoading.value = false
  }
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
  if (name === 'jobs') await getJobPage()
  if (name === 'markets') await getMarketPage()
  if (name === 'customers') await getCustomerPage()
  if (name === 'history') await getHistoryPage()
  if (name === 'filters') await getFilterRules()
  if (name === 'export') await getExportRules()
}

const refreshCurrentTab = async () => {
  await Promise.all([getDashboard(), getMarketOptions()])
  await handleTabChange(activeTab.value)
}

const handleJobQuery = () => {
  jobQuery.pageNo = 1
  getJobPage()
}

const resetJobQuery = () => {
  jobQueryFormRef.value.resetFields()
  handleJobQuery()
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

const openRunDialog = async () => {
  if (!marketOptions.value.length) {
    await getMarketOptions()
  }
  runDialogVisible.value = true
}

const submitRunForm = async () => {
  await runFormRef.value.validate()
  runSubmitting.value = true
  try {
    await LeadAgentApi.startRun(runForm)
    message.success('采集任务已启动')
    runDialogVisible.value = false
    activeTab.value = 'jobs'
    await Promise.all([getDashboard(), getJobPage()])
  } finally {
    runSubmitting.value = false
  }
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

const exportCustomerExcel = async () => {
  exporting.value = true
  try {
    const data = await LeadAgentApi.exportCustomers(customerQuery)
    download.excel(data, 'Lead Agent 客户线索.xlsx')
  } finally {
    exporting.value = false
  }
}

const openCustomerDetail = (row: any) => {
  currentCustomer.value = row
  customerDrawerVisible.value = true
}

const syncPolling = () => {
  const hasRunning = jobList.value.some((item) => ['PENDING', 'RUNNING'].includes(item.status))
  if (hasRunning && !pollingTimer.value) {
    pollingTimer.value = window.setInterval(async () => {
      await Promise.all([getJobPage(), getDashboard()])
    }, 5000)
  }
  if (!hasRunning && pollingTimer.value) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = undefined
  }
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

const formatArray = (items?: string[]) => {
  return items && items.length ? items.join('；') : '-'
}

const getGradeTagType = (grade?: string) => {
  if (grade === 'A') return 'success'
  if (grade === 'B') return 'primary'
  if (grade === 'C') return 'warning'
  return 'info'
}

const getStatusText = (status?: string) => {
  return statusTextMap[status || ''] || status || '-'
}

const getStatusTagType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

const getJobProgress = (row: any) => {
  if (!row.totalCandidates) {
    return row.status === 'SUCCESS' ? 100 : 0
  }
  return Math.min(100, Math.round(((row.crawledCount || 0) / row.totalCandidates) * 100))
}

onMounted(async () => {
  await Promise.all([getDashboard(), getMarketOptions(), getJobPage()])
})

onUnmounted(() => {
  if (pollingTimer.value) {
    window.clearInterval(pollingTimer.value)
  }
})
</script>

<style scoped>
.lead-agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.lead-agent-title {
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}

.lead-agent-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.lead-agent-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 768px) {
  .lead-agent-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .lead-agent-actions {
    justify-content: flex-start;
  }
}
</style>
