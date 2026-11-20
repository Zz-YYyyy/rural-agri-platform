<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useSessionStore } from '@/stores/session'
import { errorMessage, signOut, verifySession } from '@/services/session'
import { ApiError } from '@/api/request'

const session = useSessionStore()
const loggingOut = ref(false)
const message = ref('')
const openAi = () => uni.navigateTo({ url: '/pages/ai/index' })
const openLogin = () => uni.navigateTo({ url: '/pages/login/index?returnTo=%2Fpages%2Fme%2Findex' })

async function refresh() {
  message.value = ''
  try { await verifySession() }
  catch (error) { message.value = errorMessage(error) }
}
onShow(() => { void refresh() })

function confirmSignOut() {
  if (loggingOut.value) return
  uni.showModal({
    title: '退出当前登录',
    content: '仅退出当前设备的登录，其他设备不受影响。',
    confirmText: '退出登录',
    success: async (result) => {
      if (!result.confirm) return
      loggingOut.value = true
      message.value = ''
      try {
        await signOut()
        uni.showToast({ title: '已退出登录', icon: 'success' })
      } catch (error) {
        message.value = error instanceof ApiError && error.status === 401
          ? '登录已失效，已清除本机登录状态。'
          : '已清除本机登录状态，但未能确认服务端会话已撤销；请稍后检查网络或服务状态。'
      } finally { loggingOut.value = false }
    },
  })
}
</script>

<template>
  <view class="page">
    <view class="profile"><view class="avatar">{{ session.account ? Array.from(session.account.nickname)[0] : '乡' }}</view><view><text class="profile-title">{{ session.account?.nickname || '欢迎来到乡间好物' }}</text><text class="page-description">{{ session.account ? `账号：${session.account.username}` : '先逛一逛，发现乡村的好味道' }}</text></view></view>
    <view v-if="session.checking" class="card"><text class="page-description">正在确认登录状态…</text></view>
    <view v-else-if="session.accessToken && !session.account" class="card"><text class="page-description">{{ session.verificationError || '登录状态尚未确认' }}</text><button class="secondary-button" @click="refresh">重新验证</button><button class="auth-switch" @click="openLogin">重新登录</button></view>
    <view v-else-if="!session.authenticated" class="card"><text class="page-description">登录后查看自己的账号资料，后续可管理订单、收藏和收货地址。</text><button class="primary-button" @click="openLogin">登录 / 注册</button></view>
    <view v-else class="role-row"><text class="role-tag">普通用户</text><text v-if="session.account?.isFarmer" class="role-tag">{{ session.account.farmerStatus === 'ACTIVE' ? '农户身份' : '农户经营已停用' }}</text><text v-if="session.account?.isAdmin" class="role-tag">管理员身份</text></view>
    <text v-if="message" class="auth-message">{{ message }}</text>
    <view class="section card">
      <view class="menu-item"><text>我的订单</text><text class="menu-note">尚未开放</text></view>
      <view class="menu-item"><text>收藏与收货地址</text><text class="menu-note">尚未开放</text></view>
      <view class="menu-item"><text>咨询消息</text><text class="menu-note">尚未开放</text></view>
      <button class="menu-item ai-link" @click="openAi"><text>AI 导购</text><text>了解一下 →</text></button>
    </view>
    <view class="section card"><text class="section-title">{{ session.account?.isFarmer ? '我的农户身份' : '我想成为农户' }}</text><text class="page-description">{{ session.account?.isFarmer ? (session.account.farmerStatus === 'ACTIVE' ? '你已拥有农户身份。商品与农户中心将在后续开放。' : '当前农户经营权限已停用，普通账号功能按平台规则使用。') : '分享自家的农产品，介绍家乡与种植故事。入驻通过后，可在这里进入农户中心。' }}</text><text class="menu-note">入驻与农户中心尚未开放</text></view>
    <button v-if="session.accessToken" class="secondary-button" :loading="loggingOut" :disabled="loggingOut || session.checking" @click="confirmSignOut">退出当前登录</button>
    <text class="preview-note">订单、收藏、消息等个人业务逐步开放</text>
  </view>
</template>

<style scoped>
.profile { display: flex; align-items: center; gap: 24rpx; margin: 16rpx 0 36rpx; }.avatar { width: 100rpx; height: 100rpx; border-radius: 32rpx; background: #e4ecd8; color: #49734b; font-size: 40rpx; text-align: center; line-height: 100rpx; flex-shrink: 0; }.profile-title { font-size: 30rpx; font-weight: 700; }.profile .page-description { font-size: 23rpx; }.ai-link { margin: 0; width: 100%; background: transparent; font-size: 28rpx; color: #205b45; line-height: 1.5; border-radius: 0; }.ai-link text:last-child { font-size: 23rpx; }
.role-row { display: flex; gap: 12rpx; flex-wrap: wrap; }.role-tag { background: #e7efdf; color: #315b41; font-size: 23rpx; padding: 10rpx 18rpx; border-radius: 14rpx; }
</style>
