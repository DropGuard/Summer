# Summer Framework — 全量代码审计

> 生成于 2026-07-29，重构完成后。本文件归档审计结论，重构完成即删除。
> 不纳入版本控制（.gitignore）。

---

## 1. 模块拓扑

```
summer-framework (reactor POM)
├── summer-parent/                          [build 契约，不在 reactor]
│   └── summer-build-parent/                [外部项目继承入口]
│
├── 框架核心 (framework-core)
│   ├── summer-core          DI/IoC, 配置绑定, SPI 接口, 异常体系
│   ├── summer-aop           拦截器链, @InterceptorBinding
│   ├── summer-tx            @Transactional, TransactionManager
│   ├── summer-web           HTTP 抽象 (Controller, Router, Body 解析)
│   ├── summer-web-http      HTTP Router 实现 (RadixTrie, HashMap)
│   ├── summer-web-netty     Netty HTTP Server, WebSocket
│   ├── summer-web-middleware CORS, Logging, Metrics
│   ├── summer-web-websocket WebSocket Router
│   ├── summer-boot          启动器 SummerApplication
│   ├── summer-runtime       运行时引擎 (Proxy, DI, Route 注册)
│   ├── summer-aot-engine    AOT 代码生成 ($$Context, WireMethod)
│   ├── summer-data-jdbc     JdbcTemplate, @RowModel, QueryBuilder
│   ├── summer-data-redis    RedisTemplate, Lettuce
│   └── summer-grpc          gRPC Server/Client
│
├── 启动器 (starters)
│   ├── summer-boot-starter           核心启动器
│   ├── summer-boot-starter-web       Web (HTTP + Netty)
│   ├── summer-boot-starter-data-jdbc JDBC 持久化
│   ├── summer-boot-starter-data-redis Redis
│   ├── summer-boot-starter-websocket  WebSocket
│   └── summer-boot-starter-grpc      gRPC
│
├── 测试套件 (framework-test)
│   ├── summer-test                   @SummerTest, TestContainer, TestResource
│   ├── summer-tck                    双引擎 TCK (DI, AOP, Config, Web)
│   ├── summer-tck-fixtures           TCK 共享 Fixture
│   ├── summer-tck-negative-fixtures  负向测试 Fixture
│   └── summer-archunit               Architecture Test 规则
│
├── 基础设施 (infrastructure)
│   ├── summer-dependencies           BOM (版本目录)
│   ├── summer-maven-plugin           Jandex + AOT 生成 Maven 插件
│   ├── summer-coverage-report        JaCoCo 聚合报告
│   └── summer-benchmark              JMH 性能基准
│
└── 示例应用 (demos)
    ├── summer-issue-tracker    Issue 跟踪系统 (mini Jira)
    ├── summer-realworld        RealWorld 博客后端
    └── summer-twitter          Twitter 克隆 (含 WebSocket DM)
```

---

## 2. 公共 API 面

### 2.1 用户 API（有意 public，不需要 @Internal）

