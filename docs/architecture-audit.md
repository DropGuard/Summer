# Summer 框架架构反模式审计报告

**审计日期：** 2026-06-10
**最后更新：** 2026-06-12
**审计范围：** 核心模块（summer-core, summer-aop, summer-web, summer-runtime, summer-boot, summer-tx, summer-web-netty）
**方法：** 代码审查，非 ArchUnit 规则检查

---

## 🔴 高优先级

### 1. `@Configuration` + `@Bean` 急切执行 — 测试隔离失败

**文件：**
- `summer-runtime/src/main/java/summer/runtime/RuntimeBeanFactory.java` — `invokeBeanProducer()`
- `summer-runtime/src/main/java/summer/runtime/DependencyGraph.java`
- `summer-maven-plugin/src/main/java/summer/plugin/BeanDiscovery.java`

**问题：** `@Configuration` 类被扫描发现后，其 `@Bean` 方法立即全部执行。即使没有 bean 依赖某个 `@Bean` 的返回类型，也会创建。测试中两个 `@Configuration` 定义了同类型的 `@Bean`，即使只有一个被需要，也触发 `AmbiguousBeanException`。

**根因：** `DependencyGraph` 只在类级别构建依赖图（构造器参数），不看方法级别（`@Bean` 返回类型）。`@Bean` 方法的发现是运行时反射完成的，无法在图构建阶段做按需过滤。

**Jandex 可用性：** `@Bean` 方法及其返回类型在 Jandex 索引中已有记录，但当前代码未利用。

**修复方案：**
- `DependencyGraph` 通过 Jandex 索引预读 `@Bean` 方法的返回类型，在依赖图中注册"该类型有一个生产者"
- `RuntimeBeanFactory.invokeBeanProducer()` 改为按需调用（有依赖者时才执行），不在 `initializeBeans()` 时全部执行
- `@Configuration` 仍被扫描发现，但其 `@Bean` 方法延迟到有消费者时才执行

**影响：** 测试隔离（多个 `@Configuration` 可共存不冲突）、启动性能（未被依赖的 `@Bean` 不创建）

---

## 🟡 中优先级

### 2. 过度抽象 — 250 行的 `TransactionAwareConnectionWrapper`

**文件：** `summer-tx/src/main/java/summer/tx/TransactionAwareConnectionWrapper.java`

**问题：** 实现整个 `java.sql.Connection` 接口（44 个方法），只为压制 3 个方法（`close()`、`commit()`、`rollback()`）。其余 41 个方法是纯委托样板代码。250 行代码中只有 3 行做真正的工作。

**修复：** 用 JDK 动态代理替代完整实现，250 行 → 20 行。

---

### 3. 不当亲密 — `NettyHttpServerHandler` 职责过多

**文件：** `summer-web-netty/src/main/java/summer/web/server/NettyHttpServerHandler.java` (262行)

**问题：** 这个类做了太多事：
1. 直接构造 `HttpContext`
2. 创建中间件链
3. 处理 WebSocket 升级检测和管道操作
4. 多重回退策略的异常处理
5. 通过 `BodyConverter` 序列化响应
6. 管理 keep-alive 逻辑
7. 管理活跃连接计数

WebSocket 升级逻辑（115-149行）尤其有问题 — 它直接操作 Netty 管道，检查 origin 头，委托给 `WsRouter`，这些都应该在 HTTP 请求处理器之外。

**修复：** 提取 WebSocket 升级处理为独立的 `WebSocketUpgradeHandler`。

---

## 🟢 低优先级

### 4. 投机泛化 — `HttpMethod` 枚举包含未使用的值

**文件：** `summer-web/src/main/java/summer/web/HttpMethod.java`

```java
public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;
}
```

**问题：** 声明了 `PATCH`、`HEAD`、`OPTIONS`，但没有对应的 `@Patch`、`@Head`、`@Options` 注解，也没有 builder 方法。用户无法通过注解使用这些 HTTP 方法。

**修复：** 要么添加对应的注解和 builder 方法，要么移除未使用的枚举值。

---

