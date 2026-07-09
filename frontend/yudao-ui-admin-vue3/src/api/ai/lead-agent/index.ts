import request from '@/config/axios'

export interface LeadMarketVO {
  id?: number
  categoryCode: string
  categoryName?: string
  weight: number
  enabled: boolean
  countries: string[]
  keywords: string[]
}

export interface LeadFilterRuleVO {
  blockedDomainKeywords: string[]
  blockedFileExtensions: string[]
}

export interface LeadExportRuleVO {
  minScore: number
  includeTargetOnly: boolean
  requireEmail: boolean
  allowedGrades: string[]
  includePossibleDuplicates: boolean
  writeRejectedFile: boolean
}

export interface LeadRunCreateReqVO {
  categoryCode?: string
  country?: string
  searchProvider?: string
  enableAiReview?: boolean
  skipSocialVerification?: boolean
  maxResults?: number
  maxPagesPerSite?: number
  crawlTimeoutSeconds?: number
}

export interface LeadCrawlJobVO {
  id: number
  runId: string
  categoryCode?: string
  country?: string
  maxResults: number
  maxPagesPerSite: number
  crawlTimeoutSeconds: number
  searchProvider: string
  analysisProvider: string
  skipSocialVerification: boolean
  enableAiReview: boolean
  status: string
  totalCandidates: number
  crawledCount: number
  leadCount: number
  exportedCount: number
  rejectedCount: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createTime?: string
  updateTime?: string
}

export const getDashboard = async () => {
  return await request.get({ url: '/ai/lead-agent/dashboard' })
}

export const startRun = async (data: LeadRunCreateReqVO) => {
  return await request.post({ url: '/ai/lead-agent/run/start', data })
}

export const getJobPage = async (params) => {
  return await request.get({ url: '/ai/lead-agent/job/page', params })
}

export const getJob = async (id: number) => {
  return await request.get({ url: '/ai/lead-agent/job/get?id=' + id })
}

export const getMarketPage = async (params) => {
  return await request.get({ url: '/ai/lead-agent/market/page', params })
}

export const getMarketList = async () => {
  return await request.get({ url: '/ai/lead-agent/market/list' })
}

export const getMarket = async (id: number) => {
  return await request.get({ url: '/ai/lead-agent/market/get?id=' + id })
}

export const createMarket = async (data: LeadMarketVO) => {
  return await request.post({ url: '/ai/lead-agent/market/create', data })
}

export const updateMarket = async (data: LeadMarketVO) => {
  return await request.put({ url: '/ai/lead-agent/market/update', data })
}

export const deleteMarket = async (id: number) => {
  return await request.delete({ url: '/ai/lead-agent/market/delete?id=' + id })
}

export const getFilterRules = async () => {
  return await request.get({ url: '/ai/lead-agent/filter-rules' })
}

export const updateFilterRules = async (data: LeadFilterRuleVO) => {
  return await request.put({ url: '/ai/lead-agent/filter-rules', data })
}

export const getExportRules = async () => {
  return await request.get({ url: '/ai/lead-agent/export-rules' })
}

export const updateExportRules = async (data: LeadExportRuleVO) => {
  return await request.put({ url: '/ai/lead-agent/export-rules', data })
}

export const getCustomerPage = async (params) => {
  return await request.get({ url: '/ai/lead-agent/customer/page', params })
}

export const exportCustomers = async (params) => {
  return await request.download<Blob>({ url: '/ai/lead-agent/customer/export-excel', params })
}

export const getHistoryPage = async (params) => {
  return await request.get({ url: '/ai/lead-agent/history/page', params })
}
