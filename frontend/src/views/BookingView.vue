<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const props = defineProps({ filter: Object })

const rows = ref([])
const rooms = ref([])
const members = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const statuses = ['已预订', '已完成', '已取消']

const form = ref(blank())
function blank() {
  return { roomId: '', memberId: '', startAt: '', endAt: '', partySize: props.filter?.partySize || 2 }
}

function toLocalISO(date, hour) {
  const d = new Date(date)
  d.setHours(hour, 0, 0, 0)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const [b, r, m] = await Promise.all([
      api.listBookings(), api.listRooms(), api.listMembers()
    ])
    rows.value = b || []
    rooms.value = r || []
    members.value = m || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function roomName(id) {
  const r = rooms.value.find(x => x.id === id)
  return r ? `${r.roomCode} ${r.roomName}` : id
}
function memberName(id) {
  const m = members.value.find(x => x.id === id)
  return m ? `${m.memberNo} ${m.memberName}` : id
}

function openCreate() {
  const f = blank()
  if (props.filter) {
    // 用刻度尺时间窗预填预订起止时间（今天）
    const today = new Date()
    f.startAt = toLocalISO(today, props.filter.startHour)
    f.endAt = toLocalISO(today, props.filter.endHour)
    f.partySize = props.filter.partySize
  }
  form.value = f
  dialog.value = true; tip.value = ''; error.value = ''
}

async function save() {
  saving.value = true; error.value = ''
  try {
    const d = {
      roomId: Number(form.value.roomId),
      memberId: Number(form.value.memberId),
      startAt: form.value.startAt,
      endAt: form.value.endAt,
      partySize: Number(form.value.partySize)
    }
    await api.createBooking(d)
    tip.value = `预订成功（${roomName(d.roomId)}）`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function cancel(row) {
  error.value = ''; tip.value = ''
  try {
    await api.cancelBooking(row.id)
    tip.value = `预订 ${row.id} 已取消`
    await load()
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">预订 · 时段与人数</span>
        <span class="panel-desc">人数不超容量、时段落在营业窗口、同包厢不重叠</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建预订</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="包厢" min-width="120">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column label="会员" min-width="120">
          <template #default="{ row }">{{ memberName(row.memberId) }}</template>
        </el-table-column>
        <el-table-column prop="partySize" label="人数" width="70" />
        <el-table-column prop="startAt" label="开始" min-width="150" />
        <el-table-column prop="endAt" label="结束" min-width="150" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '已预订' ? 'warning' : row.status === '已完成' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain :disabled="row.status === '已取消' || row.status === '已完成'" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建预订" width="600px">
      <el-form :model="form" label-width="100px" class="dialog-form">
        <el-form-item label="包厢">
          <el-select v-model="form.roomId" filterable placeholder="选择包厢">
            <el-option v-for="r in rooms" :key="r.id"
              :label="`${r.roomCode} ${r.roomName}（${r.capacity}人）${['待清场', '停用'].includes(r.status) ? ' · ' + r.status + '不可订' : ''}`"
              :value="r.id" :disabled="['待清场', '停用'].includes(r.status)" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员">
          <el-select v-model="form.memberId" filterable placeholder="选择会员">
            <el-option v-for="m in members" :key="m.id" :label="`${m.memberNo} ${m.memberName}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="人数" class="full"><el-input v-model="form.partySize" type="number" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">预订</el-button>
      </template>
    </el-dialog>
  </div>
</template>
