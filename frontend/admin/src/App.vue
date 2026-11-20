<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { ElButton, ElIcon, ElTag } from 'element-plus'
import { ArrowRight, Fold, Menu as MenuIcon, Sunny } from '@element-plus/icons-vue'
import { modules } from './modules'
import { useSessionStore } from './stores/session'
import { safeReturnPath, signOutAdmin } from './services/admin-session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const menuOpen = ref(false)
const signingOut = ref(false)
const pageTitle = computed(() => String(route.meta.title || '管理工作台'))

async function signOut() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await signOutAdmin()
    menuOpen.value = false
    await router.replace({ name: 'login' })
  } finally {
    signingOut.value = false
  }
}

watch(() => session.accessToken, (token) => {
  if (!token && route.name !== 'login' && route.matched.length && !signingOut.value) {
    void router.replace({ name: 'login', query: { redirect: safeReturnPath(route.path) } })
  }
})
</script>

<template>
  <RouterView v-if="route.name === 'login'" />
  <div v-else-if="session.isVerifiedAdmin" class="admin-shell">
    <button v-if="menuOpen" class="menu-backdrop" aria-label="关闭导航" @click="menuOpen = false" />
    <aside class="sidebar" :class="{ 'is-open': menuOpen }">
      <RouterLink to="/dashboard" class="brand" @click="menuOpen = false">
        <span class="brand-mark" aria-hidden="true"><ElIcon :size="24"><Sunny /></ElIcon></span>
        <span><strong>乡村好物</strong><small>特色农产品 · 助农平台</small></span>
      </RouterLink>
      <p class="nav-caption">管理与运营</p>
      <nav aria-label="管理模块">
        <RouterLink
          v-for="item in modules" :key="item.key" :to="item.path"
          class="nav-link" active-class="is-active" @click="menuOpen = false"
        >
          <ElIcon :size="19"><component :is="item.icon" /></ElIcon>
          <span>{{ item.title }}</span>
          <ElIcon v-if="route.path === item.path" class="nav-arrow"><ArrowRight /></ElIcon>
        </RouterLink>
      </nav>
      <div class="sidebar-note">
        <span class="note-dot" /> 毕业设计 · 模拟演示
        <p>连接乡村好物，传递产地故事。</p>
      </div>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          <ElButton class="mobile-toggle" text aria-label="切换导航" @click="menuOpen = !menuOpen">
            <ElIcon :size="20"><Fold v-if="menuOpen" /><MenuIcon v-else /></ElIcon>
          </ElButton>
          <span class="breadcrumb-root">运营中心</span>
          <span class="breadcrumb-divider">/</span>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="header-status">
          <span class="current-account"><span class="status-dot" />{{ session.user?.nickname || session.user?.username }}</span>
          <ElButton text :loading="signingOut" @click="signOut">退出登录</ElButton>
        </div>
      </header>
      <main id="main-content" class="main-content">
        <div class="preview-notice"><ElTag size="small" effect="plain">业务接口待接入</ElTag><span>管理员身份已验证，管理模块当前展示页面结构，业务数据将逐步接入。</span></div>
        <RouterView />
      </main>
      <footer class="page-footer">乡村特色农产品展示与助农推广平台 <span>支付、退款与物流均为模拟演示</span></footer>
    </div>
  </div>
  <main v-else class="session-check" role="status">正在验证管理员身份，请稍候…</main>
</template>
