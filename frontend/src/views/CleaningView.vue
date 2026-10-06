<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

// ------------------------------------------------------------------
// 清场交接与验收放行闭环：
//   保洁待办（待领取/清洁中/整改中） → 提交卫生结果/遗留物/设备异常
//   经理待验收（待验收） → 验收放行 / 退回整改 / 异常处置 / 停用结论
//   整改积压（整改中） → 跟踪返工往返
// 取舍：任务未闭环的包厢一律不对外显示可用（房态保持「待清场」）。
// ------------------------------------------------------------------

const tasks = ref([])
const rooms = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const acting = ref(false)

// 操作人（无登录体系，姓名本地记忆；负责人/状态/历史均以服务端为准，刷新一致）
const cleaner = ref(localStorage.getItem('cleanerName') || '')
const manager = ref(localStorage.getItem('managerName') || '')

const dialog = ref('') // submit / accept / return / resolve / decommission / history
const current = ref(null)
const saving = ref(false)
const submitForm = ref({ hygieneResult: '合格', leftoverItems: '', equipmentIssue: '' })
const noteForm = ref({ note: '', operator: '' })

const cleanerTodo = computed(() => tasks.value.filter(t => ['待领取', '清洁中', '整改中'].includes(t.status)))
const managerTodo = computed(() => tasks.value.filter(t => t.status === '待验收'))
const reworkBacklog = computed(() => tasks.value.filter(t => t.status === '整改中'))

function roomLabel(id) {
  const r = rooms.value.find(x => x.id === id)
  return r ? `${r.roomCode} ${r.roomName}` : `包厢#${id}`
}
function fmt(s) { return s ? String(s).replace('T', ' ').slice(0, 16) : '—' }
function orNone(s) { return s ? s : '无' }
function tagType(s) {
  return {
    待领取: 'warning', 清洁中: 'primary', 待验收: 'warning',
    整改中: 'danger', 已放行: 'success', 已停用: 'info'
  }[s] || 'info'
}
function issueOpen(t) { return t.hasEquipmentIssue && !t.issueResolved }

