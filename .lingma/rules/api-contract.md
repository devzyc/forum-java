---
trigger: glob
glob:
  - "forum-portal/src/main/java/pub/developers/forum/portal/controller/**/*.java"
  - "forum-api/src/main/java/pub/developers/forum/api/request/tag/**/*.java"
  - "forum-api/src/main/java/pub/developers/forum/api/response/tag/**/*.java"
---
<!-- 接口路径约定与标签规则：REST 命名、已知映射、标签防滥用；Controller + Tag API 生效 -->

# 接口契约与标签规则

## 接口路径约定

- 新接口路径不加 `-rest` 后缀；存量 `/article-rest`、`/user-rest` 前缀保持不改名，避免影响小程序端已发布版本。
- 已知映射：文章详情为 `POST /article-rest/{id}`（不是 GET）；评论列表为 `GET /article-rest/comments/{id}`。
- 接口清单以代码为准，核对方式是在 `forum-portal` 下检索 `@RequestMapping`、`@GetMapping`、`@PostMapping`。
- 给的接口示例必须与 Controller 注解一致，尤其是 HTTP 方法，不要凭命名猜动词。

## 标签系统

- 标签接口返回值带 `isHot` 字段标识热门标签，前端展示依赖该字段，不要删除或改名。
- 废除独立接口 `POST /tag/create`，新标签在发布文章时自动创建并绑定——保证每个新标签诞生时至少关联当前这篇文章，从源头杜绝空标签。
- 配套限制：单篇最多 20 个标签，防止批量滥用。
- 适用边界：当前用于 UGC 社区内容管理；若将来需要纯标签库或词典能力，需重新评估。

## 发文数据模型

- 发文不再区分 `typeId` 与 `ContentType`，正文只保留 `html_content`。
- 写接口或查询时不要再引入类型字段；历史 Markdown 兼容逻辑应删除而非保留。
