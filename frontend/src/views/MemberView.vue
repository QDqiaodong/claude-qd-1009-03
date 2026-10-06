<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const detail = ref(null)
const error = ref('')
const tip = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const levelFilter = ref('')
const dialog = ref(false)
const detailDrawer = ref(false)
const rechargeDialog = ref(false)
const saving = ref(false)
const recharging = ref(false)
const levels = ['普通', '银卡', '金卡', '钻石']
const rechargeAmount = ref('')
const rechargeMember = ref(null)

const form = ref(blank())
function blank() {
  return { memberNo: '', memberName: '', phone: '', level: '普通', balance: 0 }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listMembers(levelFilter.value ? { level: levelFilter.value } : {})
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, balance: Number(form.value.balance) }
    await api.createMember(d)
    tip.value = `会员 ${d.memberName} 已建档，初始储值已入流水`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function openDetail(row) {
  detailDrawer.value = true
  detailLoading.value = true
  error.value = ''
  try {
    detail.value = await api.getMemberDetail(row.id)
  } catch (e) { error.value = e.message } finally { detailLoading.value = false }
}

function openRecharge(row) {
  rechargeMember.value = row
  rechargeAmount.value = ''
  rechargeDialog.value = true
  error.value = ''
}

async function submitRecharge() {
  if (!rechargeMember.value) return
  recharging.value = true; error.value = ''
  try {
    const amount = Number(rechargeAmount.value)
    await api.rechargeMember(rechargeMember.value.id, amount)
    tip.value = `${rechargeMember.value.memberName} 储值成功，流水已生成`
    rechargeDialog.value = false
    await load()
    if (detail.value && detail.value.member.id === rechargeMember.value.id) {
      await openDetail(rechargeMember.value)
    }
  } catch (e) { error.value = e.message } finally { recharging.value = false }
}

async function changeLevel(row, level) {
  error.value = ''; tip.value = ''
  try {
    await api.updateMemberLevel(row.id, level)
    tip.value = `${row.memberName} 等级已更新为「${level}」`
    await load()
  } catch (e) { error.value = e.message }
}

function money(v) { return Number(v || 0).toFixed(2) }
function signedMoney(v) {
  const n = Number(v || 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}`
}
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">会员 · 等级、余额与资金流水</span>
        <span class="panel-desc">当前余额、已结账消费、储值/扣款流水分别核对</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">会员数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">钻石</div><div class="v">{{ rows.filter(r => r.level === '钻石').length }}</div></div>
        <div class="stat"><div class="k">金卡</div><div class="v">{{ rows.filter(r => r.level === '金卡').length }}</div></div>
        <div class="stat"><div class="k">银卡</div><div class="v">{{ rows.filter(r => r.level === '银卡').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="levelFilter" placeholder="全部等级" clearable style="width: 140px" @change="load">
          <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建会员</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="memberNo" label="会员号" width="100" />
        <el-table-column prop="memberName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="当前余额(元)" width="130">
          <template #default="{ row }"><strong>{{ money(row.balance) }}</strong></template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.level === '钻石' ? 'danger' : row.level === '金卡' ? 'warning' : 'success'">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="390" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openDetail(row)">详情/流水</el-button>
            <el-button size="small" type="success" plain @click="openRecharge(row)">储值</el-button>
            <el-button v-for="l in levels" :key="l" size="small" :disabled="row.level === l" @click="changeLevel(row, l)">{{ l }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建会员" width="560px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="会员号"><el-input v-model="form.memberNo" placeholder="如 M005" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.memberName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="初始储值(元)"><el-input v-model="form.balance" type="number" :min="0" step="1" /></el-form-item>
        <el-form-item label="等级" class="full">
          <el-radio-group v-model="form.level">
            <el-radio v-for="l in levels" :key="l" :value="l">{{ l }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rechargeDialog" title="会员储值" width="420px">
      <div v-if="rechargeMember" class="recharge-box">
        <div>会员：<strong>{{ rechargeMember.memberNo }} {{ rechargeMember.memberName }}</strong></div>
        <div>当前余额：<strong>{{ money(rechargeMember.balance) }} 元</strong></div>
        <el-input v-model="rechargeAmount" type="number" :min="0.01" step="100" placeholder="请输入储值金额" />
      </div>
      <template #footer>
        <el-button @click="rechargeDialog = false">取消</el-button>
        <el-button type="success" :loading="recharging" @click="submitRecharge">确认储值</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="会员详情与资金追溯" size="62%">
      <div v-loading="detailLoading" class="detail-wrap">
        <template v-if="detail">
          <div class="member-summary">
            <div>
              <div class="summary-name">{{ detail.member.memberNo }} {{ detail.member.memberName }}</div>
              <div>{{ detail.member.phone }} · {{ detail.member.level }}</div>
            </div>
            <div class="balance-card">
              <div class="balance-label">当前储值余额</div>
              <div class="balance-value">¥ {{ money(detail.member.balance) }}</div>
            </div>
          </div>

          <div class="panel detail-panel">
            <div class="panel-head">
              <span class="panel-title">已结账消费</span>
              <span class="panel-desc">消费单状态、金额、结账时间与扣款流水可相互追溯</span>
            </div>
            <el-empty v-if="detail.settledBookings.length === 0" description="暂无已结账消费" />
            <div v-for="b in detail.settledBookings" :key="b.bookingId" class="settled-card">
              <div class="settled-head">
                <div>
                  <strong>消费单 #{{ b.bookingId }}</strong>
                  <el-tag type="success" size="small">已完成</el-tag>
                  <el-tag size="small">{{ b.settlementMethod }}</el-tag>
                </div>
                <div>结账金额 <strong>¥ {{ money(b.settlementAmount) }}</strong></div>
              </div>
              <div class="settled-meta">{{ b.roomName }} · 结账时间：{{ b.settledAt }}</div>
              <el-table :data="b.items" border size="small">
                <el-table-column prop="itemName" label="消费项目" min-width="160" />
                <el-table-column label="金额(元)" width="120">
                  <template #default="{ row }">{{ money(row.amount) }}</template>
                </el-table-column>
                <el-table-column prop="at" label="挂单时间" min-width="160" />
              </el-table>
            </div>
          </div>

          <div class="panel detail-panel">
            <div class="panel-head">
              <span class="panel-title">储值 / 扣款流水</span>
              <span class="panel-desc">只追加，不允许直接修改；扣款流水的关联消费单必须存在</span>
            </div>
            <el-table :data="detail.transactions" border size="small">
              <el-table-column prop="id" label="流水#" width="80" />
              <el-table-column prop="createdAt" label="时间" min-width="170" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column label="发生额(元)" width="120">
                <template #default="{ row }">
                  <strong :class="Number(row.amount) < 0 ? 'amount-danger' : 'amount-plus'">{{ signedMoney(row.amount) }}</strong>
                </template>
              </el-table-column>
              <el-table-column label="流水后余额(元)" width="130">
                <template #default="{ row }">{{ money(row.balanceAfter) }}</template>
              </el-table-column>
              <el-table-column label="关联消费单" min-width="100">
                <template #default="{ row }">{{ row.bookingId ? `#${row.bookingId}` : '—' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="180" />
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
