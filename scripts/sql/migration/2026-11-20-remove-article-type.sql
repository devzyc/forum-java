-- 2026-11-20: 彻底移除"文章分类"(ArticleType)体系
-- 种草社区发帖无分类概念，正文只留 htmlContent，分类字段/表全部下线。
-- 执行前请确认生产库无需保留 forum_posts.type_id 数据（本次为不可逆 DROP）。

ALTER TABLE `forum_posts` DROP COLUMN `type_id`;

DROP TABLE IF EXISTS `forum_article_type`;
