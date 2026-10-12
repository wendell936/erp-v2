<template>
  <div class="app-container">
    <el-alert title="1 USD = X CNY。按指定日期取值，缺当日汇率需补充；已确认单据保留原快照。" type="info" :closable="false" show-icon class="mb8" />
    <el-form :inline="true" v-show="showSearch">
      <el-form-item label="汇率日期">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" unlink-panels />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button v-hasPermi="['erp:exchangeRate:add']" type="primary" plain icon="Plus" :disabled="fetching" @click="handleAdd">新增汇率</el-button></el-col>
      <el-col :span="1.5"><el-button v-hasPermi="['erp:exchangeRate:query']" plain @click="openPreview">换算预览</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>
    <el-alert v-if="listFailed" title="汇率加载失败，请重新搜索或刷新。" type="error" :closable="false" show-icon />
    <el-table v-else v-loading="loading" :data="rates" empty-text="暂无日期汇率，请按需补充">
      <el-table-column label="汇率日期" prop="rateDate" min-width="140" />
      <el-table-column label="原币" width="100"><template #default>USD</template></el-table-column>
      <el-table-column label="目标币" width="100"><template #default>CNY</template></el-table-column>
      <el-table-column label="1 USD 对应 CNY" prop="usdCny" min-width="180" />
      <el-table-column label="版本" prop="version" width="80" />
      <el-table-column label="最近维护人" min-width="130"><template #default="scope">{{ scope.row.updateBy || scope.row.createBy }}</template></el-table-column>
      <el-table-column label="最近维护时间" min-width="180"><template #default="scope">{{ scope.row.updateTime || scope.row.createTime }}</template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="scope">
          <el-button v-hasPermi="['erp:exchangeRate:query']" link type="primary" :disabled="fetching" @click="handleDetail(scope.row)">详情</el-button>
          <el-button v-hasPermi="['erp:exchangeRate:edit']" link type="primary" :disabled="fetching" @click="handleEdit(scope.row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="!listFailed && total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="getList" />

    <el-dialog v-model="editOpen" :title="form.rateId ? '修改日期汇率' : '新增日期汇率'" width="560px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeEdit">
      <el-alert v-if="saveFailed" title="保存未成功，输入已保留。若提示日期已存在或资料已变化，请关闭并刷新列表，核对后再编辑。" type="warning" :closable="false" show-icon class="mb8" />
      <el-form ref="rateRef" :model="form" :rules="rules" :disabled="saving" label-width="110px">
        <el-form-item label="汇率日期" prop="rateDate"><el-date-picker v-model="form.rateDate" value-format="YYYY-MM-DD" placeholder="请选择汇率日期" :disabled="saving || !!form.rateId" /></el-form-item>
        <el-form-item label="1 USD = CNY" prop="usdCny"><el-input v-model="form.usdCny" maxlength="17" placeholder="请输入正数，最多8位小数" inputmode="decimal" /></el-form-item>
        <el-form-item v-if="form.rateId" label="修改原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明本次汇率修正原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="submitForm">保存</el-button>
        <el-button :disabled="saving" @click="closeEdit()">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="汇率详情与历史版本" width="860px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="汇率日期">{{ detail.rateDate }}</el-descriptions-item>
        <el-descriptions-item label="当前汇率">1 USD = {{ detail.usdCny }} CNY</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy }}</el-descriptions-item>
      </el-descriptions>
      <h4>历史版本</h4>
      <el-alert v-if="revisionsFailed" title="历史版本加载失败" type="error" :closable="false"><el-button link type="primary" @click="getRevisions">重试</el-button></el-alert>
      <el-table v-else v-loading="revisionsLoading" :data="revisions">
        <el-table-column label="版本" prop="version" width="75" />
        <el-table-column label="1 USD 对应 CNY" prop="usdCny" width="160" />
        <el-table-column label="原因" prop="reason" min-width="200" show-overflow-tooltip />
        <el-table-column label="维护人" prop="operator" width="110" />
        <el-table-column label="时间" prop="changeTime" width="180" />
      </el-table>
      <pagination v-show="!revisionsFailed && revisionsTotal > 0" :total="revisionsTotal" v-model:page="revisionQuery.pageNum" v-model:limit="revisionQuery.pageSize" @pagination="getRevisions" />
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="previewOpen" title="原币换算预览" width="600px" append-to-body :close-on-click-modal="false" :close-on-press-escape="!previewing" :show-close="!previewing">
      <el-alert title="仅供参考，不产生费用、库存或资金记录。实际单据确认时重新核对并固定快照。" type="info" :closable="false" show-icon class="mb8" />
      <el-form ref="previewRef" :model="previewForm" :rules="previewRules" :disabled="previewing" label-width="100px">
        <el-form-item label="原币" prop="currency"><el-select v-model="previewForm.currency" @change="clearPreview"><el-option label="USD 美元" value="USD" /><el-option label="CNY 人民币" value="CNY" /></el-select></el-form-item>
        <el-form-item label="原币金额" prop="originalAmount"><el-input v-model="previewForm.originalAmount" maxlength="16" inputmode="decimal" placeholder="最多12位整数、2位小数" @input="clearPreview" /></el-form-item>
        <el-form-item v-if="previewForm.currency === 'USD'" label="汇率日期" prop="rateDate"><el-date-picker v-model="previewForm.rateDate" value-format="YYYY-MM-DD" placeholder="请选择汇率日期" @change="clearPreview" /></el-form-item>
      </el-form>
      <el-alert v-if="previewFailed" title="换算未成功，请核对金额和当日汇率后重试。" type="warning" :closable="false" show-icon />
      <el-descriptions v-if="previewResult" :column="1" border>
        <el-descriptions-item label="原币金额">{{ previewResult.originalAmount }} {{ previewResult.currency }}</el-descriptions-item>
        <el-descriptions-item v-if="previewResult.currency === 'USD'" label="适用汇率">{{ previewResult.rateDate }}，1 USD = {{ previewResult.usdCny }} CNY（版本 {{ previewResult.rateVersion }}）</el-descriptions-item>
        <el-descriptions-item label="人民币金额">{{ previewResult.cnyAmount }} CNY</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" :loading="previewing" :disabled="previewing" @click="calculatePreview">计算</el-button>
        <el-button :disabled="previewing" @click="previewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ErpExchangeRate">
