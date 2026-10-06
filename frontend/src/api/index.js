import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    // 主动取消的旧请求：不包装成业务错误，交给调用方的竞态守卫忽略
    if (axios.isCancel?.(error) || error?.code === 'ERR_CANCELED') {
      error.cancelled = true
      return Promise.reject(error)
    }
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o })

// 合法语种与显式“全部”口径，与后端 Dict 保持一致
const SONG_LANGUAGES = ['国语', '粤语', '英文', '日文']
const SONG_LANGUAGE_ALL = '全部'

/**
 * 归一化歌曲语种筛选参数。
 * - 空值（undefined/null/''）与“全部” → 不带 language 参数，后端返回全库；
 * - 合法语种 → 原样透传；
 * - 空白串或非法语种 → 直接抛错，绝不把模糊/非法参数发到后端，
 *   任何调用方（视图、计数、第三方脚本）都受同一口径约束。
 */
function normalizeSongLanguage(language) {
  if (language === undefined || language === null || language === '') return {}
  if (typeof language !== 'string') {
    throw new Error(`歌曲语种参数类型非法：${String(language)}`)
  }
  const value = language.trim()
  if (!value) {
    throw new Error('歌曲语种不能为空：请选择合法语种或“全部”')
  }
  if (value === SONG_LANGUAGE_ALL) return {}
  if (!SONG_LANGUAGES.includes(value)) {
    throw new Error(`歌曲语种不合法：${language}，应为 ${SONG_LANGUAGES.join('/')} 或 ${SONG_LANGUAGE_ALL}`)
  }
  return { language: value }
}

export { SONG_LANGUAGES, SONG_LANGUAGE_ALL }

export default {
  // ---------------- 包厢 ----------------
  listRooms: (p) => call(http.get('/rooms', params(p))),
  listRoomsWithBookings: () => call(http.get('/rooms/with-bookings')),
  createRoom: (d) => call(http.post('/rooms', d)),
  updateRoomStatus: (id, target) =>
    call(http.put(`/rooms/${id}/status`, null, params({ target }))),
  finishRoom: (id, operator) =>
    call(http.post(`/rooms/${id}/finish`, null, params({ operator }))),

  // ---------------- 预订 ----------------
  listBookings: (p) => call(http.get('/bookings', params(p))),
  createBooking: (d) => call(http.post('/bookings', d)),
  cancelBooking: (id) => call(http.post(`/bookings/${id}/cancel`)),

  // ---------------- 消费 ----------------
  listConsumptions: (p) => call(http.get('/consumptions', params(p))),
  createConsumption: (d) => call(http.post('/consumptions', d)),
  settleBooking: (bookingId, paymentMethod) =>
    call(http.post('/consumptions/settle', { paymentMethod }, { params: { bookingId } })),

  // ---------------- 会员 ----------------
  listMembers: (p) => call(http.get('/members', params(p))),
  getMemberDetail: (id) => call(http.get(`/members/${id}/detail`)),
  createMember: (d) => call(http.post('/members', d)),
  rechargeMember: (id, amount) =>
    call(http.post(`/members/${id}/recharge`, null, params({ amount }))),
  updateMemberLevel: (id, level) =>
    call(http.put(`/members/${id}/level`, null, params({ level }))),

  // ---------------- 歌曲 ----------------
  // language 经统一归一化：非法/空白直接在客户端报错，合法语种透传，空值/全部 → 全库
  listSongs: (language, config = {}) =>
    call(http.get('/songs', { params: normalizeSongLanguage(language), ...config })),
  createSong: (d) => call(http.post('/songs', d)),

  // ---------------- 清场交接与验收放行 ----------------
  listCleaningTasks: (p) => call(http.get('/cleaning-tasks', params(p))),
  getCleaningTask: (id) => call(http.get(`/cleaning-tasks/${id}`)),
  claimCleaningTask: (id, operator) =>
    call(http.post(`/cleaning-tasks/${id}/claim`, null, params({ operator }))),
  submitCleaningTask: (id, d) => call(http.post(`/cleaning-tasks/${id}/submit`, d)),
  acceptCleaningTask: (id, manager, note) =>
    call(http.post(`/cleaning-tasks/${id}/accept`, null, params({ manager, note }))),
  returnCleaningTask: (id, manager, note) =>
    call(http.post(`/cleaning-tasks/${id}/return`, null, params({ manager, note }))),
  resolveCleaningIssue: (id, operator, note) =>
    call(http.post(`/cleaning-tasks/${id}/resolve-issue`, null, params({ operator, note }))),
  decommissionCleaningTask: (id, manager, conclusion) =>
    call(http.post(`/cleaning-tasks/${id}/decommission`, null, params({ manager, conclusion })))
}
