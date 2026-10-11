<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="供应商名称" prop="supplierName">
        <el-input v-model="queryParams.supplierName" placeholder="请输入供应商名称" maxlength="200" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="启用" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['erp:supplier:add']" type="primary" plain icon="Plus" @click="handleAdd">新增供应商</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>
    <el-alert v-if="listFailed" title="供应商加载失败，请重新搜索或刷新。" type="error" :closable="false" show-icon class="mb8" />
    <el-table v-else v-loading="loading" :data="supplierList">
      <el-table-column label="编号" prop="supplierId" width="90" />
      <el-table-column label="供应商名称" prop="supplierName" min-width="220" show-overflow-tooltip />
      <el-table-column label="联系人" prop="contactName" min-width="120" show-overflow-tooltip />
      <el-table-column label="联系方式" prop="contactPhone" min-width="160" show-overflow-tooltip />
      <el-table-column label="结算说明" prop="settlementTerms" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope"><el-tag :type="scope.row.status === '0' ? 'success' : 'info'">{{ statusLabel(scope.row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['erp:supplier:query']" link type="primary" :disabled="fetching" @click="handleDetail(scope.row)">详情</el-button>
          <el-button v-hasPermi="['erp:supplier:edit']" link type="primary" :disabled="fetching" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button v-hasPermi="['erp:supplier:status']" link :type="scope.row.status === '0' ? 'danger' : 'primary'" :disabled="fetching" @click="handleStatus(scope.row)">{{ scope.row.status === '0' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="!listFailed && total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="editOpen" :title="form.supplierId ? '编辑供应商' : '新增供应商'" width="640px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeEdit">
      <el-alert v-if="saveFailed" :title="form.supplierId ? '保存未成功，填写内容已保留。若提示资料已变化，请关闭并重新打开，核对最新资料后再保存。' : '未确认保存成功，填写内容已保留。可重试；重新新增前请刷新列表核对。'" type="warning" :closable="false" show-icon class="mb8" />
      <el-form ref="supplierRef" :model="form" :rules="rules" :disabled="saving" label-width="100px">
        <el-form-item label="供应商名称" prop="supplierName"><el-input v-model="form.supplierName" maxlength="200" show-word-limit placeholder="请输入供应商名称" /></el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" maxlength="100" placeholder="请输入联系人" /></el-form-item>
        <el-form-item label="联系方式" prop="contactPhone"><el-input v-model="form.contactPhone" maxlength="100" placeholder="电话、邮箱或其他联系方式" /></el-form-item>
        <el-form-item label="结算说明" prop="settlementTerms"><el-input v-model="form.settlementTerms" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="记录约定，不自动生成付款计划" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="form.supplierId" label="修改原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请说明本次修改原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="submitForm">保存</el-button>
        <el-button :disabled="saving" @click="closeEdit()">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusOpen" :title="statusForm.status === '1' ? '停用供应商' : '启用供应商'" width="520px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!statusSaving" :show-close="!statusSaving">
      <p class="status-description">{{ statusForm.supplierName }}：{{ statusForm.status === '1' ? '停用后保留资料和变更记录。' : '确认恢复启用。' }}</p>
      <el-alert v-if="statusFailed" title="操作未成功。若提示资料已变化，请取消并刷新列表，核对状态后再操作。" type="warning" :closable="false" show-icon class="mb8" />
      <el-form ref="statusRef" :model="statusForm" :rules="statusRules" :disabled="statusSaving" label-width="80px">
        <el-form-item label="操作原因" prop="reason"><el-input v-model="statusForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入启停原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="statusSaving" :disabled="statusSaving" @click="submitStatus">确认</el-button>
        <el-button :disabled="statusSaving" @click="statusOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="供应商详情" width="860px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="供应商名称" :span="2">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ detail.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="最近修改">{{ detail.updateTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结算说明" :span="2"><span class="multiline">{{ detail.settlementTerms || '—' }}</span></el-descriptions-item>
        <el-descriptions-item label="备注" :span="2"><span class="multiline">{{ detail.remark || '—' }}</span></el-descriptions-item>
      </el-descriptions>
      <h4>变更记录</h4>
      <el-alert v-if="changesFailed" title="变更记录加载失败" type="error" :closable="false" show-icon>
        <el-button link type="primary" @click="getChanges">重试</el-button>
      </el-alert>
      <el-table v-else v-loading="changesLoading" :data="changes">
        <el-table-column label="操作" width="90"><template #default="scope">{{ operationLabel(scope.row.operation) }}</template></el-table-column>
        <el-table-column label="原因" prop="reason" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作人" prop="operator" width="120" />
        <el-table-column label="时间" prop="changeTime" width="180" />
      </el-table>
      <pagination v-show="!changesFailed && changesTotal > 0" :total="changesTotal" v-model:page="changesQuery.pageNum" v-model:limit="changesQuery.pageSize" @pagination="getChanges" />
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="ErpSupplier">
import { listSupplier, getSupplier, listSupplierChanges, addSupplier, updateSupplier, changeSupplierStatus } from '@/api/erp/supplier'

const { proxy } = getCurrentInstance()
const supplierList = ref([])
const loading = ref(false)
const listFailed = ref(false)
const showSearch = ref(true)
const total = ref(0)
const fetching = ref(false)
const editOpen = ref(false)
const saving = ref(false)
const saveFailed = ref(false)
const statusOpen = ref(false)
const statusSaving = ref(false)
const statusFailed = ref(false)
const detailOpen = ref(false)
const changesLoading = ref(false)
const changesFailed = ref(false)
const changes = ref([])
const changesTotal = ref(0)
const detail = ref({})
const form = ref({})
const statusForm = ref({})
const queryParams = reactive({ pageNum: 1, pageSize: 10, supplierName: '', status: undefined })
const changesQuery = reactive({ pageNum: 1, pageSize: 10 })
let listSequence = 0
let changesSequence = 0

const nonblank = (rule, value, callback) => value?.trim() ? callback() : callback(new Error(rule.message))
const rules = {
  supplierName: [{ required: true, validator: nonblank, message: '供应商名称不能为空', trigger: 'blur' }],
  reason: [{ required: true, validator: nonblank, message: '请填写修改原因', trigger: 'blur' }]
}
const statusRules = { reason: [{ required: true, validator: nonblank, message: '请填写启停原因', trigger: 'blur' }] }
const statusLabel = value => value === '0' ? '启用' : value === '1' ? '停用' : '—'
const operationLabel = value => ({ CREATE: '新增', UPDATE: '编辑', STATUS: '启停' }[value] || value)

async function getList() {
  const sequence = ++listSequence
  loading.value = true
  listFailed.value = false
  try {
    const response = await listSupplier({ ...queryParams })
    if (sequence !== listSequence) return
    supplierList.value = response.rows
    total.value = response.total
  } catch {
    if (sequence === listSequence) listFailed.value = true
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleAdd() {
  form.value = { supplierName: '', contactName: '', contactPhone: '', settlementTerms: '', remark: '', requestId: crypto.randomUUID() }
  saveFailed.value = false
  editOpen.value = true
  nextTick(() => proxy.$refs.supplierRef?.clearValidate())
}

async function handleEdit(row) {
  if (fetching.value) return
  fetching.value = true
  try {
    const response = await getSupplier(row.supplierId)
    form.value = { ...response.data, reason: '' }
    saveFailed.value = false
    editOpen.value = true
    nextTick(() => proxy.$refs.supplierRef?.clearValidate())
  } catch { /* 请求封装已显示错误，保留当前列表。 */ }
  finally { fetching.value = false }
}

function closeEdit(done) {
  if (saving.value) return
  editOpen.value = false
  if (typeof done === 'function') done()
}

async function submitForm() {
  if (saving.value) return
  saving.value = true
  try {
    if (!await proxy.$refs.supplierRef.validate().catch(() => false)) return
    const data = { supplierName: form.value.supplierName, contactName: form.value.contactName,
      contactPhone: form.value.contactPhone, settlementTerms: form.value.settlementTerms, remark: form.value.remark }
    if (form.value.supplierId) {
      await updateSupplier(form.value.supplierId, { ...data, version: form.value.version, reason: form.value.reason })
    } else {
      // 失败重试保留同一创建标识，不能每次点击重新生成。
      await addSupplier({ ...data, requestId: form.value.requestId })
    }
    editOpen.value = false
    proxy.$modal.msgSuccess('保存成功')
    getList()
  } catch { saveFailed.value = true }
  finally { saving.value = false }
}

async function handleStatus(row) {
  if (fetching.value) return
  fetching.value = true
  try {
    const { data } = await getSupplier(row.supplierId)
    statusForm.value = { supplierId: data.supplierId, supplierName: data.supplierName, version: data.version, status: data.status === '0' ? '1' : '0', reason: '' }
    statusFailed.value = false
    statusOpen.value = true
    nextTick(() => proxy.$refs.statusRef?.clearValidate())
  } catch { /* 读取失败不打开操作窗口。 */ }
  finally { fetching.value = false }
}

async function submitStatus() {
  if (statusSaving.value) return
  statusSaving.value = true
  try {
    if (!await proxy.$refs.statusRef.validate().catch(() => false)) return
    await changeSupplierStatus(statusForm.value.supplierId, { status: statusForm.value.status, version: statusForm.value.version, reason: statusForm.value.reason })
    statusOpen.value = false
    proxy.$modal.msgSuccess('状态修改成功')
    getList()
  } catch { statusFailed.value = true }
  finally { statusSaving.value = false }
}

async function handleDetail(row) {
  if (fetching.value) return
  fetching.value = true
  try {
    const response = await getSupplier(row.supplierId)
    detail.value = response.data
    changesQuery.pageNum = 1
    changes.value = []
    changesTotal.value = 0
    detailOpen.value = true
    getChanges()
  } catch { /* 读取失败不展示空详情。 */ }
  finally { fetching.value = false }
}

async function getChanges() {
  const id = detail.value.supplierId
  const sequence = ++changesSequence
  changesLoading.value = true
  changesFailed.value = false
  try {
    const response = await listSupplierChanges(id, { ...changesQuery })
    if (sequence !== changesSequence || id !== detail.value.supplierId) return
    changes.value = response.rows
    changesTotal.value = response.total
  } catch {
    if (sequence === changesSequence) changesFailed.value = true
  } finally {
    if (sequence === changesSequence) changesLoading.value = false
  }
}

getList()
</script>

<style scoped>
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
.status-description { overflow-wrap: anywhere; margin-bottom: 20px; }
</style>
