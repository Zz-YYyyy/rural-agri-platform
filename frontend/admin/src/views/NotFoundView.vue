<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ElButton, ElEmpty } from 'element-plus'
</script>

<template><section class="module-panel"><ElEmpty description="页面不存在"><RouterLink to="/dashboard"><ElButton type="primary">返回工作台</ElButton></RouterLink></ElEmpty></section></template>