import { listExchangeRates, getExchangeRate, listRateRevisions, addExchangeRate, updateExchangeRate, previewConversion } from '@/api/erp/exchange-rate'
import { onBeforeRouteLeave } from 'vue-router'

const { proxy } = getCurrentInstance()
const rates = ref([]), total = ref(0), loading = ref(false), listFailed = ref(false), showSearch = ref(true)
const dateRange = ref([]), query = reactive({ pageNum: 1, pageSize: 10 })
const editOpen = ref(false), saving = ref(false), fetching = ref(false), saveFailed = ref(false), form = ref({})
const detailOpen = ref(false), detail = ref({}), revisions = ref([]), revisionsTotal = ref(0)
const revisionsLoading = ref(false), revisionsFailed = ref(false), revisionQuery = reactive({ pageNum: 1, pageSize: 10 })
const previewOpen = ref(false), previewing = ref(false), previewFailed = ref(false), previewResult = ref(null)
const previewForm = ref({ currency: 'USD', originalAmount: '', rateDate: '' })
let listSequence = 0, revisionSequence = 0, initialForm = ''

const validRate = (rule, value, callback) => /^[0-9]{1,8}(\.[0-9]{1,8})?$/.test(value || '') && /[1-9]/.test(value)
  ? callback() : callback(new Error('请输入大于0的普通十进制汇率，最多8位整数和8位小数'))
