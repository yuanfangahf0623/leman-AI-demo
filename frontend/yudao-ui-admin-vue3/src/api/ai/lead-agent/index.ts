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

export const getDashboard = async () => {
  return await request.get({ url: '/ai/lead-agent/dashboard' })
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

export const getHistoryPage = async (params) => {
  return await request.get({ url: '/ai/lead-agent/history/page', params })
}