### 5. 抽象泄漏 — `Handler.handle()` 返回 `Object` 但返回值被忽略

**文件：** `summer-web/src/main/java/summer/web/Handler.java`

```java
@FunctionalInterface
public interface Handler {
    Object handle(HttpContext ctx);
}
```

**问题：** 返回类型暗示处理器会产生结果，但框架文档明确说"返回值不被框架使用"。`NettyHttpServerHandler.processRequest()` 从未捕获返回值。新开发者可能写 `return ctx.json(...)` 期望它工作，但 `json()` 返回 `void`。

**修复：** 改为 `void handle(HttpContext ctx)`。

---

### 6. 数据泥团 — `NettyHttpServer` 构造函数 7 个参数

**文件：** `summer-web-netty/src/main/java/summer/web/server/NettyHttpServer.java`

```java
public NettyHttpServer(ServerConfig config, HttpRouter httpRouter, WsRouter wsRouter,
    List<Middleware> middlewares, BodyConverter jsonConverter,
    ExceptionRegistry exceptionRegistry, List<WsInterceptor> wsInterceptors)
```

**问题：** 这 7 个参数总是成组传递，是典型的数据泥团。

**修复：** 引入 `WebServerConfig` 或 `WebServerDependencies` record 打包这些对象。

---

## 📊 总结矩阵

| 优先级 | 反模式 | 位置 | 影响 |
|--------|--------|------|------|
| 🔴 高 | @Configuration + @Bean 急切执行 | summer-runtime | 测试隔离失败，启动性能浪费 |
| 🟡 中 | 250 行 Connection 包装器 | summer-tx | 维护负担，3 个有效方法 |
| 🟡 中 | NettyHttpServerHandler 职责过多 | summer-web-netty | WebSocket 升级逻辑混在 HTTP 处理中 |
| 🟢 低 | HttpMethod 枚举投机泛化 | summer-web | 误导性 API |
| 🟢 低 | Handler 返回 Object 但被忽略 | summer-web | 误导性 API |
| 🟢 低 | 构造函数 7 参数数据泥团 | summer-web-netty | 可读性差 |
| 🟡 中 | 测试 fixture 重复 | summer-tck | 隔离问题 |
| 🟡 中 | RuntimeRouteRegistrar 用反射而非 Jandex | summer-runtime | 架构不一致 |

### 7. 测试 fixture 重复 — `summer.tck.dummy.*` vs `summer.fixtures.dummy.*`

**问题：** `summer-tck/src/test/java/summer/tck/dummy/` 和 `summer-tck-fixtures/src/main/java/summer/fixtures/dummy/` 存在同名类（`ServiceA`, `ServiceB`, `ServiceC` 等）。测试源码的 fixture 不在预编译 Jandex 索引里，导致 Runtime 引擎（只加载索引）无法发现它们。

**修复：** 删除 `summer-tck/src/test/java/summer/tck/dummy/` 中的重复 fixture，统一使用 `summer-tck-fixtures` 中的共享版本。

### 8. `RuntimeRouteRegistrar` 用反射发现路由而非 Jandex

**文件：** `summer-runtime/src/main/java/summer/runtime/RuntimeRouteRegistrar.java`

**问题：** `RuntimeRouteRegistrar.registerControllers()` 遍历 `context.getRegisteredTypes()`，用 `clazz.isAnnotationPresent(RestController.class)` 和 `method.getAnnotation(Get.class)` 反射发现路由。`@RestController`、`@Get`、`@Post` 等注解在 Jandex 索引中已有记录，完全不需要反射。

**影响：**
- 违反 "Reflection confined to `summer-runtime`" 原则——虽然此类在 `summer-runtime` 内，但它用反射做的事 Jandex 已经能做
- 与组件发现（走 Jandex）不一致，同一框架两种发现机制
- 恰好让不在索引中的测试内部类也能被发现，掩盖了 fixture 隔离问题

