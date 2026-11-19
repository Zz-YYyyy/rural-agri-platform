# 乡村特色农产品展示与助农推广平台

毕业设计题目：基于小程序的乡村特色农产品展示与助农推广平台设计与实现。

本仓库用于管理前端、后端、设计文档和部署脚本。当前处于需求确认与目录准备阶段，应用工程尚未初始化。

## 仓库目录

```text
frontend/
  miniapp/     uni-app 项目，面向微信小程序和 H5，包含用户端与农户中心
  admin/       Vue 3 网页管理端
backend/       Spring Boot 后端，供两个前端共用
docs/
  文档导航.md      文档阅读入口
  requirements/  需求确认稿与后续需求说明
  architecture/  技术方向、系统边界与工程设计
  api/           接口契约与接口说明
  database/      ER 图、数据字典与数据库设计
  testing/       验收场景与后续验证记录
  项目决策记录.md   已确认决策及待确认事项
  项目变更记录.md   需求与设计变更记录
deploy/        部署配置与运行维护说明
scripts/       开发、检查和维护辅助脚本
```

数据库迁移脚本统一放在 `backend/src/main/resources/db/migration/`，演示数据脚本放在 `backend/src/main/resources/db/seed/`。

## 技术与交付方向

- 小程序与 H5：uni-app、Vue 3；用户与农户共用应用。
- 管理端：Vue 3；首版使用统一管理员角色。
- 后端与数据：Spring Boot、MySQL 8.0、Redis。
- AI：Spring AI、商品查询工具、多轮对话、推荐商品卡片校验；RAG 为后续扩展候选。
- 工程配套：版本化数据库迁移、OpenAPI 接口契约、关键业务测试、Docker Compose 与 Nginx 部署。
- 必须支持微信开发者工具运行演示，同时提供可部署的 H5 和网页管理端。

框架具体版本、模型服务商和部署资源将在对应设计阶段确认。

## 从哪里开始

先阅读 [功能模块与主要页面总览](docs/requirements/功能模块与主要页面总览.md) 和 [文档索引](docs/文档导航.md)，了解模块、角色入口及主流程，再按模块查看 [需求确认稿](docs/requirements/需求文档.md)、[决策记录](docs/项目决策记录.md)、[待确认问题](docs/requirements/待确认问题.md)、[页面讨论稿](docs/requirements/页面与导航说明.md)、[技术方向](docs/architecture/技术架构与工程设计.md) 与 [验收场景](docs/testing/验收场景清单.md)。

文档需要让未参与讨论的人或 AI 独立理解，重要规则通过编号关联流程、例子、接口和数据设计。当前已详细记录下单预占、取消及退款返还、30 分钟未支付关闭等确认结果；未决规则和页面方案仍明确标注，尚未形成完整开发规格。

涉及业务行为、接口或数据库结构的变更，需要同步更新相关文档和迁移脚本，具体约定见 [AGENTS.md](AGENTS.md)。

远程仓库：[Zz-YYyyy/rural-agri-platform](https://github.com/Zz-YYyyy/rural-agri-platform)。
