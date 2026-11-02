# 小程序登录态改造定稿方案：Cookie → Token Header

| 项 | 内容 |
|---|---|
| 文档状态 | **后端已实施并通过本地验收**（第 7 节 1–9 项已跑通），小程序端待接入 |
| 影响范围 | 登录态传输、会话有效期、失效信号、登出能力 |
| 不影响范围 | token 生成算法、服务端会话存储模型、所有业务逻辑与数据库结构 |
| 破坏性 | **有**（前端必须改为携带 `token` 请求头），但后端保留 Query 兜底，改动可一次完成 |
| 分支 | `main` |

---

## 1. 背景与结论

本项目源自开源论坛 `developers/forum`，其会话机制诞生于 **PC 浏览器网页**场景：服务端 `Set-Cookie: sid=...`，浏览器 Cookie Jar 自动回带。瘦身为纯 REST 后端后，同一套机制被直接用于服务微信小程序客户端。

**结论：抛弃 Cookie 通道，凭证一律通过 `token` 请求头显式传递；服务端会话模型不变，但把"绝对过期"改为"滑动窗口"，并为会话失效提供统一的 HTTP 401 信号。**

已评估并否决的两个方向：

- ❌ **双通道长期并行（Cookie + Header）** —— 否决。两套传输方式共用同一份服务端会话，并行只意味着"取值入口多一条"，但会让前端、Postman、后端三方的心智负担翻倍，且最终仍需删除。改为一步到位。
- ❌ **加请求参数区分新老架构** —— 否决。服务端不存在两套架构，只有两个取值入口；加参数会重新制造"每个请求都要特殊配置"的问题，且是未来必须删除的死代码。

---

## 2. 架构变化总览

| 维度 | 改造前（浏览器时代） | 改造后（为小程序设计） |
|---|---|---|
| 凭证传输 | `Set-Cookie: sid=xxx; Path=/`，客户端依赖 Cookie Jar 自动携带 | 请求头 `token: xxx`，值由客户端自己存、自己带 |
| 凭证存储位置 | 微信运行时内存级 Cookie Jar，**杀进程即丢** | `wx.setStorageSync('token')`，落盘持久化，跨冷启动 |
| token 产生 | `StringUtil.generateUUID()` | **不变** |
| 服务端会话存储 | `CacheService` / `DbCacheServiceImpl`，两条 key：`token→user`、`uid→token` | **不变** |
| 会话有效期 | 硬编码 7 天、绝对过期（改一处要重新编译部署） | 配置化 + **滑动窗口**：空闲满 1 天才失效 |
| 失效信号 | HTTP 200 + `code:80008998` 或 `80007988`（两个魔数，前端要都认） | **HTTP 401**（body 结构保留 `code` 作二级判据） |
| 权限不足 | 与"未登录"混在同一处理路径 | **HTTP 403**，与"未登录"严格分离 |
| 取值入口 | Cookie → Header → Query 三级兜底 | Header → Query 两级（**删除 Cookie 分支**） |
| Controller 样板 | 每方法手写 `request.setAttribute("token", cookieGetSid(request))`，全项目 19 处（另 1 处在拦截器里） | 全部删除，由拦截器统一注入一次 |
| 登出 | 服务层 `UserManager.logout()` 早已存在，但**没有 REST 入口**（`cookieDelSid` 定义了却从未被调用） | 新增 `POST /user-rest/logout` |
| 页面时代遗产 | `GetMappingAspect`、`ViewException`、`postHandle` 里塞 Thymeleaf 变量 | 删除（见第 5 节，其中一个是真实风险） |

---

## 3. 请求契约

### 3.1 通用规则

请求头：

```
Content-Type: application/json
token: <登录接口返回的 data 字符串>
```

- 不带 `token` 头 = 游客。
- 带 `token` 头但服务端已不认识 = 需要登录的接口返回 401；游客接口照常 200。

响应外壳永远是 `ResultModel`，HTTP 状态码是唯一分流依据：

```json
{ "code": 200, "message": "success", "success": true, "data": "<业务数据>" }
```