const requiredText = (rule, value, callback) => value?.trim() ? callback() : callback(new Error(rule.message))
const rules = {
  rateDate: [{ required: true, message: '请选择汇率日期', trigger: 'change' }],
  usdCny: [{ required: true, validator: validRate, trigger: 'blur' }],
  reason: [{ required: true, validator: requiredText, message: '请填写修改原因', trigger: 'blur' }]
}
const previewRules = {
  originalAmount: [{ required: true, pattern: /^-?[0-9]{1,12}(\.[0-9]{1,2})?$/, message: '请输入最多12位整数和2位小数的金额', trigger: 'blur' }],
  rateDate: [{ required: true, message: '请选择汇率日期', trigger: 'change' }]
}

async function getList() {
  const sequence = ++listSequence
  loading.value = true; listFailed.value = false
  try {
    const { rows, total: count } = await listExchangeRates({ ...query, from: dateRange.value?.[0], to: dateRange.value?.[1] })
    if (sequence !== listSequence) return
    rates.value = rows; total.value = count
  } catch { if (sequence === listSequence) listFailed.value = true }
  finally { if (sequence === listSequence) loading.value = false }
}
function handleQuery() { query.pageNum = 1; getList() }
function resetQuery() { dateRange.value = []; handleQuery() }
function openEdit(data) {
  form.value = data; initialForm = JSON.stringify(data); saveFailed.value = false; editOpen.value = true
  nextTick(() => proxy.$refs.rateRef?.clearValidate())
}
function handleAdd() { openEdit({ rateDate: '', usdCny: '', requestId: crypto.randomUUID() }) }
async function handleEdit(row) {
  if (fetching.value) return
  fetching.value = true
  try { const { data } = await getExchangeRate(row.rateId); openEdit({ ...data, reason: '' }) }
  catch { /* 保留列表，读取失败不打开空表单。 */ }
  finally { fetching.value = false }
}
async function discardChanges() {
  if (!editOpen.value || JSON.stringify(form.value) === initialForm) return true
  try { await proxy.$modal.confirm('填写内容尚未保存，确认放弃本次修改？'); return true }
  catch { return false }
}
async function closeEdit(done) {
  if (saving.value || !await discardChanges()) return
  editOpen.value = false
  if (typeof done === 'function') done()
}
onBeforeRouteLeave(async () => !saving.value && !previewing.value && await discardChanges())
async function submitForm() {
  if (saving.value) return
  saving.value = true
  try {
    if (!await proxy.$refs.rateRef.validate().catch(() => false)) return
    const data = { rateDate: form.value.rateDate, usdCny: form.value.usdCny }
    if (form.value.rateId) await updateExchangeRate(form.value.rateId, { ...data, version: form.value.version, reason: form.value.reason })
    else await addExchangeRate({ ...data, requestId: form.value.requestId })
    editOpen.value = false; proxy.$modal.msgSuccess('汇率保存成功'); getList()
  } catch { saveFailed.value = true }
  finally { saving.value = false }
}
async function handleDetail(row) {
  if (fetching.value) return
  fetching.value = true
  try {
    const { data } = await getExchangeRate(row.rateId)
    detail.value = data; revisionQuery.pageNum = 1; revisions.value = []; revisionsTotal.value = 0
    detailOpen.value = true; getRevisions()
  } catch { /* 不展示空详情。 */ }
  finally { fetching.value = false }
}
async function getRevisions() {
  const id = detail.value.rateId, sequence = ++revisionSequence
  revisionsLoading.value = true; revisionsFailed.value = false
  try {
    const response = await listRateRevisions(id, { ...revisionQuery })
    if (sequence !== revisionSequence || id !== detail.value.rateId) return
    revisions.value = response.rows; revisionsTotal.value = response.total
  } catch { if (sequence === revisionSequence) revisionsFailed.value = true }
  finally { if (sequence === revisionSequence) revisionsLoading.value = false }
}
function clearPreview() { previewResult.value = null; previewFailed.value = false }
function openPreview() { clearPreview(); previewOpen.value = true }
async function calculatePreview() {
  if (previewing.value) return
  previewing.value = true; clearPreview()
  try {
    if (!await proxy.$refs.previewRef.validate().catch(() => false)) return
    const { data } = await previewConversion({ ...previewForm.value })
    previewResult.value = data
  } catch { previewFailed.value = true }
  finally { previewing.value = false }
}
getList()
</script>
