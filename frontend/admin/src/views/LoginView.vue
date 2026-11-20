<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElIcon, ElInput } from 'element-plus'
import { Sunny } from '@element-plus/icons-vue'
import { useSessionStore } from '../stores/session'
import { describeError, ensureAdminSession, safeReturnPath, signInAdmin, signOutAdmin } from '../services/admin-session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const hasSavedCredential = computed(() => Boolean(session.accessToken))

async function enterAdmin() {
  await router.replace(safeReturnPath(route.query.redirect))
}

async function submit() {
  if (submitting.value || hasSavedCredential.value) return
  submitting.value = true
  errorMessage.value = ''
  session.showNotice('')
  try {
    await signInAdmin(username.value, password.value)
    password.value = ''
    await enterAdmin()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    password.value = ''
    submitting.value = false
  }
}

async function retryVerification() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  session.showNotice('')
  try {
    if (await ensureAdminSession()) await enterAdmin()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    submitting.value = false
  }
}

async function switchAccount() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try { await signOutAdmin() } finally { submitting.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="login-title">
      <div class="login-brand"><span class="brand-mark" aria-hidden="true"><ElIcon :size="26"><Sunny /></ElIcon></span><span>乡村好物 · 运营中心</span></div>
      <p class="eyebrow">RURAL AGRI PLATFORM</p>
      <h1 id="login-title">管理员登录</h1>
      <p class="login-description">管理乡村特色好物，连接产地与消费者。</p>
      <ElAlert v-if="session.notice" :title="session.notice" :type="session.noticeType" :closable="false" show-icon />
      <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

      <div v-if="hasSavedCredential" class="login-retry">
        <p>已找到本机登录凭证，需要服务端确认当前管理员身份后才能进入。</p>
        <ElButton type="primary" :loading="submitting" @click="retryVerification">重新验证登录状态</ElButton>
        <ElButton text :disabled="submitting" @click="switchAccount">退出当前会话并切换账号</ElButton>
      </div>
      <form v-else class="login-form" @submit.prevent="submit">
        <div class="login-field">
          <label for="admin-username">账号</label>
          <ElInput id="admin-username" v-model="username" autocomplete="username" :maxlength="32" placeholder="请输入管理员账号" :disabled="submitting" />
        </div>
        <div class="login-field">
          <label for="admin-password">密码</label>
          <ElInput id="admin-password" v-model="password" type="password" autocomplete="current-password" :maxlength="72" placeholder="请输入密码" show-password :disabled="submitting" />
        </div>
        <ElButton type="primary" native-type="submit" :loading="submitting">登录管理端</ElButton>
      </form>
      <p class="login-tip">仅具有管理员权限的账号可进入管理端。</p>
    </section>
    <p class="login-footer">乡村特色农产品展示与助农推广平台 · 毕业设计</p>
  </main>
</template>
