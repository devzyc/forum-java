BEGIN;
INSERT INTO `forum_user`(`id`, `email`, `nickname`, `password`, `role`, `state`, `sex`, `avatar`, `signature`, `last_login_time`, `is_delete`, `create_at`, `update_at`) VALUES (1, 'admin@developers.pub', '管理员-0', '711475695fde28fc68fb2547cd2cf2ef', 'ADMIN', 'ENABLE', 'UNKNOWN', '', '开发者客栈管理员', '2020-10-01 14:00:01', 0, '2020-10-01 08:00:01', '2020-10-01 08:00:01');
INSERT INTO `forum_user`(`id`, `email`, `nickname`, `password`, `role`, `state`, `sex`, `avatar`, `signature`, `last_login_time`, `is_delete`, `create_at`, `update_at`) VALUES (2, 'superadmin@developers.pub', '开发者客栈', '711475695fde28fc68fb2547cd2cf2ef', 'SUPER_ADMIN', 'ENABLE', 'UNKNOWN', '', '开发者客栈官方超级管理员', '2020-10-01 14:00:01', 0, '2020-10-01 08:00:01', '2020-10-01 08:00:01');
COMMIT;

-- ----------------------------
-- Records of forum_article_type
-- ----------------------------
BEGIN;
INSERT INTO `forum_article_type` VALUES (1, 'PASS', '活动', '官方活动', 0, 'OFFICIAL', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (2, 'PASS', '公告', '官方公告', 0, 'OFFICIAL', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (3, 'PASS', '前端', '前端', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (4, 'PASS', '后端', '后端', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (5, 'PASS', '小程序', '小程序', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (6, 'PASS', '公众号', '公众号', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (7, 'PASS', 'iOS', 'iOS类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (8, 'PASS', 'Android', 'Android类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (9, 'PASS', 'AI', 'AI 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (10, 'PASS', '云计算', '云计算 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (11, 'PASS', '游戏', '游戏 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (12, 'PASS', '安全', '安全 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (13, 'PASS', '金融科技', '金融科技 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (14, 'PASS', '区块链', '区块链 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (15, 'PASS', '开源', '开源 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (16, 'PASS', '工具', '工具 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (17, 'PASS', '程序员', '程序员 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (18, 'PASS', '行业', '行业 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
INSERT INTO `forum_article_type` VALUES (19, 'PASS', '其他', '其他 类别', 0, 'USER', 2, 0, '2020-11-10 12:03:01', '2020-11-10 12:03:01');
COMMIT;

-- ----------------------------
-- Records of forum_tag
-- ----------------------------
BEGIN;
INSERT INTO `forum_tag` VALUES (188, 'PASS', '购物分享', '好物推荐', '分享你发现的好东西、实用商品推荐', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (189, 'PASS', '开箱测评', '真实体验', '产品开箱、使用评测、性能分析', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (190, 'PASS', '买家秀', '晒单分享', '购买后的实物展示、效果对比', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (191, 'PASS', '求推荐', '咨询求助', '需要别人帮忙推荐产品或方案', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (192, 'PASS', '避雷拔草', '负面警示', '踩坑经验、不推荐的产品、避坑指南', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (193, 'PASS', '时尚穿搭', '服饰搭配', '服装搭配技巧、时尚风格展示', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (194, 'PASS', '美妆护肤', '美容保养', '化妆品测评、护肤心得、美容技巧', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (195, 'PASS', '家居日用', '家庭生活', '家居装饰、日用品推荐、收纳整理', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (196, 'PASS', '数码家电', '电子设备', '手机电脑、智能设备、家用电器', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (197, 'PASS', '美食零食', '食品饮品', '美食探店、零食推荐、饮料评测', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (198, 'PASS', '运动户外', '户外运动', '运动装备、户外用品、健身器材', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
INSERT INTO `forum_tag` VALUES (199, 'PASS', '宠物用品', '爱宠必备', '猫狗用品、宠物食品、玩具配件', 0, 2, 0, '2024-11-15 12:03:01', '2024-11-15 12:03:01');
COMMIT;

