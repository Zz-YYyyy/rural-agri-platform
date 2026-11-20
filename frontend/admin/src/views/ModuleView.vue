<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElEmpty, ElIcon, ElTag } from 'element-plus'
import { modules } from '../modules'

const route = useRoute()
const current = computed(() => modules.find((item) => item.key === route.meta.moduleKey))
</script>

<template>
  <template v-if="current">
    <section class="page-heading"><div><p class="eyebrow">MANAGEMENT</p><h1>{{ current.title }}</h1><p>{{ current.summary }}</p></div><span class="module-icon large"><ElIcon :size="27"><component :is="current.icon" /></ElIcon></span></section>
    <section class="module-panel">
      <div class="module-toolbar"><h2>模块功能</h2><div class="module-tags"><ElTag v-for="entry in current.entries" :key="entry" type="info" effect="plain">{{ entry }}</ElTag></div></div>
      <ElEmpty description="接口待接入" :image-size="130"><p class="empty-description">这里将展示实际业务数据。当前尚未开放查询与管理操作。</p></ElEmpty>
    </section>
  </template>
</template>
