import request from '@/utils/request'

export function listSupplier(query) {
  return request({ url: '/erp/supplier/list', method: 'get', params: query })
}

export function getSupplier(id) {
  return request({ url: `/erp/supplier/${id}`, method: 'get' })
}

export function listSupplierChanges(id, query) {
  return request({ url: `/erp/supplier/${id}/changes`, method: 'get', params: query })
}

export function addSupplier(data) {
  return request({ url: '/erp/supplier', method: 'post', data })
}

export function updateSupplier(id, data) {
  return request({ url: `/erp/supplier/${id}`, method: 'put', data })
}

export function changeSupplierStatus(id, data) {
  return request({ url: `/erp/supplier/${id}/status`, method: 'put', data })
}
