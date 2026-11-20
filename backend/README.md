# 共享业务后端

本目录是供 uni-app 小程序、H5 和网页管理端共用的 Spring Boot 后端。2026-11-20 已实现基础工程和第一阶段账号接口：注册、登录、当前账号、退出登录；商品、订单、助农与 AI 接口后续按模块实现。

固定 Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、springdoc 2.10.0；Spring AI 1.1.8 仅导入依赖版本管理，没有模型客户端或收费调用。MySQL 驱动、Redis、Flyway、Spring Security 和校验组件由 Boot BOM 管理，最终版本可用 `mvn dependency:tree` 核对。

```text
pom.xml                           Maven 依赖与构建配置
.mvn/jvm.config                   Maven 的 UTF-8 与 UTC 设置
src/main/java/                    启动类、配置、公共 HTTP 基础与 auth 账号模块
src/main/resources/               应用资源与配置
src/main/resources/db/migration/  版本化数据库结构迁移
src/main/resources/db/seed/       单独维护的演示数据脚本
src/test/java/                    后端测试
```

从本目录运行：

```powershell
mvn test
mvn package
mvn spring-boot:run
```

默认 `bootstrap` 配置无需 MySQL、Redis 或密钥，监听 `127.0.0.1:8080`，可访问 `GET /actuator/health` 和 `/swagger-ui/index.html`。健康检查是独立运维端点，使用 Actuator 的 `{"status":"UP"}`，不套业务包络；只表示基础 HTTP 进程可用。运行时 Swagger 只列实际 Controller，不复制设计中的 137 项业务操作冒充实现。

`bootstrap` 仍不开放业务接口，不连接数据库与 Redis。`dev` 只开放已经实现的四个账号接口，其他业务接口继续拒绝访问；不会创建默认管理员或假 JWT。普通账号可自行注册，注册不自动登录。密码使用 BCrypt；登录签发两小时 HS256 JWT，并先将独立会话登记到 Redis。受保护请求从数据库读取当前身份与停用状态，退出只撤销当前会话。`bootstrap` 与 `dev` 可看运行时接口页面，其他配置默认关闭。

`dev` 配置显式读取 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 及 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`，不在源码保存真实连接信息。还必须提供 `JWT_SECRET_BASE64`（至少32个安全随机字节的 Base64）；缺失或过短会阻止 `dev` 启动。`JWT_ISSUER`、`JWT_AUDIENCE` 可配置，`PUBLIC_BASE_URL` 用于生成公开默认头像地址。只有明确启用 `DB_MIGRATE_ENABLED=true` 才运行 Flyway；先准备指定的隔离 MySQL 8.0 数据库并阅读 [迁移说明](../docs/database/数据库实现与迁移说明.md)。Flyway 清库禁用、不会自动 baseline 已有数据库，演示数据未接入启动流程。完整运行与本地服务配置以 [本地开发与运行](../docs/development/本地开发与运行.md) 为准。

端口和监听地址可由 `SERVER_PORT`、`SERVER_ADDRESS` 调整。打包后使用 `java -Dfile.encoding=UTF-8 -Duser.timezone=UTC -jar target/rural-agri-backend-0.1.0-SNAPSHOT.jar` 启动；中文路径依靠 Java／Maven 的实际构建验证，不要求修改中文文档文件名。

账号注册和登录采用 Redis 原子短时限流，超限返回 `429 RATE_LIMITED` 和 `Retry-After`。配置默认值为注册每 IP 每60秒5次、登录每 IP 每60秒30次且每规范化账号每60秒10次；对应 `AUTH_REGISTER_PER_MINUTE`、`AUTH_LOGIN_IP_PER_MINUTE`、`AUTH_LOGIN_ACCOUNT_PER_MINUTE`。不直接信任客户端 `X-Forwarded-For`，部署代理时需单独设计可信代理配置。这些是账号防刷参数，与 AI 每日额度无关。

数据库设计见 [字段字典](../docs/database/数据库字段字典.md)，已有 29 表 `V1__initial_schema.sql` 保留原样。Maven 测试使用真实 MVC、安全链、BCrypt 和 JWT，但模拟数据库及 Redis 边界；真实服务迁移与联调情况见 [账号与基础设施验证记录](../docs/testing/账号与基础设施验证记录.md)，不能用模拟依赖测试代替 MySQL 8.0 验证。

正常主流程接口已形成，入口为 [接口文档](../docs/api/接口文档.md)，逐字段定义见 [接口字段说明](../docs/api/接口字段说明.md)，工具读取使用 [OpenAPI 契约](../docs/api/接口契约.openapi.json)。源定义在 `docs/api/分模块契约/`，修改后从仓库根目录运行 `python scripts/build_api_contract.py --write`，再运行不带 `--write` 的检查。少量未决核心权限分支按 `x-pending-policy` 处理，不能自动开放。

当前检查不代表商品、交易、AI 或微信端完整联调已经通过。版本依据：[Spring AI 兼容关系](https://github.com/spring-projects/spring-ai#readme)、[MyBatis-Plus Boot3 接入](https://baomidou.com/getting-started/)、[springdoc 兼容矩阵](https://springdoc.org/v2/)。
