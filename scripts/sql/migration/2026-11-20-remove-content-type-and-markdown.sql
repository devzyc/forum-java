-- 移除 contentType 与 markdownContent：种草社区只有 HTML 一种格式，无需内容类型标记
ALTER TABLE forum_posts DROP COLUMN `content_type`;
ALTER TABLE forum_posts DROP COLUMN `markdown_content`;