### 3.2 错误码映射表

`ErrorCodeEn` 构造器带 `8000_0000` 偏移，真实码为 8 位。

| 枚举 | 真实 code | message | HTTP 状态码 |
|---|---|---|---|
| `USER_NOT_LOGIN` | `80008998` | 用户未登陆 | **401** |
| `USER_TOKEN_INVALID` | `80007988` | token 无效 | **401** |
| `COMMON_TOKEN_NO_PERMISSION` | `80008996` | 无操作权限 | **403** |
| `NO_PERMISSION` | `80009993` | 无操作权限 | **403** |
| 其余业务失败 | `8000xxxx` | 各自文案 | **200** |
| 未知异常 | `80009999` | 系统异常 | **200** |

### 3.3 场景 A：注册与登录

`UserManager.register()` 末行为 `return login(registerUser, request)`，**注册即签发 token**（改造前后一致，非本次新增行为）。

```
POST /user-rest/login
Body: {"email":"a@b.com","password":"<明文密码>"}
→ {"code":200,"success":true,"data":"550e8400e29b41d4a716446655440000"}

POST /user-rest/register
Body: {"email":"a@b.com","nickname":"张三","password":"<明文密码>"}
→ {"code":200,"success":true,"data":"<token>"}
```

- `ip` / `ua` 由后端从请求中自取，前端不传。
- **改造后不再下发 `Set-Cookie`。**
- 产品流程约定：注册成功后**前端忽略该 token、不写入 storage**，并引导用户手工登录一次。服务端通过 `deleteLoginUser(uid)` 在下次登录时显式清理被丢弃的 token，无残留、无冲突。

### 3.4 场景 B：会话探活

前端唯一可靠的登录态判定手段（客户端无法预判服务端是否还认这个 token）。

```
GET /user-rest/current
Header: token: <本地 storage 的值>

有效 → HTTP 200
{"code":200,"success":true,"data":{"id":123,"email":"a@b.com","role":"USER",
  "nickname":"张三","sex":"MALE","avatar":"https://...","signature":"...",
  "createAt":"2026-01-01 10:00:00","lastLoginTime":"2026-11-01 09:00:00"}}

失效 → HTTP 401
{"code":80008998,"message":"用户未登陆","success":false,"data":null}
```

实现说明：`current` 不再把 token 当入参传给 `info(token)`，而是改走 `UserApiService.current()` → `UserManager.current()`（标了 `@IsLogin`），从登录上下文取用户。好处有二：不带 token、带过期 token、带乱填 token 三种情况**统一收敛到 `80008998`**（前端只认一个码），且省掉每请求一次重复的缓存查用户 + JSON 反序列化。

策略：`App.onShow` 调一次，成功则把 `data`（含 `id`）存入全局 store。

### 3.5 场景 C：需要登录的写操作

适用于发帖、评论、点赞、关注、改资料、改密码等标注了 `@IsLogin` 的接口。

```
POST /user-rest/follow/{followed}
Header: token: <token>
→ 成功        200  {"code":200,"success":true,"data":null}
→ 未登录/失效  401  {"code":80008998,"message":"用户未登陆","success":false}
→ 非管理员     403  {"code":80008996,"message":"无操作权限","success":false}
```

前端不为这类接口写任何登录判断，401/403 由封装层统一兜住。

### 3.6 场景 D：游客也可访问的读接口（语义必须保住）

```
GET /user-rest/articles/{uid}
GET /user-rest/followers/{uid}
GET /article-rest/{id}
GET /article-rest/comments/{articleId}
```

这些接口**没有 `@IsLogin`**，因此：

- 不带 token → 200，按游客返回；
- 带**已失效** token → **仍然 200**，按游客返回，**不返回 401**。

这是刻意保留的语义，**不得改成一律强校验**，否则"未登录连帖子都不能看"。"过期了要跳登录页"由场景 B 的 `onShow` 探活兜住。

