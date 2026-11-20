import { createRouter, createWebHistory } from 'vue-router'
import { modules } from '../modules'
import { pinia } from '../stores'
import { useSessionStore } from '../stores/session'
import { describeError, ensureAdminSession, safeReturnPath } from '../services/admin-session'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '管理员登录' } },
    {
      path: '/dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { title: '工作台' },
    },
    ...modules.filter((item) => item.key !== 'dashboard').map((item) => ({
      path: item.path,
      component: () => import('../views/ModuleView.vue'),
      meta: { title: item.title, moduleKey: item.key },
    })),
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const session = useSessionStore(pinia)
  if (to.name === 'login') {
    if (session.isVerifiedAdmin) return safeReturnPath(to.query.redirect)
    // A failed /me check stays on login until an explicit retry, avoiding redirect loops.
    if (!session.accessToken || session.verificationFailed) return true
    try {
      if (await ensureAdminSession()) return safeReturnPath(to.query.redirect)
    } catch (error) {
      session.showNotice(describeError(error), 'warning')
    }
    return true
  }
  try {
    if (await ensureAdminSession()) return true
  } catch (error) {
    session.showNotice(describeError(error), 'warning')
  }
  return { name: 'login', query: { redirect: safeReturnPath(to.path) }, replace: true }
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '管理工作台')} · 乡村助农平台`
})