| 包 | 类/注解 | 用途 |
|---|---|---|
| `core` | `@Component` | 标记组件 |
| `core` | `@Configuration` / `@Bean` | 工厂配置 |
| `core` | `@ConditionalOnBean` / `@Replaces` | 条件装配 |
| `core` | `BeanContainer` | DI 容器入口 |
| `core` | `Provider<T>` | 注入提供器 |
| `core` | `ApplicationRunner` | 启动钩子 |
| `core` | `@ConfigMapping` / `@WithDefault` / `@WithName` | 配置绑定 |
| `core` | `Validator<T>` | SPI: Bean 生命周期校验 |
| `core` | `Engine` / `ContainerEngine` | SPI: 引擎选择 |
| `core.exception` | `SummerException` 及 12 个子类 | 异常体系 |
| `web` | `@RestController` | REST 控制器 |
| `web` | `@Get` / `@Post` / `@Put` / `@Delete` | HTTP 方法映射 |
| `web` | `@PathParam` / `@QueryParam` | 参数绑定 |
| `web` | `@ExceptionHandler` | 全局异常处理 |
| `web` | `@GlobalMiddleware` | 全局中间件 |
| `web` | `HttpContext` | 请求上下文 |
| `web` | `Request` / `RequestAttributes` | 请求模型 |
| `web` | `Handler` | 函数式处理器 |
| `web` | `Middleware` | 中间件接口 |
| `web` | `BodyConverter` | SPI: Body 序列化 |
| `web` | `HttpRouter` | SPI: HTTP 路由 |
| `web` | `AuthMiddleware` | SPI: 认证中间件 |
| `web` | `WsRouteProvider` / `WsLifecycleBuilder` | SPI: WebSocket 路由 |
| `web` | `RouteRegistrar` | SPI: 路由注册 |
| `web` | `ServerOriginChecker` | SPI: 跨域校验 |
| `web` | `BodyParser` | Body 解析 + 校验 |
| `web.exception` | `SummerWebException` 及 4 个子类 | Web 异常 |
| `web.annotation` | 7 个注解 | HTTP 路由注解 |
| `data.jdbc` | `JdbcTemplate` | JDBC 模板 |
| `data.jdbc` | `@RowModel` | 行模型注解 |
| `data.jdbc` | `QueryTemplate` / `QueryBuilder` | 类型安全查询 |
| `data.jdbc` | `RowMapper` | SPI: 行映射 |
| `data.redis` | `SummerRedisTemplate` | Redis 模板 |
| `data.redis` | `RedisProperties` | Redis 配置 |
| `aop` | `@Interceptor` / `@InterceptorBinding` | AOP 注解 |
| `aop` | `MethodInterceptor` | SPI: 方法拦截 |
| `aop` | `TargetInvoker` | SPI: 方法调用 |
| `tx` | `@Transactional` | 事务注解 |
| `tx` | `TransactionManager` | SPI: 事务管理 |
| `tx` | `TransactionStatus` | 事务状态 |
| `grpc` | `GrpcServerConfig` / `GrpcTlsConfig` | gRPC 配置 |
| `test` | `@SummerTest` | 测试注解 |
| `test` | `@DualEngine` | 双引擎测试 |
| `test` | `@Mock` | Mock 注入 |
| `test` | `@TestResource` | 测试资源 |
| `test` | `@TestProfile` | 测试 Profile |
| `test` | `TestResource` (接口) | SPI: 外部资源生命周期 |
| `test` | `SummerTestProfile` | SPI: 自定义 Profile |

### 2.2 @Internal 标注（~55 类，全覆盖）

所有框架内部实现（Discovery、BeanDefinition、BeanEnrichment、ConfigBinder、TypeConverter、BodyParser、RouterRegistry、RadixTrie、ParameterResolver、TransactionInterceptor、EntityMetadata、RowMapperFactory、JsonRedisCodec、GrpcServerRunner 等）已于 2026-07-29 统一标注 `@Internal`。

### 2.3 审计发现

- **ContainerEngine** — SPI 接口，原误标 `@Internal`，已取消。
- **internal/ 包** — 已删除，统一使用 `@Internal` 注解。
- **SPI 接口**（24 个，全部正确 public，不标 @Internal）：
  `ApplicationRunner`, `Provider<T>`, `Validator<T>`, `Handler`, `Middleware`, `AuthMiddleware`, `BodyConverter`, `HttpParameterResolver`, `WsRouteProvider`, `WsLifecycleBuilder`, `WebSocketHandler`, `WsInterceptor`, `WebSocketBroadcaster`, `MethodInterceptor`, `InterceptorChain`, `TransactionManager`, `TransactionStatus`, `RowMapper<T>`, `TestResource`, `SummerTestProfile`, `SummerBootstrap`

---

## 3. 架构规则

### 3.1 分层定义 (ArchitectureTest)