> 分页参数为可选：`pageNo` / `pageSize` 均不传时返回**全量数组**（内部 `pageSize=9999`）；传了则返回 `PageResponseModel` 结构。前端注意 `data` 存在数组与对象两种形状。

### 3.7 场景 E：登出

```
POST /user-rest/logout
Header: token: <token>
→ {"code":200,"success":true,"data":null}
```

服务端删除 `token→user` 与 `uid→token` 两条缓存；客户端各自 `wx.removeStorageSync('token')`。

### 3.8 场景 F：头像 / 图片上传

```
POST /user-rest/update-headimg
Header: token: <token>        ← header 通道对 multipart 同样有效
formData: { file: <图片> }
```

- `wx.uploadFile` 支持 `header`，与 `wx.request` 共用注入逻辑。
- **`wx.uploadFile` 返回的 `res.data` 是字符串**，必须 `JSON.parse` 后才能读 `success` / `code`。
- 类型白名单：`png/jpeg/jpg/ico/gif/bmp/svg`，其余返回 `80004997`。

---

## 4. 后端改动清单

| # | 文件 | 改动 | 性质 |
|---|---|---|---|
| 1 | `forum-portal/.../support/WebUtil.java` | `cookieGetSid` → `resolveToken`，只读 **Header `token` → Query `token`**；删除 `cookieAddSid` / `cookieDelSid` | 核心 |
| 2 | `forum-portal/.../support/WebConst.java` | 删除 `COOKIE_SID_KEY` | 清理 |
| 3 | `forum-portal/.../support/GlobalViewInterceptor.java` | `preHandle` 统一 `request.setAttribute("token", sid)`；删除 `postHandle` 中的 Thymeleaf 死代码，**清理逻辑搬到 `afterCompletion`** | 核心 |
| 4 | **新增** `forum-portal/.../support/TokenResultStatusAdvice.java` | `ResponseBodyAdvice`：按最终响应体 code 注入 401 / 403 | 核心 |
| 5 | `forum-facade/.../aspect/ExceptionHandlerAndLogAspect.java` | `80008998` / `80007988` 日志降级，避免刷爆 `/opt/logs/forum-java` | 加固 |
| 6 | `forum-facade/.../aspect/LoginUserAspect.java` | 缓存命中后调 `cacheService.renew(...)` 做滑动续期 | 核心 |
| 7 | `forum-app/.../manager/AbstractLoginManager.java` | TTL 从硬编码常量改为 `@Value` 注入；续期保持两条 key 成对操作 | 核心 |
| 8 | `forum-domain/.../service/CacheService.java` + `forum-infrastructure/.../cache/DbCacheServiceImpl.java` | 新增 `renew(bizType, key)`（时长不再由调用方传，改为 `StringValue` 新增 `seconds` 字段自描述）；阈值判断与"不复活已过期"逻辑全部内置于 impl | 核心 |
| 9 | `forum-portal/.../controller/rest/UserRestController.java` | 删除本类 8 行样板；删除 2 处 `cookieAddSid` 调用；**新增 `/logout`**；`/current` 改走 `UserApiService.current()`（连带新增 `UserApiService`/`UserApiServiceImpl`/`UserManager.current()` 三处） | 核心 + 清理 |
| 10 | 其余 6 个 `*RestController` | 删除 `request.setAttribute(...)` 样板（合计 11 行）及随之失效的 `HttpServletRequest` 形参与 import | 清理 |
| 11 | `forum-portal/.../support/CorsInterceptor.java` | 删除注释掉的 `Allow-Credentials` 行；`Allow-Headers: Content-Type,token` 已就绪，不动 | 清理 |
| 12 | `forum-portal/.../support/GetMappingAspect.java` + `ViewException.java` + `ResponseBodyAspect.java` | 三个整体删除（页面时代遗产，见 5.2 / 5.3） | 清理 |
| 13 | `forum-starter/src/main/resources/application.properties` | 新增 `custom-config.token.expire-seconds=86400`、`custom-config.cache.renew-threshold-ratio=0.5`（两处 `@Value` 都带默认值，老配置文件不改也能起） | 配置 |
| 14 | `forum-common/.../enums/ErrorCodeEn.java` | `USER_REGISTER_EMAIL_IS_EXIST` 从 `7992` 挪至 `7986`（消除与 `USER_LOGIN_PWD_ERROR` 的重码；`7990` 已被 `USER_ROLE_NOT_EXIST` 占用，故取用户段首个空闲码） | 修正 |

