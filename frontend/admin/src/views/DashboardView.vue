<script setup lang="ts">
import { ElButton, ElIcon, ElTag } from 'element-plus'
import { ArrowRight, Compass, Connection, Opportunity } from '@element-plus/icons-vue'
import { RouterLink } from 'vue-router'
import { modules } from '../modules'

const priorities = modules.filter((item) => ['members', 'promotion', 'orders'].includes(item.key))
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">OVERVIEW</p><h1>管理工作台</h1><p>从产地到餐桌，让每一份乡村好物被看见。</p></div><ElTag effect="plain" type="info">基础工程</ElTag></section>
  <section class="welcome-card">
    <div class="welcome-copy">
      <span class="welcome-label"><ElIcon><Compass /></ElIcon> 助农推广 · 从这里开始</span>
      <h2>连接农户与消费者，<br />让乡村好物走得更远。</h2>
      <p>组织助农专题、讲好产地故事，<br class="desktop-break" />为农户提供展示与交流的空间。</p>
      <RouterLink to="/promotion"><ElButton type="primary" size="large">查看推广模块 <ElIcon class="button-icon"><ArrowRight /></ElIcon></ElButton></RouterLink>
    </div>
    <div class="landscape" aria-hidden="true"><span class="sun" /><span class="hill hill-back" /><span class="hill hill-front" /><span class="field field-one" /><span class="field field-two" /><span class="field field-three" /><span class="seed seed-one" /><span class="seed seed-two" /><span class="landscape-caption">来自土地的美好</span></div>
  </section>

  <section class="workspace-section">
    <div class="section-heading"><h2>常用管理入口</h2><span>围绕主要业务开展管理</span></div>
    <div class="priority-grid">
      <RouterLink v-for="item in priorities" :key="item.key" :to="item.path" class="priority-card">
        <div class="priority-top"><span class="module-icon"><ElIcon :size="22"><component :is="item.icon" /></ElIcon></span><ElIcon class="card-arrow"><ArrowRight /></ElIcon></div>
        <h3>{{ item.title }}</h3><p>{{ item.summary }}</p><span class="card-link">进入模块</span>
      </RouterLink>
    </div>
  </section>

  <div class="dashboard-bottom">
    <section class="info-panel"><div class="section-heading"><h2>推广反馈</h2><ElIcon><Connection /></ElIcon></div><div class="empty-metrics"><span class="metrics-symbol">—</span><h3>还没有接入统计数据</h3><p>后续展示实际记录的浏览与咨询反馈，<br />模拟订单不计为真实助农收入。</p><RouterLink to="/reports">查看反馈模块 <span aria-hidden="true">→</span></RouterLink></div></section>
    <section class="info-panel"><div class="section-heading"><h2>管理范围</h2><ElIcon><Opportunity /></ElIcon></div><ol class="scope-list"><li><span>01</span><div><h3>首次入驻审核</h3><p>只审核农户首次入驻，商品和资料通过校验后直接生效。</p></div></li><li><span>02</span><div><h3>专题与产地运营</h3><p>管理助农专题与产地介绍，符合条件的商品自动加入专题。</p></div></li><li><span>03</span><div><h3>必要管理与售后</h3><p>按已确认规则处理商品、评价和退款争议，保留操作记录。</p></div></li></ol></section>
  </div>
</template>