async function load() {
  loading.value = true; error.value = ''
  try {
    const [ts, rs] = await Promise.all([api.listCleaningTasks(), api.listRooms()])
    tasks.value = ts || []
    rooms.value = rs || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function rememberNames() {
  localStorage.setItem('cleanerName', cleaner.value.trim())
  localStorage.setItem('managerName', manager.value.trim())
}

async function run(action, okTip) {
  acting.value = true; error.value = ''; tip.value = ''
  try {
    await action()
    tip.value = okTip
    await load()
  } catch (e) {
    error.value = e.message
    await load() // 失败也刷新：并发场景下让别人已生效的结果立刻可见
  } finally { acting.value = false }
}

// ---------------- 保洁动作 ----------------
async function claim(row) {
  if (!cleaner.value.trim()) { error.value = '请先在上方填写保洁员姓名再领取'; return }
  rememberNames()
  await run(() => api.claimCleaningTask(row.id, cleaner.value.trim()),
    `任务 #${row.id} 已领取（${cleaner.value.trim()}），开始清场`)
}

function openSubmit(row) {
  if (!cleaner.value.trim()) { error.value = '请先在上方填写保洁员姓名再提交'; return }
  current.value = row
  submitForm.value = { hygieneResult: '合格', leftoverItems: '', equipmentIssue: '' }
  dialog.value = 'submit'; error.value = ''
}

async function doSubmit() {
  saving.value = true; error.value = ''
  try {
    rememberNames()
    await api.submitCleaningTask(current.value.id, {
      cleanerName: cleaner.value.trim(),
      hygieneResult: submitForm.value.hygieneResult,
      leftoverItems: submitForm.value.leftoverItems,
      equipmentIssue: submitForm.value.equipmentIssue
    })
    tip.value = `任务 #${current.value.id} 清场结果已提交，等待经理验收`
    dialog.value = ''
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

// ---------------- 经理动作 ----------------
function needManager() {
  if (!manager.value.trim()) { error.value = '请先在上方填写经理姓名再操作'; return false }
  rememberNames()
  return true
}

function openAccept(row) {
  if (issueOpen(row)) { error.value = `任务 #${row.id} 存在未处置的设备异常，请先「异常处置」或作「停用结论」`; return }
  if (!needManager()) return
  current.value = row; noteForm.value = { note: '', operator: '' }
  dialog.value = 'accept'; error.value = ''
}
async function doAccept() {
  saving.value = true; error.value = ''
  try {
    await api.acceptCleaningTask(current.value.id, manager.value.trim(), noteForm.value.note)
    tip.value = `任务 #${current.value.id} 验收放行，${roomLabel(current.value.roomId)} 已恢复空闲可接待`
    dialog.value = ''
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

function openReturn(row) {
  if (!needManager()) return
  current.value = row; noteForm.value = { note: '', operator: '' }
  dialog.value = 'return'; error.value = ''
}
async function doReturn() {
  if (!noteForm.value.note.trim()) { error.value = '退回整改必须填写整改要求'; return }
  saving.value = true; error.value = ''
  try {
    await api.returnCleaningTask(current.value.id, manager.value.trim(), noteForm.value.note.trim())
    tip.value = `任务 #${current.value.id} 已退回整改（整改要求与前轮问题已留痕）`
    dialog.value = ''
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

function openResolve(row) {
  if (!needManager()) return
  current.value = row
  noteForm.value = { note: '', operator: manager.value.trim() }
  dialog.value = 'resolve'; error.value = ''
}
async function doResolve() {
  if (!noteForm.value.note.trim()) { error.value = '异常处置必须填写处理结果'; return }
  saving.value = true; error.value = ''
  try {
    await api.resolveCleaningIssue(current.value.id, noteForm.value.operator.trim(), noteForm.value.note.trim())
    tip.value = `任务 #${current.value.id} 设备异常已处置，可继续验收`
    dialog.value = ''
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

function openDecommission(row) {
  if (!needManager()) return
  current.value = row; noteForm.value = { note: '', operator: '' }
  dialog.value = 'decommission'; error.value = ''
}
async function doDecommission() {
  if (!noteForm.value.note.trim()) { error.value = '停用必须留下明确结论（原因与后续安排）'; return }
  saving.value = true; error.value = ''
  try {
    await api.decommissionCleaningTask(current.value.id, manager.value.trim(), noteForm.value.note.trim())
    tip.value = `任务 #${current.value.id} 已作停用结论，${roomLabel(current.value.roomId)} 转为停用`
    dialog.value = ''
    await load()
  } catch (e) { error.value = e.message; await load() } finally { saving.value = false }
}

function openHistory(row) { current.value = row; dialog.value = 'history'; error.value = '' }

onMounted(load)
</script>

<template>
  <div>
    <!-- 操作人 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">清场 · 交接与验收放行</span>
        <span class="panel-desc">任务未闭环的包厢保持「待清场」，不对外显示可用；验收放行是唯一出口</span>
      </div>
      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>
      <div class="toolbar">
        <el-input v-model="cleaner" placeholder="保洁员姓名（领取/提交用）" style="width: 220px" @change="rememberNames" />
        <el-input v-model="manager" placeholder="经理姓名（验收/退回/停用用）" style="width: 240px" @change="rememberNames" />
        <span class="spacer"></span>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <div class="stat-row">
        <div class="stat"><div class="k">待领取</div><div class="v">{{ tasks.filter(t => t.status === '待领取').length }}</div></div>
        <div class="stat"><div class="k">清洁中</div><div class="v">{{ tasks.filter(t => t.status === '清洁中').length }}</div></div>
        <div class="stat"><div class="k">待验收</div><div class="v">{{ managerTodo.length }}</div></div>
        <div class="stat"><div class="k">整改积压</div><div class="v">{{ reworkBacklog.length }}</div></div>
      </div>
    </div>

    <!-- 保洁待办 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">保洁待办</span>
        <span class="panel-desc">领取 → 清场 → 提交卫生结果 / 遗留物 / 设备异常；被退回的任务带整改要求</span>
      </div>
      <el-table :data="cleanerTodo" v-loading="loading" border stripe>
        <el-table-column prop="id" label="任务" width="64" />
        <el-table-column label="包厢" min-width="120">
          <template #default="{ row }">{{ roomLabel(row.roomId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="负责人" width="90">
          <template #default="{ row }">{{ row.cleanerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="round" label="轮次" width="64" />
        <el-table-column label="整改要求" min-width="180">
          <template #default="{ row }">
            <span v-if="row.status === '整改中'" style="color:#c0392b">{{ row.reworkNote }}</span>
            <span v-else style="color:#bbb">—</span>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="140">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === '待领取'" size="small" type="primary" :disabled="acting" @click="claim(row)">领取</el-button>
            <el-button v-else size="small" type="primary" :disabled="acting" @click="openSubmit(row)">
              {{ row.status === '整改中' ? '提交整改结果' : '提交清场结果' }}
            </el-button>
            <el-button size="small" @click="openHistory(row)">历史</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 经理待验收 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">经理待验收</span>
        <span class="panel-desc">只能验收放行或退回整改；设备异常未处置的任务禁止放行</span>
      </div>
      <el-table :data="managerTodo" v-loading="loading" border stripe>
        <el-table-column prop="id" label="任务" width="64" />
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="保洁" width="80" />
        <el-table-column label="卫生" width="80">
          <template #default="{ row }">
            <el-tag :type="row.hygieneResult === '合格' ? 'success' : 'danger'">{{ row.hygieneResult || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="遗留物" min-width="130">
          <template #default="{ row }">{{ orNone(row.leftoverItems) }}</template>
        </el-table-column>
        <el-table-column label="设备异常" min-width="170">
          <template #default="{ row }">
            <template v-if="row.hasEquipmentIssue">
              <el-tag type="danger">{{ row.equipmentIssue }}</el-tag>
              <el-tag :type="row.issueResolved ? 'success' : 'warning'" size="small" style="margin-left:4px">
                {{ row.issueResolved ? '已处置' : '未处置' }}
              </el-tag>
            </template>
            <span v-else style="color:#bbb">无</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="140">
          <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-tooltip :disabled="!issueOpen(row)" content="设备异常未处置，须先异常处置或作停用结论" placement="top">
              <span>
                <el-button size="small" type="success" :disabled="acting || issueOpen(row)" @click="openAccept(row)">验收放行</el-button>
              </span>
            </el-tooltip>
            <el-button size="small" type="warning" plain :disabled="acting" @click="openReturn(row)">退回整改</el-button>
            <el-button v-if="issueOpen(row)" size="small" type="primary" plain :disabled="acting" @click="openResolve(row)">异常处置</el-button>
            <el-button size="small" type="danger" plain :disabled="acting" @click="openDecommission(row)">停用</el-button>
            <el-button size="small" @click="openHistory(row)">历史</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 整改积压 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">整改积压</span>
        <span class="panel-desc">被经理退回的任务：整改要求与前轮问题已留痕，保洁返工后从「保洁待办」重新提交</span>
      </div>
      <el-table :data="reworkBacklog" v-loading="loading" border stripe>
        <el-table-column prop="id" label="任务" width="64" />
        <el-table-column label="包厢" min-width="110">
          <template #default="{ row }">{{ roomLabel(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="cleanerName" label="负责人" width="90" />
        <el-table-column prop="round" label="往返轮次" width="90" />
        <el-table-column prop="reworkNote" label="整改要求" min-width="220" />
        <el-table-column label="上次提交" width="140">
          <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openHistory(row)">历史</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 提交清场结果 -->
    <el-dialog :model-value="dialog === 'submit'" title="提交清场结果" width="560px" @update:model-value="dialog = ''">
      <el-alert v-if="current && current.status === '整改中'" type="error" :closable="false" style="margin-bottom:12px"
        :title="`整改要求：${current.reworkNote}`" description="前轮问题与整改要求已在交接流水中留痕，整改完成后请如实填写本轮结果。" />
      <el-form :model="submitForm" label-width="100px" class="dialog-form">
        <el-form-item label="卫生结果" class="full">
          <el-radio-group v-model="submitForm.hygieneResult">
            <el-radio value="合格">合格</el-radio>
            <el-radio value="不合格">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="遗留物" class="full">
          <el-input v-model="submitForm.leftoverItems" placeholder="无遗留物可留空；有则写明物品与去向" />
        </el-form-item>
        <el-form-item label="设备异常" class="full">
          <el-input v-model="submitForm.equipmentIssue" placeholder="麦克风 / 点歌屏等异常，无异常留空；上报后须处置完毕才能放行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = ''">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSubmit">提交（{{ cleaner.trim() || '未填姓名' }}）</el-button>
      </template>
    </el-dialog>

    <!-- 验收放行 -->
    <el-dialog :model-value="dialog === 'accept'" title="验收放行" width="520px" @update:model-value="dialog = ''">
      <el-alert type="success" :closable="false" style="margin-bottom:12px"
        title="放行后任务闭环，包厢立即恢复「空闲」可接待下一场。" />
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="验收意见" class="full">
          <el-input v-model="noteForm.note" placeholder="可留空，默认「验收合格，包厢恢复空闲可接待」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = ''">取消</el-button>
        <el-button type="success" :loading="saving" @click="doAccept">确认放行</el-button>
      </template>
    </el-dialog>

    <!-- 退回整改 -->
    <el-dialog :model-value="dialog === 'return'" title="退回整改" width="520px" @update:model-value="dialog = ''">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
        title="退回后任务回到保洁待办；前一轮问题与本次整改要求都会保留在交接流水中。" />
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="整改要求" class="full">
          <el-input v-model="noteForm.note" type="textarea" :rows="3" placeholder="必填：写明哪里不合格、需要怎么返工" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = ''">取消</el-button>
        <el-button type="warning" :loading="saving" @click="doReturn">退回整改</el-button>
      </template>
    </el-dialog>

    <!-- 异常处置 -->
    <el-dialog :model-value="dialog === 'resolve'" title="设备异常处置" width="520px" @update:model-value="dialog = ''">
      <el-alert v-if="current" type="error" :closable="false" style="margin-bottom:12px"
        :title="`异常内容：${current.equipmentIssue}`" description="登记处置结果后才允许验收放行；无法处置时请改用「停用」。" />
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="处置人"><el-input v-model="noteForm.operator" placeholder="处置人姓名" /></el-form-item>
        <el-form-item label="处理结果" class="full">
          <el-input v-model="noteForm.note" type="textarea" :rows="3" placeholder="必填：如 已更换 2 号麦克风并试音正常 / 工程部已检修点歌屏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = ''">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doResolve">登记处置</el-button>
      </template>
    </el-dialog>

    <!-- 停用结论 -->
    <el-dialog :model-value="dialog === 'decommission'" title="停用结论" width="520px" @update:model-value="dialog = ''">
      <el-alert type="error" :closable="false" style="margin-bottom:12px"
        title="停用后任务闭环、包厢转为「停用」，不可预订；维修完成后在包厢页恢复空闲。" />
      <el-form label-width="100px" class="dialog-form">
        <el-form-item label="停用结论" class="full">
          <el-input v-model="noteForm.note" type="textarea" :rows="3" placeholder="必填：停用原因与后续安排，如 点歌屏主板故障待换件，维修后恢复" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = ''">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doDecommission">确认停用</el-button>
      </template>
    </el-dialog>

    <!-- 交接流水 -->
    <el-dialog :model-value="dialog === 'history'" :title="`交接记录 · 任务 #${current?.id}（${current ? roomLabel(current.roomId) : ''}）`" width="620px" @update:model-value="dialog = ''">
      <el-timeline v-if="current && (current.records || []).length" style="padding-left:4px">
        <el-timeline-item v-for="r in current.records" :key="r.id" :timestamp="fmt(r.createdAt)"
          :type="r.action === '验收放行' ? 'success' : r.action === '退回整改' || r.action === '停用' ? 'danger' : 'primary'">
          <div><el-tag size="small" style="margin-right:6px">{{ r.action }}</el-tag><b>{{ r.operatorName }}</b></div>
          <div style="color:#666;margin-top:4px">{{ r.detail }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无交接记录" />
    </el-dialog>
  </div>
</template>
