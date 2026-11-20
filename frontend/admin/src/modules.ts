import {
  DataAnalysis, Document, Goods, House, Location, Message, Tickets, User,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

export interface AdminModule {
  key: string
  path: string
  title: string
  summary: string
  icon: Component
  entries: string[]
}

export const modules: AdminModule[] = [
  { key: 'dashboard', path: '/dashboard', title: '工作台', summary: '集中查看平台运营入口与待办。', icon: House, entries: [] },
  { key: 'members', path: '/members', title: '用户与农户', summary: '了解平台用户与农户，处理首次入驻申请。', icon: User, entries: ['用户列表', '农户资料', '入驻审核'] },
  { key: 'products', path: '/products', title: '商品与分类', summary: '管理商品展示与分类，按规则处理异常商品。', icon: Goods, entries: ['商品管理', '分类维护'] },
  { key: 'promotion', path: '/promotion', title: '产地与专题', summary: '介绍乡村产地，组织助农专题，让好产品被看见。', icon: Location, entries: ['产地介绍', '助农专题', '助农信息'] },
  { key: 'orders', path: '/orders', title: '订单与退款', summary: '查看模拟交易，处理退款复核与超时待办。', icon: Tickets, entries: ['订单查询', '退款复核', '超时接手'] },
  { key: 'reviews', path: '/reviews', title: '评价管理', summary: '管理购买评价与农户回复的公开可见状态。', icon: Message, entries: ['购买评价', '农户回复'] },
  { key: 'reports', path: '/reports', title: '推广反馈', summary: '通过浏览与咨询反馈，了解助农推广的关注情况。', icon: DataAnalysis, entries: ['浏览反馈', '咨询反馈'] },
  { key: 'audit', path: '/audit', title: '操作日志', summary: '查看重要管理操作与处理原因，保留必要记录。', icon: Document, entries: ['管理操作记录'] },
]
