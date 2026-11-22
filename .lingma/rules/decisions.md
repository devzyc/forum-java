---
trigger: glob
glob:
  - "forum-portal/src/main/java/pub/developers/forum/portal/controller/**/*.java"
---
<!-- 后端瘦身形态与分层原则：仅服务小程序的纯 REST 后端；Portal Controller 中生效 -->

# 后端形态与分层原则

## 当前形态（瘦身后）

- 纯 REST 后端，只服务小程序端。
- Thymeleaf 页面、GitHub OAuth、FAQ 模块、后台管理入口均已移除，不要再按老形态写代码。
- 任何涉及页面渲染、OAuth 登录流程、后台管理的需求都不再在当前仓库范围内，先确认需求是否还有效。
- `forum-vue` 管理后台前端已移除，仓库内没有可运行前端。

## 分层与依赖方向（硬约束）

- 严格单向调用链：
  `forum-portal`（Controller）→ `forum-api`（接口定义）→ `forum-facade`（实现）→ `forum-app`（业务）→ `forum-domain`（实体）→ `forum-infrastructure`（持久化）。
- Portal 层不得直接依赖 `forum-app` 或 `forum-facade` 的内部类，不得跨层调用（例如 Controller 里直接调 `UserManager`）。
- 写任何后端代码前，先判断这次改动应该落在哪个模块，再动手；不要为了省事在 Controller 里堆业务逻辑。
- 业务逻辑放 `forum-app`，接口契约放 `forum-api`，实现组装放 `forum-facade`，SQL 与 Mapper 只放 `forum-infrastructure`。
