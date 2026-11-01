# 小程序登录态改造定稿方案：Cookie → Token Header

| 项 | 内容 |
|---|---|
| 文档状态 | **定稿，待实施**（本文件为后端与小程序端的单一对齐依据） |
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
| Controller 样板 | 每方法手写 `request.setAttribute("token", cookieGetSid(request))`，全项目 **20 处** | 全部删除，由拦截器统一注入一次 |
| 登出 | **接口不存在**（`cookieDelSid` 定义了但从未被调用） | 新增 `POST /user-rest/logout` |
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
{"code":80007988,"message":"token 无效","success":false,"data":null}
```

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
| 8 | `forum-domain/.../service/CacheService.java` + `forum-infrastructure/.../cache/DbCacheServiceImpl.java` | 新增 `renew(bizType, key, seconds)`，阈值判断与"不复活已过期"逻辑全部内置于 impl | 核心 |
| 9 | `forum-portal/.../controller/rest/UserRestController.java` | 删除本类 8 行样板；删除 2 处 `cookieAddSid` 调用；**新增 `/logout`** | 核心 + 清理 |
| 10 | 其余 7 个 `*RestController` | 删除 `request.setAttribute(...)` 样板（合计约 12 行） | 清理 |
| 11 | `forum-portal/.../support/CorsInterceptor.java` | 删除注释掉的 `Allow-Credentials` 行；`Allow-Headers: Content-Type,token` 已就绪，不动 | 清理 |
| 12 | `forum-portal/.../support/GetMappingAspect.java` + `ViewException.java` | 整体删除（页面时代遗产，见 5.2） | 清理 |
| 13 | `forum-starter/src/main/resources/application.properties` | 新增 `custom-config.token.expire-seconds=86400`、`custom-config.token.renew-threshold-ratio=0.5` | 配置 |
| 14 | `forum-common/.../enums/ErrorCodeEn.java` | `USER_REGISTER_EMAIL_IS_EXIST` 从 `7992` 挪至 `7990`（消除与 `USER_LOGIN_PWD_ERROR` 的重码） | 修正 |

### 4.1 滑动窗口设计（"天天用就一直不过期"）

语义：**距最后一次成功使用满 1 天才失效。**

配置：

```properties
custom-config.token.expire-seconds=86400
custom-config.token.renew-threshold-ratio=0.5
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

后端（Postman，配好 Collection 级 Pre-request Script 后）：

1. `login` → 200，`data` 为 32 位 UUID；**响应头中不再有 `Set-Cookie`**
2. 带 `token` 头调 `current` → 200 + `id`
3. **删除** `token` 头调 `current` → **401** + `code:80007988`，且 body 仍是 `ResultModel` 结构（不是 `/error` 形状）
4. 带乱填的 token 调 `/article-rest/{id}`（游客接口）→ **仍返回 200 正常数据，绝不 401** ← 5.1 的回归测试
5. 有 `@IsLogin` 的接口：未登录 → 401；普通用户调 admin 接口 → **403 而非 401**
6. `logout` → 200，随后用原 token 调 `current` → 401
7. **滑动续期验证**：临时设 `expire-seconds=60`、`renew-threshold-ratio=0.5`；登录后每 40 秒调一次接口 → 持续 200 不失效；停止 70 秒 → 401。验完改回 `86400`
8. `forum_cache` 表中 `USER_LOGIN_TOKEN` 行数不随时间单调增长 ← 4.1 铁律 1 的验证
9. 触发一批 401 请求后，`/opt/logs/forum-java` 无失败日志刷屏

小程序：

10. 登录成功 → storage 有 token → 首页列表正常
11. **完全杀掉小程序后重新进入 → 直接是登录态** ← 本次改造的核心指标，Cookie 方案做不到
12. 手工把 storage 中 token 改成乱码 → 一进 App 就被引导回登录页（`onShow` 探活生效）
13. 首页并发 5 个请求全部 401 → **只跳一次登录页、只弹一次 toast**（并发锁生效）
14. 头像上传成功且返回值可正常解析（`JSON.parse(res.data)`）
15. 注册成功 → 不写入 storage → 强制登录后才可用；用被丢弃的那个 token 调 `current` 应返回 401

---

## 8. 实施顺序建议

1. **第 1 步（地基）**：`WebUtil.resolveToken` + `GlobalViewInterceptor` 统一注入 + 清理搬到 `afterCompletion` + 删除 20 处样板 + 删除 `GetMappingAspect`/`ViewException`。此时 Cookie 通道仍可用，可先回归一轮。
2. **第 2 步（会话语义）**：`CacheService.renew` + `DbCacheServiceImpl` 实现 + `LoginUserAspect` 续期 + `AbstractLoginManager` TTL 配置化 + 两个配置项。
3. **第 3 步（失效信号）**：`TokenResultStatusAdvice`（401/403）+ 切面日志降级 + `ErrorCodeEn` 重码修正。
4. **第 4 步（收尾）**：切断 Cookie 下发与读取、新增 `/logout`、`CorsInterceptor` 清理。
5. 每步完成后跑第 7 节对应项，全部通过再进下一步；全部改动保持 Git 工作区未提交状态直至验收通过。
