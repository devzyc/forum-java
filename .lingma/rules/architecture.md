<!-- 后端分层架构与接口约定：模块依赖方向、用户上下文、登录态、接口路径；Java 源码中生效 -->

# 后端架构与接口约定

## 分层与依赖方向（硬约束）

- 单体 Spring Boot，启动入口在 `forum-starter`，共 8 个 Maven 模块。
- 严格单向调用链：
  `forum-portal`（Controller）→ `forum-api`（接口定义）→ `forum-facade`（实现）→ `forum-app`（业务）→ `forum-domain`（实体）→ `forum-infrastructure`（持久化）。
- Portal 层不得直接依赖 `forum-app` 或 `forum-facade` 的内部类，不得跨层调用（例如 Controller 里直接调 `UserManager`）。
- 写任何后端代码前，先判断这次改动应该落在哪个模块，再动手；不要为了省事在 Controller 里堆业务逻辑。
- 业务逻辑放 `forum-app`，接口契约放 `forum-api`，实现组装放 `forum-facade`，SQL 与 Mapper 只放 `forum-infrastructure`。

## 用户上下文双轨制（易踩线）

- App 层使用 `LoginUserContext`：基于 ThreadLocal，仅限 `forum-app` 内部使用，由 facade 层切面注入。
- Portal 层使用 `WebContext.getCurrentUser()` 获取当前用户。
- 硬约束：Portal 层代码直接 import `LoginUserContext` 会编译失败，必须走 `WebContext`。

```java
// Do：Controller 中取当前登录用户
User current = WebContext.getCurrentUser();
```

- 新增业务方法需要当前用户时，优先由上层显式传参，不要在 app 层重复从 ThreadLocal 兜底读取，避免掩盖注入缺失。

## 登录态机制

- 凭证形式：客户端持有 token，每次请求放在 Header `token` 中；登录、注册只在响应体返回 token，不使用 Set-Cookie。
- token 取值两级：先 Header，再 Query（工具方法 `WebUtil.resolveToken`）。
- `GlobalViewInterceptor.preHandle` 统一把用户注入 request attribute，业务代码不各自解析 token。
- 会话缓存存于 `CacheBizTypeEn.USER_LOGIN_TOKEN` 两条 key：token 到 user JSON、uid 到 token。
- 单账号单会话：新登录要踢掉旧会话，两条 key 同步覆盖。
- 有效期由配置项控制并支持滑动续期；登出接口 `POST /user-rest/logout` 删除两条 key。
- 缓存实现 `DbCacheServiceImpl` 是单机 Map 加定时刷库；多实例部署需要换 Redis，替换点在 domain 层接口，只新增实现类，不改调用方。

## 接口路径与清单

- 新接口路径不加 `-rest` 后缀；存量 `/article-rest`、`/user-rest` 前缀保持不改名。
- 已知映射：文章详情为 `POST /article-rest/{id}`（不是 GET）；评论列表为 `GET /article-rest/comments/{id}`。
- 接口清单以代码为准，核对方式是在 `forum-portal` 下检索 `@RequestMapping`、`@GetMapping`、`@PostMapping`。
- 给的接口示例必须与 Controller 注解一致，尤其是 HTTP 方法，不要凭命名猜动词。

## 当前形态

- 纯 REST 后端，服务小程序端。
- Thymeleaf 页面、GitHub OAuth、FAQ、后台管理入口均已移除，不要再按老形态写代码。
- 发文不再有 `typeId`、`ContentType` 概念，正文只存 `html_content`。
- `forum-vue` 管理后台前端已移除，仓库内没有可运行前端。

## 标签接口

- 标签接口返回值带 `isHot` 字段标识热门标签，前端展示依赖该字段，不要删除或改名。
- 标签创建不再走独立接口，新标签在发文流程内自动处理（详见决策记录）。
