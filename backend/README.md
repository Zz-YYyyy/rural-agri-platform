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

数据库设计见 `../docs/database/`，接口契约见 `../docs/api/`。现阶段尚未创建 Maven 工程、运行配置、表结构或迁移内容。
