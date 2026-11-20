<script setup lang="ts">
import EmptyPanel from '@/components/EmptyPanel.vue'
const browse = () => uni.switchTab({ url: '/pages/category/index' })
</script>

<template>
  <view class="page">
    <text class="eyebrow">选购多一点灵感</text><text class="page-title">你的农产品导购助手</text>
    <text class="page-description">告诉我预算、偏好和用途，一起找到适合你的乡间好物。</text>
    <view class="section card"><text class="section-title">可以这样问</text><view class="example">“想买应季水果，预算 100 元。”</view><view class="example">“这两款苹果的规格有什么区别？”</view><text class="menu-note">以上仅为提问示例，不是生成结果。</text></view>
    <view class="section"><EmptyPanel title="导购服务尚未开放" description="开放后需登录使用。当前不能发送消息，也不会产生模型调用费用。" symbol="AI"><button class="secondary-button" @click="browse">先通过分类发现好物</button></EmptyPanel></view>
  </view>
</template>

<style scoped>
.example { margin: 24rpx 0; background: #f5f7f1; padding: 22rpx; border-radius: 14rpx; font-size: 26rpx; color: #61745d; line-height: 1.7; }
</style>
