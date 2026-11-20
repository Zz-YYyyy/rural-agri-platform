<script setup lang="ts">
import EmptyPanel from '@/components/EmptyPanel.vue'
const browse = () => uni.switchTab({ url: '/pages/category/index' })
</script>

<template>
  <view class="page">
    <text class="eyebrow">把喜欢的好物放在一起</text><text class="page-title">购物车</text>
    <view class="section"><EmptyPanel title="购物服务尚未开放" description="开放后，登录即可保存选中的商品，并按农户分别结算。" symbol="选"><button class="secondary-button" @click="browse">先逛逛分类</button></EmptyPanel></view>
    <text class="preview-note">本项目的支付、退款与物流均为模拟演示</text>
  </view>
</template>
