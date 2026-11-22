-- 2026-11-21: 彻底移除"帖子分类"(PostsCategory)体系
-- category 枚举仅剩 ARTICLE 一个值，整条链路（写死→查自己→读回）为原始论坛项目多内容形态的遗留空壳，
-- 与种草社区业务无关。代码侧已删净 PostsCategoryEn/BasePosts.category/DAO/Mapper/响应字段，
-- 本脚本 DROP forum_posts.category 列完成下线（不可逆）。
-- 执行前请确认生产库该列无需保留：SELECT DISTINCT category FROM forum_posts;
-- 注意：category 为 NOT NULL 且无默认值，新代码 INSERT 已不含该列，
--       因此本脚本必须与新 JAR 同批上线（先停旧服 → 执行 DDL → 启新服）。
--       索引 idx_category_state_views 随列自动删除，无需单独 DROP INDEX。

ALTER TABLE `forum_posts` DROP COLUMN `category`;
