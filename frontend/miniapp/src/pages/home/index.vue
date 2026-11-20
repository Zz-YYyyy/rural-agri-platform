<script setup lang="ts">
import EmptyPanel from '@/components/EmptyPanel.vue'
const openCategory = () => uni.switchTab({ url: '/pages/category/index' })
const openAssistance = () => uni.switchTab({ url: '/pages/assistance/index' })
const openAi = () => uni.navigateTo({ url: '/pages/ai/index' })
</script>

<template>
  <view class="page">
    <view class="brand-row"><text class="brand">乡间好物</text><text class="brand-note">看见乡土 · 连接你我</text></view>
    <button class="search" @click="openCategory"><text>⌕</text><text>找一份来自乡间的好味道</text></button>
    <view class="hero">
      <text class="hero-label">把乡间的故事，带到身边</text>
      <text class="hero-title">{{ '一方水土，\n一份好物。' }}</text>
      <text class="hero-copy">{{ '认识产地，走近农户\n让每一份用心种植被看见。' }}</text>
      <button class="hero-button" @click="openAssistance">逛逛助农专区 <text>→</text></button>
      <view class="field field-one" /><view class="field field-two" /><view class="sun" />
    </view>
    <button class="ai-card" @click="openAi">
      <view class="ai-mark">AI</view>
      <view class="ai-content"><text class="ai-title">不知道选什么？问问 AI 导购</text><text class="ai-copy">按预算和喜好，发现适合你的农产品</text></view>
      <text class="arrow">↗</text>
    </button>
    <view class="section">
      <view class="section-heading"><text class="section-title">当季好物</text><button class="text-button" @click="openCategory">查看分类 →</button></view>
      <EmptyPanel title="乡间好物，即将见面" description="这里将展示农户的特色产品、产地与规格信息。当前选购尚未开放。" />
    </view>
    <text class="preview-note">商品与购物服务逐步开放，可在“我的”登录账号</text>
  </view>
</template>

<style scoped>
.brand-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28rpx; gap: 12rpx; }
.brand { font-size: 36rpx; font-weight: 800; letter-spacing: 3rpx; }.brand-note { font-size: 22rpx; color: #8a927f; }
.search { display: flex; align-items: center; gap: 16rpx; background: #fff; border: 1rpx solid #e3e9df; border-radius: 18rpx; color: #959d90; padding: 0 26rpx; margin: 0 0 28rpx; font-size: 25rpx; line-height: 84rpx; text-align: left; }
.hero { position: relative; overflow: hidden; padding: 40rpx 36rpx; background: #e5edd6; border-radius: 28rpx; min-height: 390rpx; }
.hero-label, .hero-title, .hero-copy, .hero-button { position: relative; z-index: 2; }.hero-label { color: #667a4c; font-size: 22rpx; letter-spacing: 2rpx; display: block; }
.hero-title { display: block; font-size: 54rpx; font-weight: 800; line-height: 1.35; margin-top: 20rpx; }.hero-copy { display: block; margin-top: 20rpx; font-size: 25rpx; line-height: 1.8; color: #63805c; }
.hero-title, .hero-copy { white-space: pre-line; }
.hero-button { display: inline-flex; align-items: center; gap: 24rpx; margin: 26rpx 0 0; background: #205b45; color: #fff; padding: 0 24rpx; border-radius: 12rpx; font-size: 24rpx; line-height: 72rpx; }
.field { position: absolute; width: 400rpx; height: 220rpx; border-radius: 50%; transform: rotate(-30deg); }.field-one { background: #b4c79a; right: -220rpx; bottom: -50rpx; }.field-two { background: #d0dcb9; right: -170rpx; bottom: 45rpx; }.sun { position: absolute; right: 54rpx; top: 62rpx; border-radius: 50%; background: #f4d48a; width: 76rpx; height: 76rpx; }
.ai-card { display: flex; align-items: center; gap: 20rpx; margin: 28rpx 0 0; padding: 28rpx 24rpx; border-radius: 22rpx; background: #fff; border: 1rpx solid #e3e9df; text-align: left; line-height: 1.5; }
.ai-mark { background: #eaf2e9; color: #205b45; border-radius: 16rpx; font-size: 25rpx; font-weight: 800; width: 70rpx; height: 70rpx; line-height: 70rpx; text-align: center; flex-shrink: 0; }.ai-content { flex: 1; }.ai-title { display: block; font-size: 27rpx; font-weight: 600; color: #2e513b; }.ai-copy { display: block; font-size: 22rpx; color: #8b9688; margin-top: 6rpx; }.arrow { font-size: 32rpx; color: #456d49; }
</style>
