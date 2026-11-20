# 共享业务后端

本目录用于初始化 Spring Boot 项目，为 uni-app 小程序、H5 和网页管理端提供统一业务接口。

基础方向为 Spring Boot、MySQL 8.0、Redis 与 Spring AI。数据库访问、鉴权、参数校验和测试组件按后续技术方案固定版本。

```text
src/main/java/                    Java 业务代码
src/main/resources/               应用资源与配置
src/main/resources/db/migration/  版本化数据库结构迁移
src/main/resources/db/seed/       单独维护的演示数据脚本
src/test/java/                    后端测试
```

截至 2026-11-20，数据库设计见 [字段字典](../docs/database/数据库字段字典.md) 与 [迁移说明](../docs/database/数据库实现与迁移说明.md)。已提供 29 表的 `V1__initial_schema.sql`，但尚无 Maven 工程、运行配置或 Flyway 集成；隔离 MySQL 8.0 迁移验证尚未执行。

正常主流程接口已形成，入口为 [接口文档](../docs/api/接口文档.md)，逐字段定义见 [接口字段说明](../docs/api/接口字段说明.md)，工具读取使用 [OpenAPI 契约](../docs/api/接口契约.openapi.json)。源定义在 `docs/api/分模块契约/`，修改后从仓库根目录运行 `python scripts/build_api_contract.py --write`，再运行不带 `--write` 的检查。少量未决核心权限分支按 `x-pending-policy` 处理，不能自动开放。

下一步确认框架兼容版本并初始化基础工程，再接入配置、鉴权、数据库迁移与首批业务接口。现有契约和 SQL 的静态检查不代表已经提供 HTTP 服务、完成真实迁移或通过业务联调。