| 层 | 包 | 可被谁访问 |
|---|---|---|
| **Core** | `summer.core..` | 无人 (mayNotAccessAnyLayer) |
| **Infrastructure** | `summer.runtime..`, `summer.plugin..`, `summer.aot..` | Web, Data, CrossCutting, Server, Test |
| **Web** | `summer.web..`, `summer.boot..` | Infrastructure, Server, Test |
| **Data** | `summer.data..` | Infrastructure, Test |
| **CrossCutting** | `summer.aop..`, `summer.tx..`, `summer.validation..` | Web, Data, Infrastructure, Server, Test |
| **Server** | `summer.web.netty..`, `summer.grpc..` | Test only |
| **Test** | `summer.test..`, `summer.tck..`, `summer.arch..` | 自己 (可访问所有) |

### 3.2 禁止依赖 (全部通过 ✓)

| 禁止项 | 状态 |
|---|---|
| `io.github.classgraph:*` | 未发现 |
| `net.sf.cglib:*` / `org.springframework.cglib:*` | 未发现 |
| `net.bytebuddy:*` | 未发现 |
| Spring Framework | 未发现 |
| 循环包依赖 | 无 (DAG) |
| Core 依赖其他 Summer 模块 | 仅字符串 (DotName, ClassName)，无字节码级依赖 |
| AOT/Runtime 隔离 | 零交叉导入 |
| `@ConfigMapping` 仅用于 interface | ✓ |
| `@Replaces` 约束 | ✓ |

### 3.3 架构违规 (5 项，待修复)

| # | 严重度 | 文件 | 问题 |
|---|--------|------|------|
| V1 | 低 | `ContainerEngines.java` | `ConcurrentHashMap` + `ServiceLoader` 在 Core 生产代码中——架构规则禁止。 |
| V2 | 中 | `MetricsRegistry.java` | `@Component` 而非 `@Configuration` + `@Bean`。 |
| V3 | 低 | `summer-boot/pom.xml` | compile 依赖 `summer-runtime` 但无 Java import——应 `runtime` scope。 |
| V4 | 低 | `summer-aot-engine/pom.xml` | compile 依赖 `summer-data-jdbc` 但仅字符串引用。 |
| V5 | 中 | `ArchitectureTest.java:105` | `web.netty..` 包路径不匹配实际代码（`web.server`）。Server 层实际只有 gRPC。 |

---

## 4. 测试覆盖

| 模块 | 测试文件数 | 覆盖范围 | 盲区 |
|---|---|---|---|
| summer-core | 7 | DI, 配置绑定, TypeConverter | `Discovery` 纵向集成 |
| summer-web | 12 | Router, ParameterResolver, BodyParser | `WebInfrastructureConfiguration` |
| summer-web-http | 3 | MapRouter, RadixTree | — |
| summer-web-netty | 4 | Handler, HTTP middleware, WebSocket 回环 | Netty pipeline 错误路径 |
| summer-web-middleware | 3 | CORS, Logging, Metrics | — |
| summer-aop | 1 | 拦截器链 | 复杂的多拦截器编排 |
| summer-tx | 4 | @Transactional, TransactionManager | — |
| summer-runtime | 14 | Proxy, DI, Route, Config binding | — |
| summer-aot-engine | 3 | 代码生成, 跨模块发现 | 已补 WireMethod 数字强制回归测试 |
| summer-data-jdbc | 9 | JdbcTemplate, QueryBuilder, RowMapper, IT | 事务恢复/回滚场景 |
| summer-data-redis | 7 | RedisTemplate, 自动配置, 集成 | — |
| summer-grpc | 1 | gRPC 拦截器集成 | gRPC Server 启停, TLS, streaming |
| summer-test | 1 | 测试框架自我测试 | — |
| summer-tck | 45 | DI, AOP, Config, Web, 双引擎 parity | — |
| summer-archunit | 4 | 分层, 环依赖, 禁止依赖 | — |
| summer-issue-tracker | 11 | Auth, RBAC, Issue CRUD, Tag, Audit, 并发 | — |
| summer-realworld | 5 | 基础 CRUD | 403/401 路径, 并发 (无集成测试) |
| summer-twitter | 11 | Auth, Tweet, Follow, Like, DM, Timeline | WebSocket DM 并发 |

