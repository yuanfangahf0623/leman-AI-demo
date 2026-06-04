<template>
  <ContentWrap v-if="showUploadPanel">
    <div class="invoice-upload-layout">
      <el-upload
        ref="uploadRef"
        v-model:file-list="fileList"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleFileExceed"
        :accept="uploadAccept"
        drag
        class="invoice-upload"
      >
        <Icon icon="ep:upload-filled" class="text-34px text-gray-400" />
        <div class="el-upload__text">将发票文件拖到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、JPG、PNG、TIFF、DOC、DOCX，单个文件不超过 20MB
          </div>
        </template>
      </el-upload>
      <div class="invoice-upload-actions">
        <el-checkbox v-model="autoRecognize">上传后自动识别</el-checkbox>
        <div>
          <el-button type="primary" :loading="uploadLoading" @click="submitUpload">
            <Icon icon="ep:upload" class="mr-5px" /> 上传发票
          </el-button>
          <el-button @click="clearUpload">
            <Icon icon="ep:refresh-left" class="mr-5px" /> 清空
          </el-button>
        </div>
      </div>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-form
      ref="queryFormRef"
      class="-mb-15px"
      :model="queryParams"
      :inline="true"
      label-width="84px"
    >
      <el-form-item label="供应商" prop="supplierName">
        <el-input
          v-model="queryParams.supplierName"
          clearable
          placeholder="请输入供应商"
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="发票号" prop="invoiceNo">
        <el-input
          v-model="queryParams.invoiceNo"
          clearable
          placeholder="请输入发票号"
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="AI状态" prop="aiStatus">
        <el-select v-model="queryParams.aiStatus" clearable placeholder="请选择" class="!w-170px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_AI_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="确认状态" prop="financeReviewStatus">
        <el-select
          v-model="queryParams.financeReviewStatus"
          clearable
          placeholder="请选择"
          class="!w-170px"
        >
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_REVIEW_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="审批状态" prop="approvalStatus">
        <el-select
          v-model="queryParams.approvalStatus"
          clearable
          placeholder="请选择"
          class="!w-170px"
        >
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-select v-model="queryParams.riskLevel" clearable placeholder="请选择" class="!w-170px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_RISK_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="发票日期">
        <el-date-picker
          v-model="invoiceDateRange"
          type="daterange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"> <Icon icon="ep:search" class="mr-5px" /> 搜索 </el-button>
        <el-button @click="resetQuery"> <Icon icon="ep:refresh" class="mr-5px" /> 重置 </el-button>
        <el-button type="primary" plain @click="showUploadPanel = !showUploadPanel">
          <Icon icon="ep:upload" class="mr-5px" /> 上传
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          v-hasPermi="['finance:invoice:export']"
          @click="handleExport"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="系统编号" prop="invoiceCode" min-width="150" fixed="left" />
      <el-table-column label="文件" prop="fileName" min-width="180" />
      <el-table-column label="来源" prop="sourceType" width="96">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.FINANCE_INVOICE_SOURCE_TYPE" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="供应商" prop="supplierName" min-width="170" />
      <el-table-column label="发票号" prop="invoiceNo" min-width="150" />
      <el-table-column
        label="发票日期"
        prop="invoiceDate"
        width="120"
        :formatter="dateFormatter2"
      />
      <el-table-column label="金额" min-width="130" align="right">
        <template #default="scope">
          {{ formatAmount(scope.row.grossAmount, scope.row.currency) }}
        </template>
      </el-table-column>
      <el-table-column label="AI" prop="aiStatus" width="105">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.FINANCE_INVOICE_AI_STATUS" :value="scope.row.aiStatus" />
        </template>
      </el-table-column>
      <el-table-column label="确认" prop="financeReviewStatus" width="105">
        <template #default="scope">
          <dict-tag
            :type="DICT_TYPE.FINANCE_INVOICE_REVIEW_STATUS"
            :value="scope.row.financeReviewStatus"
          />
        </template>
      </el-table-column>
      <el-table-column label="风险" prop="riskLevel" width="105">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.FINANCE_INVOICE_RISK_LEVEL" :value="scope.row.riskLevel" />
        </template>
      </el-table-column>
      <el-table-column label="审批" prop="approvalStatus" width="110">
        <template #default="scope">
          <dict-tag
            :type="DICT_TYPE.FINANCE_INVOICE_APPROVAL_STATUS"
            :value="scope.row.approvalStatus"
          />
        </template>
      </el-table-column>
      <el-table-column label="记账" prop="bookkeepingStatus" width="105">
        <template #default="scope">
          <dict-tag
            :type="DICT_TYPE.FINANCE_INVOICE_BOOKKEEPING_STATUS"
            :value="scope.row.bookkeepingStatus"
          />
        </template>
      </el-table-column>
      <el-table-column label="付款" prop="paymentStatus" width="105">
        <template #default="scope">
          <dict-tag
            :type="DICT_TYPE.FINANCE_INVOICE_PAYMENT_STATUS"
            :value="scope.row.paymentStatus"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" fixed="right" width="360" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">
            <Icon icon="ep:view" class="mr-3px" /> 详情
          </el-button>
          <el-button link type="primary" @click="openPreview(scope.row)">
            <Icon icon="ep:document" class="mr-3px" /> 预览
          </el-button>
          <el-button
            link
            type="primary"
            :loading="getActionLoading('recognize', scope.row.id)"
            v-hasPermi="['finance:invoice:recognize']"
            @click="handleRecognize(scope.row)"
          >
            <Icon icon="ep:cpu" class="mr-3px" /> 识别
          </el-button>
          <el-button
            link
            type="success"
            v-hasPermi="['finance:invoice:confirm']"
            @click="openConfirm(scope.row.id)"
          >
            <Icon icon="ep:finished" class="mr-3px" /> 确认
          </el-button>
          <el-dropdown trigger="click">
            <el-button link type="primary">
              更多<Icon icon="ep:arrow-down" class="ml-3px" />
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-hasPermi="['finance:invoice:submit']"
                  :disabled="!canSubmitApproval(scope.row)"
                  @click="handleSubmitApproval(scope.row)"
                >
                  提交审批
                </el-dropdown-item>
                <el-dropdown-item
                  v-hasPermi="['finance:invoice:submit']"
                  :disabled="!canSyncApproval(scope.row)"
                  @click="handleApprovalResult(scope.row, 'APPROVED')"
                >
                  审批通过
                </el-dropdown-item>
                <el-dropdown-item
                  v-hasPermi="['finance:invoice:submit']"
                  :disabled="!canSyncApproval(scope.row)"
                  @click="handleApprovalResult(scope.row, 'REJECTED')"
                >
                  审批驳回
                </el-dropdown-item>
                <el-dropdown-item
                  v-hasPermi="['finance:invoice:book-update']"
                  @click="handleToggleBookkeeping(scope.row)"
                >
                  {{ scope.row.bookkeepingStatus === 'BOOKED' ? '取消记账' : '标记记账' }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-hasPermi="['finance:invoice:payment-update']"
                  @click="openPayment(scope.row)"
                >
                  更新付款
                </el-dropdown-item>
                <el-dropdown-item
                  divided
                  v-hasPermi="['finance:invoice:delete']"
                  @click="handleDelete(scope.row)"
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

  <Dialog v-model="detailVisible" title="发票详情" width="1080">
    <el-tabs v-model="detailTab">
      <el-tab-pane label="基础信息" name="base">
        <el-descriptions v-if="detailData" :column="2" border>
          <el-descriptions-item label="系统编号">{{
            detailData.invoiceCode || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <dict-tag
              :type="DICT_TYPE.FINANCE_INVOICE_SOURCE_TYPE"
              :value="detailData.sourceType"
            />
          </el-descriptions-item>
          <el-descriptions-item label="文件名">{{
            detailData.fileName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{
            formatFileSize(detailData.fileSize)
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{
            detailData.supplierName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="税号">{{
            detailData.supplierTaxNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="发票号">{{
            detailData.invoiceNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="发票日期">{{
            formatDateTime(detailData.invoiceDate)
          }}</el-descriptions-item>
          <el-descriptions-item label="金额">
            {{ formatAmount(detailData.grossAmount, detailData.currency) }}
          </el-descriptions-item>
          <el-descriptions-item label="税额">{{
            formatAmount(detailData.vatAmount, detailData.currency)
          }}</el-descriptions-item>
          <el-descriptions-item label="IBAN">{{ detailData.iban || '-' }}</el-descriptions-item>
          <el-descriptions-item label="BIC">{{ detailData.bic || '-' }}</el-descriptions-item>
          <el-descriptions-item label="费用类别">
            <dict-tag
              :type="DICT_TYPE.FINANCE_INVOICE_EXPENSE_CATEGORY"
              :value="detailData.expenseCategory"
            />
          </el-descriptions-item>
          <el-descriptions-item label="项目">{{
            detailData.projectName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="PO号">{{ detailData.poNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同号">{{
            detailData.contractNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="业务说明" :span="2">
            {{ detailData.businessDesc || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            detailData.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="状态与风险" name="risk">
        <el-descriptions v-if="detailData" :column="2" border>
          <el-descriptions-item label="AI状态">
            <dict-tag :type="DICT_TYPE.FINANCE_INVOICE_AI_STATUS" :value="detailData.aiStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="置信度">
            {{ detailData.aiConfidence ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="确认状态">
            <dict-tag
              :type="DICT_TYPE.FINANCE_INVOICE_REVIEW_STATUS"
              :value="detailData.financeReviewStatus"
            />
          </el-descriptions-item>
          <el-descriptions-item label="审批状态">
            <dict-tag
              :type="DICT_TYPE.FINANCE_INVOICE_APPROVAL_STATUS"
              :value="detailData.approvalStatus"
            />
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <dict-tag :type="DICT_TYPE.FINANCE_INVOICE_RISK_LEVEL" :value="detailData.riskLevel" />
          </el-descriptions-item>
          <el-descriptions-item label="流程实例">{{
            detailData.processInstanceId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="风险标记" :span="2">
            <el-space wrap>
              <dict-tag
                v-for="flag in parseRiskFlags(detailData.riskFlags)"
                :key="flag"
                :type="DICT_TYPE.FINANCE_INVOICE_RISK_FLAG"
                :value="flag"
              />
              <span v-if="parseRiskFlags(detailData.riskFlags).length === 0">-</span>
            </el-space>
          </el-descriptions-item>
          <el-descriptions-item label="风险摘要" :span="2">
            {{ detailData.riskSummary || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="AI摘要" :span="2">
            {{ detailData.aiSummary || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="AI错误" :span="2">
            {{ detailData.aiErrorMessage || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="原始结果" name="raw">
        <el-input
          :model-value="detailData?.aiRawResult || ''"
          type="textarea"
          :rows="14"
          readonly
          placeholder="暂无 AI 原始结果"
        />
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="detailVisible = false">关 闭</el-button>
    </template>
  </Dialog>

  <Dialog v-model="confirmVisible" title="财务确认" width="980">
    <el-form
      ref="confirmFormRef"
      v-loading="confirmLoading"
      :model="confirmForm"
      :rules="confirmRules"
      label-width="118px"
    >
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="供应商" prop="supplierName">
            <el-input v-model="confirmForm.supplierName" maxlength="255" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="税号" prop="supplierTaxNo">
            <el-input v-model="confirmForm.supplierTaxNo" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="发票号" prop="invoiceNo">
            <el-input v-model="confirmForm.invoiceNo" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="发票日期" prop="invoiceDate">
            <el-date-picker
              v-model="confirmForm.invoiceDate"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="到期日期" prop="dueDate">
            <el-date-picker
              v-model="confirmForm.dueDate"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="币种" prop="currency">
            <el-input v-model="confirmForm.currency" maxlength="16" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="未税金额" prop="netAmount">
            <el-input-number
              v-model="confirmForm.netAmount"
              :precision="2"
              :min="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="税额" prop="vatAmount">
            <el-input-number
              v-model="confirmForm.vatAmount"
              :precision="2"
              :min="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="含税金额" prop="grossAmount">
            <el-input-number
              v-model="confirmForm.grossAmount"
              :precision="2"
              :min="0"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="IBAN" prop="iban">
            <el-input v-model="confirmForm.iban" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="BIC" prop="bic">
            <el-input v-model="confirmForm.bic" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="账户名" prop="paymentAccountName">
            <el-input v-model="confirmForm.paymentAccountName" maxlength="255" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="费用类别" prop="expenseCategory">
            <el-select v-model="confirmForm.expenseCategory" clearable class="!w-1/1">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_EXPENSE_CATEGORY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="PO号" prop="poNo">
            <el-input v-model="confirmForm.poNo" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="合同号" prop="contractNo">
            <el-input v-model="confirmForm.contractNo" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="项目" prop="projectName">
            <el-input v-model="confirmForm.projectName" maxlength="255" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="业务说明" prop="businessDesc">
            <el-input v-model="confirmForm.businessDesc" maxlength="1000" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="confirmForm.remark"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="confirmLoading" @click="submitConfirm">
        确认并检测风险
      </el-button>
      <el-button @click="confirmVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="paymentVisible" title="更新付款状态" width="560">
    <el-form
      ref="paymentFormRef"
      :model="paymentForm"
      :rules="paymentRules"
      label-width="100px"
      v-loading="paymentLoading"
    >
      <el-form-item label="付款状态" prop="paymentStatus">
        <el-select v-model="paymentForm.paymentStatus" class="!w-1/1">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.FINANCE_INVOICE_PAYMENT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="付款时间" prop="paymentTime">
        <el-date-picker
          v-model="paymentForm.paymentTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="付款备注" prop="paymentRemark">
        <el-input
          v-model="paymentForm.paymentRemark"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="paymentLoading" @click="submitPayment">确 定</el-button>
      <el-button @click="paymentVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <el-dialog
    v-model="previewVisible"
    :width="previewFullscreen ? '96vw' : '980px'"
    destroy-on-close
    @closed="handlePreviewClosed"
  >
    <template #header="{ titleId, titleClass }">
      <div class="preview-dialog-header">
        <span :id="titleId" :class="titleClass">{{ previewTitle }}</span>
        <el-button link type="primary" @click="previewFullscreen = !previewFullscreen">
          <Icon :icon="previewFullscreen ? 'ep:copy-document' : 'ep:full-screen'" class="mr-5px" />
          {{ previewFullscreen ? '还原' : '最大化' }}
        </el-button>
      </div>
    </template>
    <div v-loading="previewLoading" class="preview-dialog-body">
      <iframe
        v-if="previewMode === 'iframe' && previewUrl"
        :src="previewUrl"
        class="preview-frame"
        :class="{ 'is-fullscreen': previewFullscreen }"
      ></iframe>
      <img
        v-else-if="previewMode === 'image' && previewUrl"
        :src="previewUrl"
        class="invoice-preview-image"
        alt="invoice preview"
      />
      <VueOfficeDocx
        v-else-if="previewMode === 'docx' && previewSource"
        :src="previewSource"
        class="office-preview"
        :class="{ 'is-fullscreen': previewFullscreen }"
        @error="handleOfficePreviewError"
      />
      <el-empty v-else-if="previewMode === 'unsupported'" :description="previewUnsupportedText" />
      <el-empty v-else description="暂无可预览内容" />
    </div>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { FormRules, UploadFile, UploadUserFile } from 'element-plus'
import VueOfficeDocx from '@vue-office/docx/lib/v3/vue-office-docx.mjs'
import '@vue-office/docx/lib/v3/index.css'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter, dateFormatter2, formatDate } from '@/utils/formatTime'
import download from '@/utils/download'
import {
  FinanceInvoiceApi,
  FinanceInvoiceConfirmReqVO,
  FinanceInvoiceUpdatePaymentReqVO,
  FinanceInvoiceVO
} from '@/api/finance/invoice'

defineOptions({ name: 'FinanceInvoice' })

const route = useRoute()
const message = useMessage()
const { t } = useI18n()

const allowedExtensions = ['pdf', 'jpg', 'jpeg', 'png', 'tif', 'tiff', 'doc', 'docx']
const uploadAccept = allowedExtensions.map((item) => `.${item}`).join(',')
const maxFileSize = 20 * 1024 * 1024

const loading = ref(false)
const list = ref<FinanceInvoiceVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const invoiceDateRange = ref<string[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  supplierName: undefined as string | undefined,
  invoiceNo: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
  aiStatus: undefined as string | undefined,
  financeReviewStatus: undefined as string | undefined,
  paymentStatus: undefined as string | undefined,
  bookkeepingStatus: undefined as string | undefined,
  riskLevel: undefined as string | undefined,
  invoiceDateStart: undefined as string | undefined,
  invoiceDateEnd: undefined as string | undefined
})
const exportLoading = ref(false)
const actionLoading = reactive<Record<string, boolean>>({})

const routeMode = computed(() => {
  if (route.path.endsWith('/invoice/upload')) return 'upload'
  if (route.path.endsWith('/invoice/confirm')) return 'confirm'
  return 'ledger'
})

const showUploadPanel = ref(routeMode.value === 'upload')
const uploadRef = ref()
const fileList = ref<UploadUserFile[]>([])
const autoRecognize = ref(true)
const uploadLoading = ref(false)

const detailVisible = ref(false)
const detailTab = ref('base')
const detailData = ref<FinanceInvoiceVO>()

const confirmVisible = ref(false)
const confirmLoading = ref(false)
const confirmFormRef = ref()
const currentInvoiceId = ref<number>()
const confirmForm = reactive<FinanceInvoiceConfirmReqVO>(createEmptyConfirmForm())
const confirmRules = reactive<FormRules>({
  supplierName: [{ required: true, message: '供应商不能为空', trigger: 'blur' }],
  invoiceNo: [{ required: true, message: '发票号不能为空', trigger: 'blur' }],
  currency: [{ required: true, message: '币种不能为空', trigger: 'blur' }],
  grossAmount: [{ required: true, message: '含税金额不能为空', trigger: 'blur' }]
})

const paymentVisible = ref(false)
const paymentLoading = ref(false)
const paymentFormRef = ref()
const paymentInvoiceId = ref<number>()
const paymentForm = reactive<FinanceInvoiceUpdatePaymentReqVO>({
  paymentStatus: 'PAID',
  paymentTime: undefined,
  paymentRemark: undefined
})
const paymentRules = reactive<FormRules>({
  paymentStatus: [{ required: true, message: '付款状态不能为空', trigger: 'change' }]
})

const previewVisible = ref(false)
const previewFullscreen = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('发票预览')
const previewUrl = ref('')
const previewSource = shallowRef<Blob | ArrayBuffer | string>('')
const previewMode = ref<'iframe' | 'image' | 'docx' | 'unsupported'>('iframe')
const previewUnsupportedText = ref('当前文件格式暂不支持在线预览')

const getList = async () => {
  loading.value = true
  try {
    applyDateRange()
    const data = await FinanceInvoiceApi.getInvoicePage(queryParams)
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
  queryFormRef.value?.resetFields()
  invoiceDateRange.value = []
  queryParams.invoiceDateStart = undefined
  queryParams.invoiceDateEnd = undefined
  applyRoutePreset()
  handleQuery()
}

const applyDateRange = () => {
  queryParams.invoiceDateStart = invoiceDateRange.value?.[0]
  queryParams.invoiceDateEnd = invoiceDateRange.value?.[1]
}

const applyRoutePreset = () => {
  showUploadPanel.value = routeMode.value === 'upload'
  queryParams.financeReviewStatus = routeMode.value === 'confirm' ? 'PENDING' : undefined
}

const submitUpload = async () => {
  const rawFile = fileList.value[0]?.raw as File | undefined
  if (!rawFile) {
    message.error('请先选择发票文件')
    return
  }
  if (!validateFile(rawFile)) return
  uploadLoading.value = true
  try {
    const id = await FinanceInvoiceApi.uploadInvoice(rawFile)
    if (autoRecognize.value) {
      await FinanceInvoiceApi.recognizeInvoice(id, false)
    }
    message.success(autoRecognize.value ? '上传并识别完成' : '上传完成')
    clearUpload()
    await getList()
  } finally {
    uploadLoading.value = false
  }
}

const handleFileChange = (file: UploadFile) => {
  const rawFile = file.raw as File | undefined
  if (!rawFile || !validateFile(rawFile)) {
    fileList.value = []
    return
  }
  fileList.value = [file]
}

const handleFileExceed = () => {
  message.error('一次只能上传一个发票文件')
}

const clearUpload = () => {
  fileList.value = []
  uploadRef.value?.clearFiles()
}

const validateFile = (file: File) => {
  const extension = getFileExtension(file.name)
  if (!allowedExtensions.includes(extension)) {
    message.error('仅支持 PDF、JPG、PNG、TIFF、DOC、DOCX 文件')
    return false
  }
  if (file.size > maxFileSize) {
    message.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

const openDetail = async (id?: number) => {
  if (!id) return
  detailData.value = await FinanceInvoiceApi.getInvoice(id)
  detailTab.value = 'base'
  detailVisible.value = true
}

const openConfirm = async (id?: number) => {
  if (!id) return
  confirmLoading.value = true
  confirmVisible.value = true
  currentInvoiceId.value = id
  try {
    const data = await FinanceInvoiceApi.getInvoice(id)
    Object.assign(confirmForm, createEmptyConfirmForm(), {
      supplierName: data.supplierName,
      supplierTaxNo: data.supplierTaxNo,
      invoiceNo: data.invoiceNo,
      invoiceDate: data.invoiceDate,
      dueDate: data.dueDate,
      currency: data.currency || 'EUR',
      netAmount: data.netAmount,
      vatAmount: data.vatAmount,
      grossAmount: data.grossAmount,
      iban: data.iban,
      bic: data.bic,
      paymentAccountName: data.paymentAccountName,
      expenseCategory: data.expenseCategory,
      businessDesc: data.businessDesc,
      poNo: data.poNo,
      contractNo: data.contractNo,
      projectName: data.projectName,
      remark: data.remark
    })
  } finally {
    confirmLoading.value = false
  }
}

const submitConfirm = async () => {
  const valid = await confirmFormRef.value?.validate()
  if (!valid || !currentInvoiceId.value) return
  confirmLoading.value = true
  try {
    const risk = await FinanceInvoiceApi.confirmInvoice(currentInvoiceId.value, confirmForm)
    message.success(`确认完成，风险等级：${risk?.riskLevel || 'NONE'}`)
    confirmVisible.value = false
    await getList()
  } finally {
    confirmLoading.value = false
  }
}

const handleRecognize = async (row: FinanceInvoiceVO) => {
  if (!row.id) return
  setActionLoading('recognize', row.id, true)
  try {
    const force = row.aiStatus === 'RECOGNIZED' || row.aiStatus === 'FAILED'
    await FinanceInvoiceApi.recognizeInvoice(row.id, force)
    message.success('AI 识别完成')
    await getList()
  } finally {
    setActionLoading('recognize', row.id, false)
  }
}

const handleSubmitApproval = async (row: FinanceInvoiceVO) => {
  if (!row.id) return
  try {
    await message.confirm('确认提交该发票进入审批流程吗？')
    const processInstanceId = await FinanceInvoiceApi.submitApproval(row.id)
    message.success(`已提交审批：${processInstanceId}`)
    await getList()
  } catch {}
}

const handleApprovalResult = async (
  row: FinanceInvoiceVO,
  approvalStatus: 'APPROVED' | 'REJECTED'
) => {
  if (!row.processInstanceId) return
  try {
    await message.confirm(
      `确认将该发票标记为${approvalStatus === 'APPROVED' ? '审批通过' : '审批驳回'}吗？`
    )
    await FinanceInvoiceApi.syncApprovalResult(row.processInstanceId, approvalStatus)
    message.success('审批结果已回写')
    await getList()
  } catch {}
}

const handleToggleBookkeeping = async (row: FinanceInvoiceVO) => {
  if (!row.id) return
  const nextStatus = row.bookkeepingStatus === 'BOOKED' ? 'NOT_BOOKED' : 'BOOKED'
  try {
    await message.confirm(`确认${nextStatus === 'BOOKED' ? '标记已记账' : '取消记账'}吗？`)
    await FinanceInvoiceApi.updateBookkeeping(row.id, nextStatus)
    message.success('记账状态已更新')
    await getList()
  } catch {}
}

const openPayment = (row: FinanceInvoiceVO) => {
  if (!row.id) return
  paymentInvoiceId.value = row.id
  paymentForm.paymentStatus = row.paymentStatus || 'PAID'
  paymentForm.paymentTime =
    row.paymentTime ||
    (row.paymentStatus === 'PAID' ? undefined : formatDate(new Date(), 'YYYY-MM-DDTHH:mm:ss'))
  paymentForm.paymentRemark = row.paymentRemark
  paymentVisible.value = true
}

const submitPayment = async () => {
  const valid = await paymentFormRef.value?.validate()
  if (!valid || !paymentInvoiceId.value) return
  paymentLoading.value = true
  try {
    await FinanceInvoiceApi.updatePayment(paymentInvoiceId.value, paymentForm)
    message.success('付款状态已更新')
    paymentVisible.value = false
    await getList()
  } finally {
    paymentLoading.value = false
  }
}

const handleDelete = async (row: FinanceInvoiceVO) => {
  if (!row.id) return
  try {
    await message.delConfirm()
    await FinanceInvoiceApi.deleteInvoice(row.id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    applyDateRange()
    const data = await FinanceInvoiceApi.exportInvoices(queryParams)
    download.excel(data, 'AI发票台账.xlsx')
  } finally {
    exportLoading.value = false
  }
}

const openPreview = async (row: FinanceInvoiceVO) => {
  if (!row.id) return
  releasePreviewUrl()
  previewTitle.value = row.fileName || row.invoiceCode || '发票预览'
  previewFullscreen.value = false
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewMode.value = resolvePreviewMode(row.fileType || row.fileName)
    if (previewMode.value === 'unsupported') {
      previewUnsupportedText.value = resolveUnsupportedPreviewText(row.fileType || row.fileName)
      return
    }
    const blob = await FinanceInvoiceApi.previewInvoice(row.id)
    if (previewMode.value === 'docx') {
      previewSource.value = blob
    } else {
      previewUrl.value = URL.createObjectURL(blob)
    }
  } finally {
    previewLoading.value = false
  }
}

const handlePreviewClosed = () => {
  previewFullscreen.value = false
  releasePreviewUrl()
}

const releasePreviewUrl = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewSource.value = ''
  previewMode.value = 'iframe'
  previewUnsupportedText.value = '当前文件格式暂不支持在线预览'
}

const resolvePreviewMode = (fileTypeOrName?: string) => {
  const extension = getFileExtension(fileTypeOrName)
  if (extension === 'pdf') return 'iframe'
  if (['jpg', 'jpeg', 'png', 'tif', 'tiff'].includes(extension)) return 'image'
  if (extension === 'docx') return 'docx'
  return 'unsupported'
}

const resolveUnsupportedPreviewText = (fileTypeOrName?: string) => {
  const extension = getFileExtension(fileTypeOrName).toUpperCase()
  if (extension === 'DOC') {
    return 'DOC 是旧版 Office 格式，当前在线预览组件暂不支持；请转换为 DOCX 后预览。'
  }
  return '当前文件格式暂不支持在线预览'
}

const handleOfficePreviewError = () => {
  message.error('Office 文件预览失败，请确认文件格式和内容是否有效')
}

function createEmptyConfirmForm(): FinanceInvoiceConfirmReqVO {
  return {
    supplierName: undefined,
    supplierTaxNo: undefined,
    invoiceNo: undefined,
    invoiceDate: undefined,
    dueDate: undefined,
    currency: 'EUR',
    netAmount: undefined,
    vatAmount: undefined,
    grossAmount: undefined,
    iban: undefined,
    bic: undefined,
    paymentAccountName: undefined,
    expenseCategory: undefined,
    businessDesc: undefined,
    poNo: undefined,
    contractNo: undefined,
    projectName: undefined,
    remark: undefined
  }
}

const canSubmitApproval = (row: FinanceInvoiceVO) => {
  return (
    row.financeReviewStatus === 'CONFIRMED' &&
    !['APPROVING', 'APPROVED'].includes(row.approvalStatus || '')
  )
}

const canSyncApproval = (row: FinanceInvoiceVO) => {
  return row.approvalStatus === 'APPROVING' && !!row.processInstanceId
}

const parseRiskFlags = (riskFlags?: string) => {
  if (!riskFlags) return []
  try {
    const flags = JSON.parse(riskFlags)
    return Array.isArray(flags) ? flags : []
  } catch {
    return []
  }
}

const formatAmount = (amount?: number, currency?: string) => {
  if (amount === undefined || amount === null) return '-'
  return `${currency || 'EUR'} ${Number(amount).toFixed(2)}`
}

const formatFileSize = (size?: number) => {
  if (!size && size !== 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const formatDateTime = (value?: string) => {
  return value ? value.replace('T', ' ') : '-'
}

const getFileExtension = (fileTypeOrName?: string) => {
  if (!fileTypeOrName) return ''
  const value = fileTypeOrName.toLowerCase()
  const dotIndex = value.lastIndexOf('.')
  return dotIndex >= 0 ? value.substring(dotIndex + 1) : value
}

const setActionLoading = (action: string, id: number, value: boolean) => {
  actionLoading[`${action}-${id}`] = value
}

const getActionLoading = (action: string, id?: number) => {
  return id ? actionLoading[`${action}-${id}`] === true : false
}

watch(
  () => route.path,
  () => {
    applyRoutePreset()
    handleQuery()
  }
)

onMounted(() => {
  applyRoutePreset()
  getList()
})
</script>

<style scoped>
.invoice-upload-layout {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) 260px;
  gap: 16px;
  align-items: stretch;
}

.invoice-upload {
  min-width: 0;
}

.invoice-upload-actions {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
}

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 32px;
}

.preview-dialog-body {
  min-height: 520px;
}

.preview-frame,
.office-preview {
  width: 100%;
  height: 520px;
  border: 1px solid var(--el-border-color-light);
}

.preview-frame.is-fullscreen,
.office-preview.is-fullscreen {
  height: calc(96vh - 130px);
}

.invoice-preview-image {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin: 0 auto;
  object-fit: contain;
}

@media (max-width: 768px) {
  .invoice-upload-layout {
    grid-template-columns: 1fr;
  }
}
</style>