### 4.1 滑动窗口设计（"天天用就一直不过期"）

语义：**距最后一次成功使用满 1 天才失效。**

配置：

```properties
custom-config.token.expire-seconds=86400
custom-config.cache.renew-threshold-ratio=0.5
```

三条实现铁律：

1. **两条 key 必须成对续期。** 只续 `token→user` 会让 `uid→token` 先过期，此后 `deleteLoginUser(uid)` 查不到旧 token，`token→user` 条目**永久残留在内存与 `forum_cache` 表**中 —— 会话只增不减。
2. **"剩余 < 阈值才刷"的判断必须在 `DbCacheServiceImpl` 内部。** `StringValue` 是私有静态内部类，`expire` 字段对外不可见；同时避免每次请求都写入 `MODIFY_KEYS` 造成 5 秒批量刷库的写放大。阈值 0.5 下，日活用户约 2 次 DB 更新/天。
3. **`renew` 绝不能复活已过期条目。** `get()` 不做惰性过期校验（依赖每 5 秒 `@Scheduled` 全表扫描），故 `renew` 必须先判 `expire != -1 && now >= expire → return false`，否则会制造"逻辑上已死、定时任务扫不掉"的幽灵会话。

### 4.2 已明确排除的改动（本轮不做）

| 项 | 理由 |
|---|---|
| 多端登录互不踢下线（`uid:clientType→token`） | 保留现状"单账号单会话"。用户量级不需要，改动只涉及 key 拼法，留待真有需求 |
| `DbCacheServiceImpl` → Redis | 单机 Map + 5 秒刷库在当前量级下足够。`CacheService` **已是 domain 层接口**，将来上多实例只需新增实现类 + `@ConditionalOnProperty`，业务层零改动 |
| JWT | 需要"登出即时失效"与"空闲 1 天过期"，无状态 JWT 无法即时吊销，引黑名单等于绕回服务端存储 |
| 微信 `code2session` 静默登录 | 列为下一步演进。`AbstractLoginManager` 已是模板方法结构，届时新增一个 `WxLoginManager` 即可；本次 Token Header 化正是它的前置条件 |
| 新老架构标志位参数 | 见第 1 节否决理由 |
| 高并发 / 限流 / 连接池调优 | 本项目为小规模论坛，不涉及 |

---

## 5. 三个必须避开的技术坑

### 5.1 401 不能设在 facade 切面里 —— 会污染游客接口

`ExceptionHandlerAndLogAspect` 切的是 `pub.developers.forum.api..*`，而 `GlobalViewInterceptor.preHandle` 对**每一个带 token 的请求**都会调 `userApiService.info(sid)`。若在该切面里 `setStatus(401)`：

```
preHandle: info(过期token) → BizException → 切面吞成 ResultModel → 此时 setStatus(401)
Controller: 帖子详情正常返回 200 数据
最终响应: 状态码残留 401 + body 是成功数据 → 前端跳登录页，但数据其实已拿到
```

**正确落点是 portal 层的 `ResponseBodyAdvice`**：它在 body 写出之前执行，`ServerHttpResponse` 提供 `setStatusCode()`，可基于**最终响应体的 code** 决定状态码 —— 业务成功绝不会被误标 401。一处代码覆盖全部接口，不需要 `@IsLogin` 元信息、不需要新样板、不需要任何编译期开关。

**铁律：用 `setStatusCode`，绝不用 `sendError()`** —— 后者触发容器转发到 `/error`，`{code,message,success}` body 会被 Spring Boot 默认错误结构整体替换。

### 5.2 `GetMappingAspect` 会把 GET 接口异常吞成字符串 `"error"`