**修复：** `RuntimeRouteRegistrar` 接收 `IndexView`（从 `ComponentScanner.getLastIndex()` 获取），用 `index.getAnnotations(RestController)` 发现控制器，用 Jandex 读取 `@Get`/`@Post` 方法和路径值。测试内部类自然不会被发现，需要把测试控制器放在索引能覆盖的位置。

---

## ✅ 已解决（本次审计周期）

| 原编号 | 问题 | 解决方式 |
|--------|------|---------|
| #1 | AOP 绑定匹配重复 3 次 | 提取 `BindingMatcher` 工具类，`DependencyGraph` 和 `ProxyFactory` 统一委托 |
| #2 | ConfigurationBinder 全局可变状态 | `ValueResolver` 架构删除，替换为无状态的 `ConfigBinder.bind()` |
| #3 | Stringly-Typed 请求属性 | 删除 raw `setAttribute(String, Object)` API，路径参数独立为 `setPathParam()`，属性用 `AttributeKey<T>` |
| #4 | HttpContext God Class | 已重构为 Facade（BodyParser 已提取），by design |
| #5 | RuntimeApplicationContext 时间耦合 | `@SummerTest` extension 自动管理生命周期，`entryPoint` 已删除 |
| #7 | Response 无封装 | 包私有 + HttpContext 门面，by design |
| #10 | 空配置类 RuntimeInfrastructureConfiguration | 已删除 |
| — | 配置绑定逻辑重复 | 提取 `ConfigBinder` 到 summer-core，Runtime 和 AOT 引擎统一委托；`ConfigPropertiesGenerator` 删除 |
| — | AOT 生成不必要的 Provider 类 | `WireMethodGenerator` 直接调用 `ConfigBinder.bind()`，不再生成 `_ConfigPropertiesProvider` |
| — | AGENTS.md / CLAUDE.md 冗余指令 | 精简 GitNexus 指令，修复 Build 段描述 |
| — | Runtime 引擎运行时 .class 扫描 | `JandexIndexLoader` 删除 `.class` 文件扫描（180→75 行），只加载 `META-INF/jandex.idx`；`ComponentScanner` 删除包扫描逻辑（225→120 行）；两个引擎统一用预编译索引 |
| — | `@SummerTest` 基础设施 | 新增 `SummerExtension`（`BeforeAllCallback` + `TestInstancePostProcessor` + `ParameterResolver`），TCK 测试从手写 `createContext()` 改为一行 `@SummerTest` 注解 |
| #1 | `@Configuration` + `@Bean` 急切执行 | `@Bean` 方法参与依赖图构建（`Method` 节点），`@ConditionalOnBean` 能看到 `@Bean` 返回类型，`@Replaces` 支持方法级覆盖，歧义检测抛 `AmbiguousBeanException` |
| #4 | HttpMethod 枚举投机泛化 | 移除未使用的 `PATCH`、`HEAD`；保留 `OPTIONS`（CORS 中间件使用） |
| #5 | Handler 返回 Object 但被忽略 | 添加 Javadoc 明确说明返回值不被框架使用（改为 `void` 需重写测试中间件，暂不实施） |
| #6 | 构造函数 7 参数数据泥团 | 引入 `WebServerDependencies` record，`NettyHttpServer` 和 `NettyHttpServerHandler` 构造函数简化为 2-3 个参数 |
| #7 | 测试 fixture 重复 | `CircularA`/`CircularB`/`ConflictConfig`/`TxTestConfiguration` 移至 test scope，由测试显式注册 |
| #8 | `ConfigBinder` 使用反射违反分层 | 反射逻辑提取为 `RuntimeDefaultValueResolver`（summer-runtime），`ConfigBinder` 通过 `DefaultValueResolver` 策略接口委托 |
| #3 | NettyHttpServerHandler 职责过多 | 提取 `WebSocketUpgradeHandler`，WebSocket 升级逻辑独立为专用类；`NettyHttpServerHandler.createHandlerChain()` 从 75 行降至 35 行 |
| #8 | RuntimeRouteRegistrar 用反射而非 Jandex | 改为 Jandex-first 混合策略：优先用 `index.getAnnotations()` 发现路由，未在索引中的类（如测试内部类）回退到反射 |
