import request from '@/config/axios'

export interface FinanceInvoiceAttachmentVO {
  id?: number
  fileName?: string
  fileUrl?: string
  fileType?: string
  fileSize?: number
  fileHash?: string
  attachmentType?: string
  createTime?: string
}

export interface FinanceInvoiceVO {
  id?: number
  invoiceCode?: string
  sourceType?: string
  sourceMessageId?: string
  sourceSender?: string
  sourceReceivedTime?: string
  fileId?: number
  fileUrl?: string
  fileName?: string
  fileType?: string
  fileHash?: string
  fileSize?: number
  supplierName?: string
  supplierTaxNo?: string
  invoiceNo?: string
  invoiceDate?: string
  dueDate?: string
  currency?: string
  netAmount?: number
  vatAmount?: number
  grossAmount?: number
  iban?: string
  bic?: string
  paymentAccountName?: string
  expenseCategory?: string
  businessDesc?: string
  poNo?: string
  contractNo?: string
  projectName?: string
  aiStatus?: string
  aiConfidence?: number
  aiRawResult?: string
  aiSummary?: string
  aiErrorMessage?: string
  financeReviewStatus?: string
  riskLevel?: string
  riskFlags?: string
  riskSummary?: string
  approvalStatus?: string
  processInstanceId?: string
  processDefinitionKey?: string
  bookkeepingStatus?: string
  paymentStatus?: string
  paymentTime?: string
  paymentRemark?: string
  remark?: string
  createTime?: string
  updateTime?: string
  attachments?: FinanceInvoiceAttachmentVO[]
}

export interface FinanceInvoicePageReqVO extends PageParam {
  supplierName?: string
  invoiceNo?: string
  approvalStatus?: string
  aiStatus?: string
  financeReviewStatus?: string
  paymentStatus?: string
  bookkeepingStatus?: string
  riskLevel?: string
  invoiceDateStart?: string
  invoiceDateEnd?: string
  createTimeStart?: string
  createTimeEnd?: string
}

export interface FinanceInvoiceConfirmReqVO {
  supplierName?: string
  supplierTaxNo?: string
  invoiceNo?: string
  invoiceDate?: string
  dueDate?: string
  currency?: string
  netAmount?: number
  vatAmount?: number
  grossAmount?: number
  iban?: string
  bic?: string
  paymentAccountName?: string
  expenseCategory?: string
  businessDesc?: string
  poNo?: string
  contractNo?: string
  projectName?: string
  remark?: string
}

export interface FinanceInvoiceUpdatePaymentReqVO {
  paymentStatus: string
  paymentTime?: string
  paymentRemark?: string
}

export const FinanceInvoiceApi = {
  getInvoicePage: async (params: FinanceInvoicePageReqVO) => {
    return await request.get<PageResult<FinanceInvoiceVO[]>>({
      url: '/finance/invoice/page',
      params
    })
  },

  getInvoice: async (id: number) => {
    return await request.get<FinanceInvoiceVO>({ url: `/finance/invoice/${id}` })
  },

  uploadInvoice: async (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return await request.post<number>({
      url: '/finance/invoice/upload',
      data: formData,
      headersType: 'multipart/form-data'
    })
  },

  previewInvoice: async (id: number) => {
    return await request.download<Blob>({ url: `/finance/invoice/preview/${id}` })
  },

  recognizeInvoice: async (id: number, force = false) => {
    return await request.post<boolean>({ url: `/finance/invoice/${id}/recognize`, data: { force } })
  },

  confirmInvoice: async (id: number, data: FinanceInvoiceConfirmReqVO) => {
    return await request.put({ url: `/finance/invoice/${id}/confirm`, data })
  },

  submitApproval: async (id: number, processDefinitionKey?: string) => {
    return await request.post<string>({
      url: `/finance/invoice/${id}/submit-approval`,
      data: processDefinitionKey ? { processDefinitionKey } : {}
    })
  },

  syncApprovalResult: async (processInstanceId: string, approvalStatus: string) => {
    return await request.post<boolean>({
      url: '/finance/invoice/approval-result',
      data: { processInstanceId, approvalStatus }
    })
  },

  updateBookkeeping: async (id: number, bookkeepingStatus: string) => {
    return await request.put<boolean>({
      url: `/finance/invoice/${id}/bookkeeping`,
      data: { bookkeepingStatus }
    })
  },

  updatePayment: async (id: number, data: FinanceInvoiceUpdatePaymentReqVO) => {
    return await request.put<boolean>({ url: `/finance/invoice/${id}/payment`, data })
  },

  deleteInvoice: async (id: number) => {
    return await request.delete<boolean>({ url: `/finance/invoice/${id}` })
  },

  exportInvoices: async (params: FinanceInvoicePageReqVO) => {
    return await request.download<Blob>({ url: '/finance/invoice/export', params })
  }
}
