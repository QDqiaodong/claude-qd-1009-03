<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import router from '../router'
import api, { SONG_LANGUAGES, SONG_LANGUAGE_ALL } from '../api'

// “全部”与具体语种同属合法筛选状态；下拉只接受这些值，不再产生空白口径
const languages = SONG_LANGUAGES
const ALL = SONG_LANGUAGE_ALL

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const langFilter = ref(ALL)
const dialog = ref(false)
const saving = ref(false)

const route = router.currentRoute

const form = ref(blank())
function blank() {
  return { songCode: '', songName: '', singer: '', language: '国语', durationSec: '' }
}

// 竞态守卫：每次发起请求自增序号，只有最新一次请求允许落地；
// 同时用 AbortController 取消旧请求，慢返回的旧结果即使到达也会被双重拦截。
let requestSeq = 0
let activeController = null

async function load() {
  const seq = ++requestSeq

  activeController?.abort()
  const controller = new AbortController()
  activeController = controller

  loading.value = true
  error.value = ''
  const filterAtRequest = langFilter.value
  try {
    // 全部 → 不传 language；具体语种 → 透传。非法值在 api 层就会被拒绝
    const languageArg = filterAtRequest === ALL ? undefined : filterAtRequest
    const data = await api.listSongs(languageArg, { signal: controller.signal })
    // 旧请求（已被更新的筛选取代）不得覆盖最新结果
    if (seq !== requestSeq) return
    rows.value = data || []
  } catch (e) {
    if (e?.cancelled || seq !== requestSeq) return
    // 失败时清掉过期列表，避免表格残留与当前筛选不符的旧歌曲；
    // 错误信息与“查无结果”的空表区分开，排查时可直接判断是参数/服务错误
    rows.value = []
    error.value = e.message
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function onFilterChange(value) {
  // el-select 只绑定合法选项，这里再做一次归一化兜底
  const next = languages.includes(value) ? value : ALL
  if (next !== langFilter.value) langFilter.value = next
  // 筛选状态写入 URL query：刷新页面、前进后退与当前筛选保持同一语义
  const query = { ...route.value.query }
  if (next === ALL) delete query.lang
  else query.lang = next
  router.replace({ query })
  load()
}

function readFilterFromRoute() {
  const raw = route.value.query.lang
  if (raw === undefined || raw === '') return { value: ALL, bad: null }
  if (languages.includes(raw)) return { value: raw, bad: null }
  // URL 上是非法语种：这是明确的参数错误，而不是“全部”或空结果；
  // 归一化回全部以便用户继续操作，但保留错误提示说明问题
  return { value: ALL, bad: String(raw) }
}

watch(() => route.value.query.lang, (raw, old) => {
  // 组件内部切换由 onFilterChange 负责加载；这里只处理前进/后退、直接改 URL 等外部变化
  if (raw === old) return
  const { value, bad } = readFilterFromRoute()
  if (bad) {
    const badValue = bad
    // 非法语种是明确的参数错误：修正 URL 为“全部”并保留错误提示
    const query = { ...route.value.query }
    delete query.lang
    router.replace({ query })
    if (value === langFilter.value) {
      error.value = `歌曲语种参数不合法：${badValue}；合法值：${languages.join('/')}`
      return
    }
    langFilter.value = ALL
    error.value = `歌曲语种参数不合法：${badValue}，已恢复为全部；合法值：${languages.join('/')}`
    load()
    return
  }
  // 与当前筛选一致说明是 onFilterChange 自己写入的 query，加载已发起，不重复请求
  if (value === langFilter.value) return
  langFilter.value = value
  load()
})

function fmtDur(s) {
  if (s == null) return '—'
  const m = Math.floor(s / 60)
  const ss = String(s % 60).padStart(2, '0')
  return `${m}:${ss}`
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, durationSec: form.value.durationSec ? Number(form.value.durationSec) : null }
    await api.createSong(d)
    tip.value = `歌曲 ${d.songName} 已入库`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

onMounted(() => {
  const { value, bad } = readFilterFromRoute()
  langFilter.value = value
  load()
  if (bad) {
    // load() 会先清 error，故在发起加载后再补挂参数错误提示
    const badValue = bad
    error.value = `歌曲语种参数不合法：${badValue}，已恢复为全部；合法值：${languages.join('/')}`
    const query = { ...route.value.query }
    delete query.lang
    router.replace({ query })
  }
})

onUnmounted(() => {
  // 离开页面时取消在途请求，防止响应回到已销毁组件
  activeController?.abort()
  requestSeq++
})
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">歌曲库 · 语种与时长</span>
        <span class="panel-desc">编码唯一，语种须为 国语/粤语/英文/日文</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <el-select
          v-model="langFilter"
          style="width: 140px"
          :loading="loading"
          @change="onFilterChange"
        >
          <el-option :label="ALL + '语种'" :value="ALL" />
          <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">入库歌曲</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe :empty-text="loading ? '加载中…' : '当前筛选下暂无歌曲'">
        <el-table-column prop="songCode" label="编码" width="90" />
        <el-table-column prop="songName" label="歌名" min-width="140" />
        <el-table-column prop="singer" label="歌手" width="130" />
        <el-table-column prop="language" label="语种" width="90">
          <template #default="{ row }"><el-tag>{{ row.language }}</el-tag></template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ fmtDur(row.durationSec) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="入库歌曲" width="560px">
      <el-form :model="form" label-width="90px" class="dialog-form">
        <el-form-item label="编码"><el-input v-model="form.songCode" placeholder="如 S006" /></el-form-item>
        <el-form-item label="歌名"><el-input v-model="form.songName" /></el-form-item>
        <el-form-item label="歌手"><el-input v-model="form.singer" /></el-form-item>
        <el-form-item label="语种">
          <el-select v-model="form.language">
            <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(秒)"><el-input v-model="form.durationSec" type="number" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>