---

## 5. 已消除的死代码

| 文件 | 说明 |
|---|---|
| `summer-integration-test/` (全模块) | 旧集成测试模块 |
| `Testing.java` | 通用测试工具类 |
| `TestContainerBuilder.java` | 测试容器构建器 |
| `TestContainerFactory.java` | 测试容器工厂 |
| `TestProfileSpec.java` | 旧 Profile 规范 |
| `DevServicesHolder.java` | 旧 Dev Services |
| `TestcontainersDevServicesHolder.java` | 旧 Testcontainers 持有者 |
| `RuntimeBeanContainerBuilder.java` (6 重载) | 合并入 RuntimeContainer |
| `UserRepository.findByOrg()` / `findByProject()` | 泄漏 passwordHash 的死方法 |
| `UserService.listByOrg()` / `listByProject()` | 死代理方法 |
| `ProjectRepository.findByKey()` / `findByOrg()` | 未使用的方法 |
| `CommentRepository.findById()` / `delete()` | 未使用的方法 |
| `AotProductionIT.java` | 旧 AOT 测试 |
| `summer-starter-parent/pom.xml` | 合并入 summer-build-parent |
| `internal/` 包 (7 个类) | 迁移至 `@Internal` 注解 |

---

## 6. 业务逻辑审计

### 6.1 summer-issue-tracker (9 项，全部已修复)

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| F2 | 中 | SHA-256 无盐 | SHA-256 + 16-byte random salt |
| F9 | 高 | 删 Issue 炸 FK | 先清 tags/comments 再删 issue |
| F12 | 中 | Org 注册竞态 | ON CONFLICT DO NOTHING |
| F13 | 中 | addMember 竞态 | try-insert → 409 |
| F16 | 高 | createIssue 不验 title | @NotBlank + validatedBody() |
| F18 | 高 | addComment 不验 body | @NotBlank + validatedBody() |
| F17 | 中 | status/priority null 静默默认 | 拒绝 null |
| F19/F20 | 中 | Tag/Project DTO 无校验 | @NotBlank + validatedBody() |
| F22/F23 | 中 | 凭据硬编码 | env var 外化 |

### 6.2 summer-realworld (10 项，待修复)

| # | 严重度 | 问题 | 位置 |
|---|--------|------|------|
| R1 | HIGH | 无登录暴力破解防护 | `UserController.java:41-53` | ✓ `LoginRateLimiter`: 5次/15分钟/email → 429 |
| R2 | HIGH | 删文章不清 favorites/comments（孤儿数据） | `ArticleService.java:92-94` | ✓ 级联删除: FavoriteRepository.deleteByArticleId + CommentRepository.deleteByArticleId |
| R3 | HIGH | JWT secret 硬编码 | `application.yml:7` | ✓ `${JWT_SECRET:-change-me-in-production}` |
| R4 | MEDIUM | 注册不校验密码最小长度 | `UserService.java:25-27` | ✓ DTO `@Size(min=8)` + service 层双重检查 |
| R5 | MEDIUM | 注册无 `@Email` 格式校验 | `UserDtos.java:11` | ✓ `@Email` on RegisterRequest.User.email |
| R6 | MEDIUM | `isTokenExpired()` 死代码从未调用 | `JwtUtil.java:64-71` | ✓ 新增 `validateAccessToken()` 串联 expired/invalid/missing → 401，删除 controller 重复 token 校验 |
| R7 | MEDIUM | 自关注未拦截 | `ProfileController.java:44-60` | ✓ followUser 加 currentUserId.equals(targetId) → 422；getProfile 改用 tryGetCurrentUserId |
| R8 | MEDIUM | 无集成测试（全单元测试） | `src/test/` | 补 FavoriteRepositoryTest, LoginRateLimiterTest; 39 单测全绿 |
| R9 | LOW | `generateRefreshToken()` 无对应 endpoint | `JwtUtil.java:35-38` | ✓ `POST /api/users/refresh` + `validateRefreshToken()` + token rotation |
| R10 | LOW | `FavoriteRepository.countByArticleId()` O(n) 全量扫描 | `FavoriteRepository.java:22-30` |

