import { createRouter } from 'vue-router'
import EditView from '../views/EditView.vue'
import { createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'edit',
      component: EditView,
    },
    {
      path: '/launch',
      name: 'launch',
      component: () => import('../views/LaunchView.vue'),
    },
    {
      path: '/watch',
      name: 'watch',
      component: () => import('../views/WatchView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
  ],
})

export default router
