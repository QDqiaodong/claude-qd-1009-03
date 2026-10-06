<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const props = defineProps({ filter: Object })

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const statusFilter = ref('')
const dialog = ref(false)
const saving = ref(false)
const statuses = ['空闲', '预订', '使用中', '待清场', '停用']

// 交接记录（清场闭环追溯）
const handoverDialog = ref(false)
const handoverRoom = ref(null)
const handoverTasks = ref([])
const handoverLoading = ref(false)

const form = ref(blank())
function blank() {
  return { roomCode: '', roomName: '', capacity: '', minSpend: '', status: '空闲' }
}

// 推荐 = 容量达标 且 当前可接待（空闲）。待清场/停用/使用中/预订一律不推荐 ——
// 未验收的包厢不得出现在可订推荐里（闭环取舍：未验收不得对外显示可用）。
const recommendedSet = computed(() => {
  const ps = (props.filter && props.filter.partySize) || 1
  return new Set(rows.value.filter(r => r.capacity >= ps && r.status === '空闲').map(r => r.id))
})

const filteredRows = computed(() =>
  statusFilter.value ? rows.value.filter(r => r.status === statusFilter.value) : rows.value
)

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listRoomsWithBookings()
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = {
      ...form.value,
      capacity: Number(form.value.capacity),
      minSpend: Number(form.value.minSpend)
    }
    await api.createRoom(d)
    tip.value = `包厢 ${d.roomName} 已建档`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function changeStatus(row, target) {
  error.value = ''; tip.value = ''
  try {
    await api.updateRoomStatus(row.id, target)
    tip.value = `${row.roomName} 已置为「${target}」`
    await load()
  } catch (e) { error.value = e.message }
}

// 结束使用：进入清场闭环（生成待清场任务），验收放行前不可接待
async function finish(row) {
  error.value = ''; tip.value = ''
  try {
    await api.finishRoom(row.id)
    tip.value = `${row.roomName} 已结束使用并生成待清场任务，验收放行前不可接待`
    await load()
  } catch (e) { error.value = e.message }
}

// 从包厢详情追溯整条清场交接记录
async function openHandover(row) {
  handoverRoom.value = row
  handoverDialog.value = true
  handoverLoading.value = true
  try {
    handoverTasks.value = await api.listCleaningTasks({ roomId: row.id }) || []
  } catch (e) { error.value = e.message } finally { handoverLoading.value = false }
}

function fmt(s) { return s ? String(s).replace('T', ' ').slice(0, 16) : '—' }
function taskTagType(s) {
  return {
    待领取: 'warning', 清洁中: 'primary', 待验收: 'warning',
    整改中: 'danger', 已放行: 'success', 已停用: 'info'
  }[s] || 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">包厢 · 房态与容量</span>
        <span class="panel-desc">刻度尺选定 {{ filter?.partySize || 1 }} 人；待清场/停用不可订，验收放行后自动回空闲</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">包厢数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">空闲</div><div class="v">{{ rows.filter(r => r.status === '空闲').length }}</div></div>
        <div class="stat"><div class="k">预订</div><div class="v">{{ rows.filter(r => r.status === '预订').length }}</div></div>
        <div class="stat"><div class="k">使用中</div><div class="v">{{ rows.filter(r => r.status === '使用中').length }}</div></div>
        <div class="stat"><div class="k">待清场</div><div class="v">{{ rows.filter(r => r.status === '待清场').length }}</div></div>
        <div class="stat"><div class="k">停用</div><div class="v">{{ rows.filter(r => r.status === '停用').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建包厢</el-button>
      </div>

      <el-table :data="filteredRows" v-loading="loading" border stripe>
        <el-table-column prop="roomCode" label="编号" width="90" />
        <el-table-column prop="roomName" label="名称" min-width="110" />
        <el-table-column prop="capacity" label="容量(人)" width="90" />
        <el-table-column prop="minSpend" label="最低消费(元)" width="120" />
        <el-table-column label="预订数" width="90">
          <template #default="{ row }">{{ (row.bookings || []).length }}</template>
        </el-table-column>
        <el-table-column label="推荐" width="80">
          <template #default="{ row }">
            <el-tag v-if="recommendedSet.has(row.id)" type="success">推荐</el-tag>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="{ 空闲: 'success', 预订: 'warning', 使用中: 'danger', 待清场: 'primary', 停用: 'info' }[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '使用中'" size="small" type="danger" @click="finish(row)">结束使用</el-button>
            <el-button size="small" :disabled="!['预订', '停用'].includes(row.status)" @click="changeStatus(row, '空闲')">置空闲</el-button>
            <el-button size="small" type="warning" plain :disabled="!['空闲', '预订'].includes(row.status)" @click="changeStatus(row, '使用中')">置使用中</el-button>
            <el-button size="small" plain @click="openHandover(row)">交接记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建包厢" width="560px">
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="包厢编号"><el-input v-model="form.roomCode" placeholder="如 K07" /></el-form-item>
        <el-form-item label="包厢名称"><el-input v-model="form.roomName" placeholder="如 中包" /></el-form-item>
        <el-form-item label="容量(人)"><el-input v-model="form.capacity" type="number" :min="1" /></el-form-item>
        <el-form-item label="最低消费(元)"><el-input v-model="form.minSpend" type="number" :min="0" step="1" /></el-form-item>
        <el-form-item label="状态" class="full">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in statuses.filter(x => x !== '待清场')" :key="s" :value="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>

    <!-- 包厢清场交接记录追溯 -->
    <el-dialog v-model="handoverDialog" :title="`交接记录 · ${handoverRoom?.roomCode} ${handoverRoom?.roomName}`" width="680px">
      <div v-loading="handoverLoading">
        <div v-for="t in handoverTasks" :key="t.id" class="handover-task">
          <div class="handover-head">
            <el-tag :type="taskTagType(t.status)" size="small">#{{ t.id }} {{ t.status }}</el-tag>
            <span class="handover-meta">
              生成 {{ fmt(t.createdAt) }}<template v-if="t.cleanerName"> · 保洁 {{ t.cleanerName }}</template><template v-if="t.managerName"> · 经理 {{ t.managerName }}</template><template v-if="t.round"> · 第 {{ t.round }} 轮</template>
            </span>
          </div>
          <el-timeline style="padding-left:4px;margin-top:10px">
            <el-timeline-item v-for="r in (t.records || [])" :key="r.id" :timestamp="fmt(r.createdAt)"
              :type="r.action === '验收放行' ? 'success' : ['退回整改', '停用'].includes(r.action) ? 'danger' : 'primary'">
              <div><el-tag size="small" style="margin-right:6px">{{ r.action }}</el-tag><b>{{ r.operatorName }}</b></div>
              <div style="color:#666;margin-top:4px">{{ r.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
        <el-empty v-if="!handoverLoading && !handoverTasks.length" description="暂无清场交接记录" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.handover-task { border: 1px solid var(--line); border-radius: 10px; padding: 10px 14px; margin-bottom: 12px; }
.handover-head { display: flex; align-items: center; gap: 10px; }
.handover-meta { font-size: 12px; color: var(--muted); }
</style>
