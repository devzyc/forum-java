---
trigger: glob
glob:
  - "forum-portal/src/main/java/pub/developers/forum/portal/support/**/*.java"
  - "forum-facade/src/main/java/pub/developers/forum/facade/aspect/**/*.java"
  - "forum-app/src/main/java/pub/developers/forum/app/support/**/*.java"
  - "forum-app/src/main/java/pub/developers/forum/app/manager/AbstractLoginManager.java"
  - "forum-app/src/main/java/pub/developers/forum/app/manager/UserManager.java"
  - "forum-infrastructure/src/main/java/pub/developers/forum/infrastructure/cache/**/*.java"
  - "forum-domain/src/main/java/pub/developers/forum/domain/service/CacheService.java"
---
<!-- 登录认证全链路：token 机制、用户上下文双轨制、会话缓存；Portal/Facade/App/Infra 认证相关分包生效 -->

# 登录认证与会话管理

## 登录态机制

- 凭证形式：客户端持有 token，每次请求放在 Header `token` 中；登录、注册只在响应体返回 token，不使用 Set-Cookie。
- token 取值两级：先 Header，再 Query（工具方法 `WebUtil.resolveToken`）。
- `GlobalViewInterceptor.preHandle` 统一把用户注入 request attribute，业务代码不各自解析 token。
- 会话缓存存于 `CacheBizTypeEn.USER_LOGIN_TOKEN` 两条 key：token → user JSON、uid → token。
- 单账号单会话：新登录要踢掉旧会话，两条 key 同步覆盖。
- 有效期由配置项控制并支持滑动续期；登出接口 `POST /user-rest/logout` 删除两条 key。

## 用户上下文双轨制（易踩线）

- App 层使用 `LoginUserContext`：基于 ThreadLocal，仅限 `forum-app` 内部使用，由 facade 层 `LoginUserAspect` 注入。
- Portal 层使用 `WebContext.getCurrentUser()` 获取当前用户。
- **硬约束**：Portal 层代码直接 import `LoginUserContext` 会编译失败，必须走 `WebContext`。

```java
// Do：Controller 中取当前登录用户
User current = WebContext.getCurrentUser();
```

- 新增业务方法需要当前用户时，优先由上层显式传参，不要在 app 层重复从 ThreadLocal 兜底读取，避免掩盖注入缺失。

## 缓存演进

- 当前：`DbCacheServiceImpl` 是单机内存 Map 加定时刷库，适用于单实例部署。
- 演进条件：多实例部署时必须换成 Redis。
- 改造方式：domain 层 `CacheService` 接口保持不变，只新增实现类并切换装配，调用方代码不改。
- 判定信号：出现"登录后另一台机器查不到会话"这类现象，就是该换 Redis 了。
