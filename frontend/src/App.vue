<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import router from './router'
import api from './api'

import RoomView from './views/RoomView.vue'
import BookingView from './views/BookingView.vue'
import ConsumptionView from './views/ConsumptionView.vue'
import MemberView from './views/MemberView.vue'
import SongView from './views/SongView.vue'
import CleaningView from './views/CleaningView.vue'

// ------------------------------------------------------------------
// 导航形态：顶部「刻度尺滑块」。一条横贯的刻度尺上拖动两个把手选定预订时间窗，
// 另用人数滑块选定 party size；二者驱动左侧包厢筛选（容量达标者优先推荐、
// 预订视图据此预填时段）。右侧为业务模块竖排标签。
// ------------------------------------------------------------------

const viewMap = {
  '/rooms': RoomView,
  '/bookings': BookingView,
  '/consumptions': ConsumptionView,
  '/members': MemberView,
  '/songs': SongView,
  '/cleaning': CleaningView
}

const tabs = [
  { path: '/rooms', label: '包厢', sub: '房态 · 容量 · 最低消费' },
  { path: '/bookings', label: '预订', sub: '时段 · 人数 · 冲突' },
  { path: '/consumptions', label: '消费', sub: '挂单 · 储值结账' },
  { path: '/members', label: '会员', sub: '余额 · 流水' },
  { path: '/songs', label: '歌曲', sub: '语种 · 时长' },
  { path: '/cleaning', label: '清场', sub: '保洁 · 验收 · 整改' }
]

const currentPath = ref(router.currentRoute.value.path)
const counts = ref({ rooms: 0, bookings: 0, consumptions: 0, members: 0, songs: 0, cleaning: 0 })

// 刻度尺滑块状态
const partySize = ref(4)
const timeWindow = ref([19, 22]) // 11..26，代表 11:00–次日02:00
const timeMarks = {
  11: '11:00', 14: '14:00', 18: '18:00', 22: '22:00', 26: '次日02:00'
}
const filter = computed(() => ({
  partySize: partySize.value,
  startHour: timeWindow.value[0],
  endHour: timeWindow.value[1]
}))

const currentView = computed(() => viewMap[currentPath.value] || RoomView)

async function loadCounts() {
  try {
    const [r, b, c, m, s, cl] = await Promise.all([
      api.listRooms(), api.listBookings(), api.listConsumptions(),
      api.listMembers(), api.listSongs(), api.listCleaningTasks()
    ])
    counts.value = { rooms: r.length, bookings: b.length, consumptions: c.length, members: m.length, songs: s.length, cleaning: cl.length }
  } catch (e) { /* 计数失败不影响主流程 */ }
}

function go(path) {
  router.push(path)
  currentPath.value = path
}

function fmtHour(h) {
  if (h <= 24) return String(h).padStart(2, '0') + ':00'
  return '次日' + String(h - 24).padStart(2, '0') + ':00'
}

watch(() => router.currentRoute.value.path, (p) => { currentPath.value = p })
onMounted(async () => {
  currentPath.value = router.currentRoute.value.path
  await loadCounts()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">K</span>
        <div class="brand-text">
          <div class="brand-title">KTV 包厢运营 · 预订 / 消费 / 会员 / 歌曲 / 清场</div>
          <div class="brand-sub">刻度尺选时段与人数，一键筛选可订包厢</div>
        </div>
      </div>
    </header>

    <!-- 刻度尺滑块：预订时间窗 + 人数，驱动包厢筛选 -->
    <div class="ruler-bar">
      <div class="ruler-block">
        <span class="ruler-label">预订时间窗</span>
        <el-slider
          class="ruler-slider"
          range
          :min="11" :max="26" :step="1"
          v-model="timeWindow"
          :marks="timeMarks"
          show-stops
        />
        <span class="ruler-val">{{ fmtHour(timeWindow[0]) }} – {{ fmtHour(timeWindow[1]) }}</span>
      </div>
      <div class="ruler-block">
        <span class="ruler-label">人数</span>
        <el-slider
          class="ruler-slider"
          :min="1" :max="20" :step="1"
          v-model="partySize"
          show-stops
        />
        <span class="ruler-val">{{ partySize }} 人</span>
      </div>
    </div>

    <div class="body">
      <main class="main">
        <component :is="currentView" :filter="filter" />
      </main>

      <aside class="right-rail">
        <div class="rail-title">业务模块</div>
        <div
          v-for="t in tabs"
          :key="t.path"
          class="rail-tab"
          :class="{ on: currentPath === t.path }"
          @click="go(t.path)"
        >
          <span class="t-count">{{ counts[t.path.slice(1)] }}</span>
          <div class="t-label">{{ t.label }}</div>
          <div class="t-sub">{{ t.sub }}</div>
        </div>
      </aside>
    </div>
  </div>
</template>
