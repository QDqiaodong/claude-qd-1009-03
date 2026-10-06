import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import RoomView from '../views/RoomView.vue'
import BookingView from '../views/BookingView.vue'
import ConsumptionView from '../views/ConsumptionView.vue'
import MemberView from '../views/MemberView.vue'
import SongView from '../views/SongView.vue'
import CleaningView from '../views/CleaningView.vue'

const routes = [
  { path: '/', redirect: '/rooms' },
  { path: '/rooms', name: 'rooms', component: RoomView },
  { path: '/bookings', name: 'bookings', component: BookingView },
  { path: '/consumptions', name: 'consumptions', component: ConsumptionView },
  { path: '/members', name: 'members', component: MemberView },
  { path: '/songs', name: 'songs', component: SongView },
  { path: '/cleaning', name: 'cleaning', component: CleaningView },
  { path: '/:pathMatch(.*)*', redirect: '/rooms' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
