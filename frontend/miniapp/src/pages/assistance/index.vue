<script setup lang="ts">
import EmptyPanel from '@/components/EmptyPanel.vue'
</script>

<template>
  <view class="page">
    <text class="eyebrow">让用心种植被看见</text><text class="page-title">一起发现乡村好物</text>
    <text class="page-description">{{ '认识一处产地，听见一位农户的故事。\n从关注开始，为乡村多一次连接。' }}</text>
    <view class="section"><view class="section-heading"><text class="section-title">助农专题</text><text class="menu-note">认识产地 · 发现特色</text></view><EmptyPanel title="新的专题正在准备" description="围绕产地和当季产品，集中展示参与农户的好物。" symbol="乡" /></view>
    <view class="section"><view class="section-heading"><text class="section-title">农户推广</text></view><EmptyPanel title="等待农户分享好消息" description="农户可介绍集中上市的产品和供货情况，用户通过商品选购或站内留言了解。" symbol="田" /></view>
    <text class="preview-note">页面预览 · 专题与农户推广内容尚未开放</text>
  </view>
</template>