该切面拦截 portal 下所有 `@GetMapping`，catch `BizException` 后 `return "error"`。对 `@RestController` 而言该字符串会被**当作响应体原样写出**，前端拿到 `"error"` 而非 `ResultModel`，`JSON.parse` 直接失败。

目前 `current` 恰好安全（异常已在 facade 层转为 ResultModel）。但只要将来有 GET 接口在 **Controller 层**直接抛 `BizException`，前端就会踩到。**整体删除 `GetMappingAspect` 与 `ViewException`**，它们服务的是已不存在的 Thymeleaf 错误页。

### 5.3 删 `postHandle` 必须成对搬走 ThreadLocal 清理

`WebContext.removeAll()` / `RequestContext.removeAll()` 当前写在 `postHandle` 的 finally 中，但 **handler 抛异常时 Spring 不调用 `postHandle`**（只调 `afterCompletion`）→ 清理不执行 → Tomcat 线程池复用线程时，下一个请求可能读到上一个用户残留的 ThreadLocal。**这在纯 REST 架构下是真实的越权风险。**

处理：清理搬到 `afterCompletion`（保证全路径执行）；`ResponseBodyAspect` 中针对 `@PostMapping` 的 `RequestContext.init/removeAll` 一并收敛到拦截器，不再两处管理同一个 ThreadLocal。

---

## 6. 部署与环境约束

### 6.1 微信合法域名规则（已核实官方文档）

| 官方规则 | 影响 |
|---|---|
| 域名只支持 https / wss | 正式版必须 https |
| **不能使用 IP 地址（小程序局域网 IP 除外）或 localhost** | `43.136.176.163` 这类公网 IP 永远无法填入 request 合法域名 |
| 域名必须经 ICP 备案 | 唯一真正的发布门槛 |
| **可以配置端口**，如 `https://myserver.com:8080` | 不需要迁就 443 |
| 后台域名不带端口时，请求 URL 连 `:443` 都不能写 | 约定：将来 URL 一律不带端口 |
| 开发者工具"不校验合法域名"、真机"打开调试"可绕过，**仅对开发者本人有效** | 当前开发/自测/体验版阶段完全够用 |

**关键结论：这堵墙与本次改造无关。** 老 Cookie 方案撞的是同一堵墙（且更糟：`Set-Cookie` 在 `http://` + IP 下无 `Secure`，上 https 后还需 `SameSite=None`）。本次改造可 100% 在现有 `http://43.136.176.163:8080` 环境做完并测完。

### 6.2 后端不加任何环境绑定

`CorsInterceptor` 的 `Access-Control-Allow-Origin: *` 原样保留，**后端对来源完全不做域名/IP 白名单校验**。当前 jar 包在 IP+http 与 域名+https 下均可运行，将来上域名时**后端一行不改**，只需：前端 `BASE_URL` 常量改一处 + 微信公众平台填一次合法域名。

### 6.3 正式发布路径（当前不做，仅留路线）

买域名（首年数十元）→ 云厂商走 ICP 备案（免费，审核 1~3 周）→ A 记录指向服务器 IP → Nginx 443 反代到 8080 → 证书用云厂商免费 DV 或 Let's Encrypt（免费自动续期）。真实成本是"等备案的 2~3 周"，不是钱也不是工作量。

备选：微信云托管 `callContainer` 免配置通讯域名（需迁移部署方式，非必要不走）。

**安全提示**：`http://` 明文通道下 token 与密码均可被同网段窃听，开发阶段可接受，**正式上线必须 https**，此为固有要求，无例外路径。

---

## 7. 验收清单

后端（本地已用 curl 跑完，服务以 `--custom-config.token.expire-seconds=60 --custom-config.cache.renew-threshold-ratio=0.5` 启动做短窗口验证，**靠命令行参数覆盖，没改任何代码开关**）：

