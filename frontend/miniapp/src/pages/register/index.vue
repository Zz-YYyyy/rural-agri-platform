<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { register } from '@/api/auth'
import { errorMessage } from '@/services/session'
import { normalizeUsername, passwordError, responseFieldErrors, safeReturnPage, usernameError } from '@/utils/auth-form'

const username = ref('')
const nickname = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const message = ref('')
const errors = ref<Record<string, string>>({})
let returnTo = '/pages/me/index'
onLoad((query) => { returnTo = safeReturnPage(query?.returnTo) })

function openLogin(registered = false) {
  const extra = registered ? `&registered=1&username=${encodeURIComponent(normalizeUsername(username.value))}` : ''
  uni.redirectTo({ url: `/pages/login/index?returnTo=${encodeURIComponent(returnTo)}${extra}` })
}

async function submit() {
  if (busy.value) return
  const cleanNickname = nickname.value.trim()
  errors.value = {
    username: usernameError(username.value),
    nickname: cleanNickname && Array.from(cleanNickname).length <= 40 ? '' : '请填写 1～40 个字符的昵称',
    password: passwordError(password.value),
    confirmation: password.value === confirmation.value ? '' : '两次输入的密码不一致',
  }
  message.value = ''
  if (Object.values(errors.value).some(Boolean)) return
  busy.value = true
  try {
    await register({ username: normalizeUsername(username.value), nickname: cleanNickname, password: password.value })
    // Registration creates an ordinary account only; do not store credentials or auto-login.
    password.value = ''
    confirmation.value = ''
    openLogin(true)
  } catch (error) {
    errors.value = { ...errors.value, ...responseFieldErrors(error) }
    message.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page auth-page">
    <text class="eyebrow">从认识乡间好物开始</text>
    <text class="page-title">创建账号</text>
    <text class="page-description">先创建普通账号。农户身份需要后续申请入驻。</text>
    <view class="section card">
      <form @submit="submit">
        <view class="auth-field"><text class="auth-label">账号</text><input v-model="username" class="auth-input" placeholder="英文字母开头，4～32 位" :maxlength="32" :disabled="busy" confirm-type="next" /><text v-if="errors.username" class="auth-field-error">{{ errors.username }}</text></view>
        <view class="auth-field"><text class="auth-label">昵称</text><input v-model="nickname" class="auth-input" placeholder="希望大家怎样称呼你" :maxlength="40" :disabled="busy" confirm-type="next" /><text v-if="errors.nickname" class="auth-field-error">{{ errors.nickname }}</text></view>
        <view class="auth-field"><text class="auth-label">密码</text><input v-model="password" class="auth-input" placeholder="至少 8 个字符" password :maxlength="72" :disabled="busy" confirm-type="next" /><text v-if="errors.password" class="auth-field-error">{{ errors.password }}</text></view>
        <view class="auth-field"><text class="auth-label">确认密码</text><input v-model="confirmation" class="auth-input" placeholder="再次输入密码" password :maxlength="72" :disabled="busy" confirm-type="done" @confirm="submit" /><text v-if="errors.confirmation" class="auth-field-error">{{ errors.confirmation }}</text></view>
        <text v-if="message" class="auth-message">{{ message }}</text>
        <button class="primary-button" form-type="submit" :loading="busy" :disabled="busy">{{ busy ? '正在创建…' : '注册账号' }}</button>
      </form>
    </view>
    <button class="auth-switch" :disabled="busy" @click="openLogin(false)">已有账号？去登录</button>
    <text class="auth-footnote">账号会自动去掉首尾空格并转为小写。密码保留原样，请牢记。</text>
  </view>
</template>
