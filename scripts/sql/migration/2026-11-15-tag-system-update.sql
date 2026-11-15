-- ================================================
-- 论坛标签系统更新脚本 - 生产环境版
-- 版本：v1.0 (2026-11-15)
-- 功能：清空旧的技术类标签，替换为小程序购物生活化的 12 个内置标签
-- 执行方式：mysql -u root -p'Abc@1234' forum-java < update_tags.sql
-- ================================================

USE `forum-java`;

-- 开始事务
START TRANSACTION;

-- 备份当前数据（可选）
-- SELECT * FROM forum_tag INTO OUTFILE '/tmp/forum_tag_backup_before_update.csv';

-- 删除所有旧标签
DELETE FROM `forum_tag`;

-- 插入新的 12 个内置标签（面向小程序用户的生活化分类）
INSERT INTO `forum_tag` (`id`, `audit_state`, `group_name`, `name`, `description`, `ref_count`, `creator_id`, `deleted`, `gmt_create`, `gmt_modified`) VALUES
(188, 'PASS', '购物分享', '好物推荐', '分享你发现的好东西、实用商品推荐', 0, 2, 0, NOW(), NOW()),
(189, 'PASS', '开箱测评', '真实体验', '产品开箱、使用评测、性能分析', 0, 2, 0, NOW(), NOW()),
(190, 'PASS', '买家秀', '晒单分享', '购买后的实物展示、效果对比', 0, 2, 0, NOW(), NOW()),
(191, 'PASS', '求推荐', '咨询求助', '需要别人帮忙推荐产品或方案', 0, 2, 0, NOW(), NOW()),
(192, 'PASS', '避雷拔草', '负面警示', '踩坑经验、不推荐的产品、避坑指南', 0, 2, 0, NOW(), NOW()),
(193, 'PASS', '时尚穿搭', '服饰搭配', '服装搭配技巧、时尚风格展示', 0, 2, 0, NOW(), NOW()),
(194, 'PASS', '美妆护肤', '美容保养', '化妆品测评、护肤心得、美容技巧', 0, 2, 0, NOW(), NOW()),
(195, 'PASS', '家居日用', '家庭生活', '家居装饰、日用品推荐、收纳整理', 0, 2, 0, NOW(), NOW()),
(196, 'PASS', '数码家电', '电子设备', '手机电脑、智能设备、家用电器', 0, 2, 0, NOW(), NOW()),
(197, 'PASS', '美食零食', '食品饮品', '美食探店、零食推荐、饮料评测', 0, 2, 0, NOW(), NOW()),
(198, 'PASS', '运动户外', '户外运动', '运动装备、户外用品、健身器材', 0, 2, 0, NOW(), NOW()),
(199, 'PASS', '宠物用品', '爱宠必备', '猫狗用品、宠物食品、玩具配件', 0, 2, 0, NOW(), NOW());

-- 提交事务
COMMIT;

-- 验证结果
SELECT '✅ 标签更新完成！总数统计:' as info;
SELECT COUNT(*) AS total_tags FROM `forum_tag`;

SELECT '✨ 前 5 个标签预览:' as info;
SELECT id, group_name AS '分组', name AS '标签名' FROM `forum_tag` ORDER BY id LIMIT 5;