| # | 项 | 结果 |
|---|---|---|
| 1 | `login` → 200，`data` 为 32 位 UUID；**响应头无 `Set-Cookie`** | ✅ |
| 2 | 带 `token` 头调 `current` → 200 + `id` | ✅ |
| 3 | 删 `token` 头 / 乱填 token 调 `current` → **401** + `{code:80008998}`，body 仍是 `ResultModel`（不是 `/error` 形状） | ✅ |
| 4 | **乱填 token 调游客接口 `/article-rest/{id}` → 仍 200 正常数据，未被污染成 401** ← 5.1 回归 | ✅ |
| 5 | `@IsLogin` 接口未登录 → 401 | ✅（`/user-rest/follow/1`） |
| 6 | 普通用户调 admin 接口 → 403 | ⚪ 未验证：瘦身删掉页面控制器后，**当前没有可达的 admin REST 入口**，403 映射作为能力保留 |
| 7 | `logout` → 200，随后原 token 调 `current` → 401；无 token 调 `logout` → 幂等 200 | ✅ |
| 8 | 重复邮箱注册 → `80007986`（与登录密码错误的 `80007992` 不再同码） | ✅ |
| 9 | Query 参数 `?token=` 通道可用 | ✅ |
| 10 | **滑动续期**：每 40 秒调一次，`current` 持续 200；`forum_cache` 里该行 `expire` 依次为 …507 / …547 / …587（每次 +40s，确实在滚动）；停止 80 秒 → 401，条目被 5 秒清扫任务删干净 | ✅ |
| 11 | `forum_cache` 中 `USER_LOGIN_TOKEN` 行数不单调增长（登出后只剩改造前已存在的 uid=5 会话 2 行，无测试残留） | ✅ |
| 12 | 一批 401 后日志不刷屏：会话失效只打一行 `session invalid. api=…#info. code=80007988. cost=0ms`，不再转储完整参数与结果 | ✅ |
| 13 | 被丢弃的注册 token 调 `current` → 401（单会话下 `deleteLoginUser` 已把它连带清掉，无幽灵会话） | ✅ |

小程序（待前端接入）：

14. 登录成功 → storage 有 token → 首页列表正常
15. **完全杀掉小程序后重新进入 → 直接是登录态** ← 本次改造的核心指标，Cookie 方案做不到
16. 手工把 storage 中 token 改成乱码 → 一进 App 就被引导回登录页（`onShow` 探活生效）
17. 首页并发 5 个请求全部 401 → **只跳一次登录页、只弹一次 toast**（并发锁生效）
18. 头像上传成功且返回值可正常解析（`JSON.parse(res.data)`）

---

## 8. 实施顺序（已全部完成）

1. **第 1 步（地基）**✅：`WebUtil.resolveToken` + `GlobalViewInterceptor` 统一注入 + 清理搬到 `afterCompletion` + 删除 19 处 Controller 样板 + 删除 `GetMappingAspect`/`ViewException`/`ResponseBodyAspect`。
2. **第 2 步（会话语义）**✅：`CacheService.renew` + `DbCacheServiceImpl` 实现 + `LoginUserAspect` 两条 key 成对续期 + `AbstractLoginManager` TTL 配置化 + 两个配置项。
3. **第 3 步（失效信号）**✅：`TokenResultStatusAdvice`（401/403）+ 切面日志降级 + `ErrorCodeEn` 重码修正。
4. **第 4 步（收尾）**✅：切断 Cookie 下发与读取、新增 `/logout`、`/current` 改走登录上下文、`CorsInterceptor` 清理。
5. 验证：`mvn -DskipTests package` 通过 → 本地起 jar 跑完第 7 节 1–13 项 → 测试用户与 `forum_opt_log` 残留已清库。

---

## 9. Postman 联调脚本（只配一次）

Cookie jar 已随本次改造失效（后端不再下发 `Set-Cookie`），Postman 改用 Collection 级脚本，**对集合内所有请求（含子文件夹与以后新增的）自动生效，不用逐个配**。

把下面这段贴到 **Collection → Pre-request Script**（不是某个 Request 的）：