**正面：BCrypt+盐；零 SQL 注入；`validatedBody()` 已集成。**

### 6.3 summer-twitter (11 项，待修复)

| # | 严重度 | 问题 | 位置 |
|---|--------|------|------|
| T1 | HIGH | 无登录暴力破解防护 | `AuthController.java:64-82` | ✓ `LoginRateLimiter`: 5次/15分钟/username → 429 |
| T2 | HIGH | 删 tweet 不清 likes/replies/Redis——大量孤儿数据 | `TweetService.java:134-142` |
| T3 | HIGH | 全表 FK 缺 `ON DELETE CASCADE` | `01-schema.sql` 10 处 |
| T4 | HIGH | JWT + DB 凭据硬编码 | `application.yml:6-8,15` |
| T5 | HIGH | 无权限删 tweet 静默 no-op 返 204（应 403） | `TweetService.java:135` |
| T6 | MEDIUM | 注册不校验 email 唯一性（DB 层面炸 500） | `AuthController.java:37-62` |
| T7 | MEDIUM | 无密码复杂度校验 | `AuthController.java:36-62` | ✓ `@Size(min=8)` on password + DTO validatedBody |
| T8 | MEDIUM | `DmRepository.findConversations/findMessages` 死代码 | `DmRepository.java:61-78` |
| T9 | MEDIUM | follow/like 的 check-then-act 竞态无 DB 错误处理 | `FollowService.java:30-43` |
| T10 | MEDIUM | follower/following/like 计数可降为负数 | `FollowService.java:55-56` |
| T11 | MEDIUM | followers/following 查询无 limit 上限 | `FollowController.java:60` |

**正面：BCrypt+盐；自关注已拦截；零 SQL 注入；`validatedBody()` 已集成。**

---

## 7. 框架设计待讨论

### 7.1 `HttpContext.body()` 应默认校验

`validatedBody()` 已提供——但和 `body()` 是两套 API。新用户可能误用 `body()` 跳过校验。建议未来版本 `body()` 默认校验，加 `rawBody()` 跳过。

### 7.2 gRPC 测试覆盖薄弱

`summer-grpc/src/test` 只有 1 个测试文件。未覆盖 gRPC Server 启动、TLS 配置绑定、streaming RPC。

### 7.3 AOT 测试仅验证代码生成

`WireMethodGeneratorTest` 验证生成的代码语法，但不执行生成的容器。TCK 填了这个空缺（双引擎 parity）但 TCK 在 `summer-tck` 模块，与 AOT 模块隔离。

### 7.4 `summer-validation` 模块为空

`summer-validation/src/main/java` 下无源文件，只有 `pom.xml`。`Validator<T>` 接口在 `summer-core` 里。可删除或收归。

### 7.5 `summer-web-http` / `summer-web-websocket` 细粒度模块

Map 和 RadixTrie 两种 Router 实现分别放在独立模块——对框架使用者不可见，但增加了 Maven 模块数。可考虑合并到 `summer-web`。

---

## 8. 审计统计

| 类别 | 数量 |
|---|---|
| 框架模块 | 23 |
| Demo 模块 | 3 |
| 用户 API 类 | ~60 |
| SPI 接口 | 24 |
| @Internal 标注 | ~55 类 (✓ 全覆盖) |
| 架构违规 | 5 (待修复) |
| 测试文件总数 | 220+ |
| issue-tracker 缺陷 (已修复) | 9 |
| realworld 缺陷 (待修复) | 10 |
| twitter 缺陷 (待修复) | 11 |
| 死代码已消除 | 15 项 |
| BCrypt/盐 | realworld + twitter ✓ |
