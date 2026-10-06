<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const bookings = ref([])
const members = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const settleId = ref('')
const paymentMethod = ref('前台现付')
const settling = ref(false)

const form = ref(blank())
function blank() { return { bookingId: '', itemName: '', amount: '' } }

const activeBookings = computed(() =>
  bookings.value.filter(x => x.status !== '已完成' && x.status !== '已取消')
)
const settledBookings = computed(() =>
  bookings.value.filter(x => x.status === '已完成')
)
const selectedBooking = computed(() => bookings.value.find(x => x.id === Number(settleId.value)))
const selectedMember = computed(() =>
  selectedBooking.value ? members.value.find(x => x.id === selectedBooking.value.memberId) : null)
const selectedTotal = computed(() => totalOf(Number(settleId.value)))
const balanceEnough = computed(() =>
  !selectedMember.value || selectedMember.value.balance >= selectedTotal.value)

async function load() {
  loading.value = true; error.value = ''
  try {
    const [c, b, m] = await Promise.all([
      api.listConsumptions(), api.listBookings(), api.listMembers()
    ])
    rows.value = c || []
    bookings.value = b || []
    members.value = m || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function bookingLabel(id) {
  const b = bookings.value.find(x => x.id === id)
  if (!b) return `预订#${id}`
  const member = members.value.find(x => x.id === b.memberId)
  return `预订#${b.id}（${member ? member.memberName : '会员#' + b.memberId}，${b.partySize}人，${b.status}）`
}

function memberOfBooking(booking) {
  return members.value.find(x => x.id === booking.memberId)
}

function totalOf(bookingId) {
  return rows.value
    .filter(x => x.bookingId === bookingId)
    .reduce((sum, x) => sum + Number(x.amount || 0), 0)
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    await api.createConsumption({
      bookingId: Number(form.value.bookingId),
      itemName: form.value.itemName,
      amount: Number(form.value.amount)
    })
    tip.value = `已记消费：${form.value.itemName}`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function settle() {
  if (!selectedBooking.value) return
  error.value = ''; tip.value = ''
  settling.value = true
  try {
    const result = await api.settleBooking(selectedBooking.value.id, paymentMethod.value)
    const balanceText = result.memberBalanceAfter !== null && result.memberBalanceAfter !== undefined
      ? `；流水#${result.transactionId}，扣款后余额 ${money(result.memberBalanceAfter)} 元`
      : ''
    tip.value = `预订#${result.bookingId} 已标记为「${result.bookingStatus}」，${result.paymentMethod} ${money(result.totalAmount)} 元${balanceText}`
    settleId.value = ''
    await load()
  } catch (e) { error.value = e.message } finally { settling.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">消费 · 挂单与结账</span>
        <span class="panel-desc">未结账先挂明细；结账时余额、消费单、资金流水同事务提交</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">记一笔消费</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="预订" min-width="240">
          <template #default="{ row }">{{ bookingLabel(row.bookingId) }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" min-width="120" />
        <el-table-column label="金额(元)" width="110">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="at" label="挂单时间" min-width="170" />
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="记一笔消费" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="预订">
          <el-select v-model="form.bookingId" filterable placeholder="选择未结账预订">
            <el-option v-for="b in activeBookings" :key="b.id" :label="bookingLabel(b.id)" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.itemName" placeholder="如 啤酒 / 果盘 / 套餐" /></el-form-item>
        <el-form-item label="金额(元)"><el-input v-model="form.amount" type="number" :min="0" step="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">记录</el-button>
      </template>
    </el-dialog>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">结账开单</span>
        <span class="panel-desc">只有选择「储值余额」才校验并扣减会员余额；余额不足时消费单保持未结账</span>
      </div>
      <div class="toolbar">
        <el-select v-model="settleId" placeholder="选择未结账消费单" style="width: 300px">
          <el-option v-for="b in activeBookings" :key="b.id" :label="bookingLabel(b.id)" :value="b.id" />
        </el-select>
        <el-radio-group v-model="paymentMethod">
          <el-radio value="前台现付">前台现付</el-radio>
          <el-radio value="储值余额">储值余额</el-radio>
        </el-radio-group>
        <el-button
          type="success"
          :loading="settling"
          :disabled="!selectedBooking || (paymentMethod === '储值余额' && !balanceEnough)"
          @click="settle"
        >结账开单</el-button>
      </div>
      <div v-if="selectedBooking" class="settle-check">
        <div>会员：<strong>{{ selectedMember ? selectedMember.memberName : `会员#${selectedBooking.memberId}` }}</strong></div>
        <div>当前挂单合计：<strong>{{ money(selectedTotal) }} 元</strong></div>
        <div v-if="paymentMethod === '储值余额'">
          储值余额：<strong :class="{ 'amount-danger': !balanceEnough }">{{ selectedMember ? money(selectedMember.balance) : '0.00' }} 元</strong>
          <el-tag v-if="!balanceEnough" type="danger" size="small">余额不足，不能提交储值扣款</el-tag>
        </div>
        <div v-else class="muted">前台现付不读取、不扣减储值余额，也不生成会员扣款流水。</div>
      </div>

      <el-table :data="settledBookings" border stripe class="settled-table">
        <el-table-column prop="id" label="消费单" width="90">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="会员" min-width="130">
          <template #default="{ row }">{{ memberOfBooking(row)?.memberName || `会员#${row.memberId}` }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag type="success">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="settlementMethod" label="结算方式" width="110" />
        <el-table-column label="结账金额(元)" width="120">
          <template #default="{ row }">{{ money(row.settlementAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settledAt" label="结账时间" min-width="170" />
      </el-table>
    </div>
  </div>
</template>
