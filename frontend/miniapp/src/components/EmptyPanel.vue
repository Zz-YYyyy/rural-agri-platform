<script setup lang="ts">
defineProps<{ title: string; description: string; symbol?: string }>()
</script>

<template>
  <view class="empty-panel">
    <view class="empty-symbol">{{ symbol || '禾' }}</view>
    <text class="empty-title">{{ title }}</text>
    <text class="empty-description">{{ description }}</text>
    <slot />
  </view>
</template>

<style scoped>
.empty-panel { text-align: center; border: 1rpx solid #e3e9df; border-radius: 24rpx; padding: 52rpx 36rpx; background: #fff; }
.empty-symbol { width: 88rpx; height: 88rpx; border-radius: 28rpx; background: #f0f4e9; color: #6c8555; font-size: 40rpx; line-height: 88rpx; margin: 0 auto 24rpx; }
.empty-title { display: block; font-size: 30rpx; font-weight: 600; }
.empty-description { display: block; margin-top: 14rpx; color: #859080; font-size: 25rpx; line-height: 1.9; }
</style>
