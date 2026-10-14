import request from '@/utils/request'

export function listProductType(query) {
  return request({ url: '/erp/product-type/list', method: 'get', params: query })
}

export function getProductType(id) {
  return request({ url: `/erp/product-type/${id}`, method: 'get' })
}

export function listProductTypeChanges(id, query) {
  return request({ url: `/erp/product-type/${id}/changes`, method: 'get', params: query })
}

export function addProductType(data) {
  return request({ url: '/erp/product-type', method: 'post', data })
}

export function updateProductType(id, data) {
  return request({ url: `/erp/product-type/${id}`, method: 'put', data })
}

export function changeProductTypeStatus(id, data) {
  return request({ url: `/erp/product-type/${id}/status`, method: 'put', data })
}