```javascript
// 除登录/注册本身以外，所有请求自动带上 token
const url = pm.request.url.toString();
if (!/\/user-rest\/(login|register)$/.test(url)) {
    const token = pm.collectionVariables.get('token');
    if (token) {
        pm.request.headers.upsert({ key: 'token', value: token });
    }
}
```

再在同一路径的 **Tests** 里加一段（登录成功自动存，会话失效自动重登并重跑一次）：

```javascript
const body = pm.response.json();

if (pm.request.url.toString().includes('/user-rest/login') && body.success) {
    pm.collectionVariables.set('token', body.data);
}

// 401：集合里存的 token 已失效，自动重登一次并重跑当前请求
if (pm.response.code === 401 && !pm.request._retriedForExpiredToken) {
    pm.request._retriedForExpiredToken = true;
    pm.sendRequest({
        url: pm.variables.replaceIn('{{baseUrl}}/user-rest/login'),
        method: 'POST',
        header: { 'Content-Type': 'application/json' },
        body: { mode: 'raw', raw: '{"email":"' + pm.collectionVariables.get('email') + '","password":"' + pm.collectionVariables.get('password') + '"}' }
    }, (err, res) => {
        if (!err) {
            pm.collectionVariables.set('token', res.json().data);
            pm.sendRequest(pm.request, (e, r) => {
                if (!e) { pm.test('重登后重跑成功', () => pm.expect(r.code).to.be.oneOf([200, 401])); }
            });
        }
    });
}
```

集合变量配 `baseUrl` / `email` / `password` 三个值即可。**日常 30+ 个请求一个都不用手动填 token，token 变了也不用管** —— 过期了就发请求，脚本自己重登。

---

## 10. 附加修复与已知决策

### 10.1 明文密码写日志 + 写库（已修复，2026-11-01）

现象：`ExceptionHandlerAndLogAspect` 把 API 入参整体序列化落 stdout（含注册/登录/改密的**明文密码**）；`AbstractLoginManager.login()` 又把 `JSON.toJSONString(baseLoginRequest)` 存进 `forum_opt_log.content`（登录/注册审计行里也是明文）。

修复方式（序列化出口统一掩码，请求类保持零依赖不动）：

| 出口 | 位置 | 处理 |
|---|---|---|
| stdout 接口日志 | `StringUtil.toJSONString` 的 ValueFilter | 字段名命中 `password`/`oldPassword`/`newPassword` 一律输出 `***`（该工具仅用于日志序列化，全项目 4 个调用点都在切面里） |
| `forum_opt_log` 登录/注册审计 | `AbstractLoginManager.login()` | `JSON.toJSONString` → `StringUtil.toJSONString` |
| `forum_opt_log` 注册记录 | `OptLog.createUserRegisterRecordLog()` | 同上（此处本是加盐 MD5，顺手一并掩码） |
| 登录缓存 JSON | `AbstractLoginManager.cacheLoginUser()` | **保持原样、绝不能掩码**：`updatePwd` 校验旧密码依赖反序列化出的 `user.password` |

实测验收（本地起服务）：注册/登录/改密全链路后，stdout 中密码金丝串 0 命中，切面 params 与 `forum_opt_log` 三行 content 均为 `"password":"***"`；改密后新密码登录 200、`/user-rest/current` 200、旧密码登录 80007992，功能无回归。

### 10.2 上传接口先传文件、后校登录（决策：不修）

`/user-rest/update-headimg`、`/file-rest/upload` 在 controller 层就把图传给七牛，`@IsLogin` 要到 `FileManager`/`UserManager` 才生效，未登录者可直连接口往云存储写文件。

**拍板结论**：一般用户经小程序前端天然拦截（未登录无入口）；懂技术直连接口滥用属可接受风险，论坛体量下损伤有限，不做后端改造。若后续七牛账单异常再回来收敛。

### 10.3 老会话缓存行无 `seconds` 字段（预期行为，无需处理）

改造前写入的 `forum_cache` 行反序列化后 `seconds == null` → `renew` 直接 no-op，行为上是"旧 7 天绝对过期不变"；用户下次登录就自然升级成滑动窗口。

