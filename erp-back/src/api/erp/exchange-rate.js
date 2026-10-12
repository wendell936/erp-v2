import request from '@/utils/request'

export const listExchangeRates = params => request({ url: '/erp/exchange-rate/list', method: 'get', params })
export const getExchangeRate = id => request({ url: `/erp/exchange-rate/${id}`, method: 'get' })
export const listRateRevisions = (id, params) => request({ url: `/erp/exchange-rate/${id}/revisions`, method: 'get', params })
export const addExchangeRate = data => request({ url: '/erp/exchange-rate', method: 'post', data })
export const updateExchangeRate = (id, data) => request({ url: `/erp/exchange-rate/${id}`, method: 'put', data })
export const previewConversion = data => request({ url: '/erp/exchange-rate/preview', method: 'post', data, headers: { repeatSubmit: false } })
