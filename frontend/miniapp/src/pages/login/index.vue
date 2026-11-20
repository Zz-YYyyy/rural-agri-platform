<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useSessionStore } from '@/stores/session'
import { errorMessage, signIn, verifySession } from '@/services/session'
import { goAfterLogin, normalizeUsername, passwordError, responseFieldErrors, safeReturnPage, usernameError } from '@/utils/auth-form'

const session = useSessionStore()
const username = ref('')
const password = ref('')
const passwordVisible = ref(false)
const busy = ref(false)
const message = ref('')
const registered = ref(false)
const errors = ref<Record<string, string>>({})
let returnTo = '/pages/me/index'

onLoad((query) => {
  returnTo = safeReturnPage(query?.returnTo)
  registered.value = query?.registered === '1'
  if (typeof query?.username === 'string') username.value = normalizeUsername(query.username)
})

async function submit() {
  if (busy.value) return
  errors.value = { username: usernameError(username.value), password: passwordError(password.value) }
  message.value = ''
  if (Object.values(errors.value).some(Boolean)) return
  busy.value = true
  try {
    if (await signIn({ username: normalizeUsername(username.value), password: password.value })) {
      password.value = ''
      goAfterLogin(returnTo)
    }
  } catch (error) {
    errors.value = { ...errors.value, ...responseFieldErrors(error) }
    message.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

async function retryVerification() {
  if (busy.value || session.checking) return
  busy.value = true
  message.value = ''
  try {
    if (await verifySession()) {
      password.value = ''
      goAfterLogin(returnTo)
    }
  } catch (error) { message.value = errorMessage(error) }
  finally { busy.value = false }
}

function openRegister() {
  uni.redirectTo({ url: `/pages/register/index?returnTo=${encodeURIComponent(returnTo)}` })
}
</script>

<template>
  <view class="page auth-page">
    <text class="eyebrow">欢迎回到乡间好物</text>
    <text class="page-title">账号登录</text>
    <text class="page-description">登录后查看自己的账号资料。你也可以随时返回首页浏览。</text>
    <view v-if="registered" class="auth-success">注册成功，请使用刚设置的账号和密码登录。</view>
    <view class="section card">
      <form @submit="submit">
        <view class="auth-field"><text class="auth-label">账号</text><input v-model="username" class="auth-input" placeholder="请输入账号" :maxlength="32" :disabled="busy" confirm-type="next" /><text v-if="errors.username" class="auth-field-error">{{ errors.username }}</text></view>
        <view class="auth-field"><text class="auth-label">密码</text><view class="password-row"><input v-model="password" class="auth-input" placeholder="请输入密码" :password="!passwordVisible" :maxlength="72" :disabled="busy" confirm-type="done" @confirm="submit" /><button class="password-toggle" :disabled="busy" @click="passwordVisible = !passwordVisible">{{ passwordVisible ? '隐藏' : '显示' }}</button></view><text v-if="errors.password" class="auth-field-error">{{ errors.password }}</text></view>
        <text v-if="message" class="auth-message">{{ message }}</text>
        <button class="primary-button" form-type="submit" :loading="busy" :disabled="busy">{{ busy ? '正在验证…' : '登录' }}</button>
      </form>
      <button v-if="session.accessToken && session.verificationError" class="secondary-button" :disabled="busy || session.checking" @click="retryVerification">重新验证已有登录</button>
    </view>
    <button class="auth-switch" :disabled="busy" @click="openRegister">还没有账号？注册一个</button>
    <text class="auth-footnote">密码只用于本次登录，不会保存在本机。当前使用账号密码登录。</text>
  </view>
</template>
