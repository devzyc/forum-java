# 生产环境快速重置方案（大刀阔斧版）

| 项 | 内容 |
|---|---|
| **适用场景** | 项目早期开发阶段，无重要 UGC 数据 |
| **操作方式** | 直接清空数据库并重建初始数据 |
| **预计耗时** | <5 分钟 |
| **风险等级** | ⭐⭐ (低) - 仅需保留用户账号密码即可恢复 |
| **回滚时间** | 随时可以重新执行脚本恢复旧版本标签

---

## 🚀 一键重置流程

### Step 1: SSH 登录服务器

```bash
# 使用 PEM 密钥认证登录
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163
```

### Step 2: 备份现有账户信息（可选但推荐）

```bash
mysql -u root -p'Abc@1234' forum-java << 'EOF'
-- 只导出用户表结构 + 数据（不含文章/评论等 UGC）
mysqldump -u root -p'Abc@1234' --no-create-info --skip-extended-insert \
  forum-java forum_user > /tmp/user_backup_$(date +%Y%m%d).sql
  
SELECT '✅ 用户数据已备份到 /tmp/' as info;
EOF
```

> 💡 **说明**: 这个备份只是为了防止误删，实际不需要真用，因为我们知道有哪些测试账号（zyc3@xx.com）。

### Step 3: 上传 SQL 脚本并执行

#### 【在你的 Mac 上执行】
```bash
cd /Users/zyc/IdeaProjects/forum-java/scripts/sql/migration

# 上传标签更新脚本到生产服务器
scp -i ~/.ssh/ssh_key_1.pem 2026-11-15-tag-system-update.sql root@43.136.176.163:/tmp/
```

#### 【在生产服务器上执行】
```bash
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163

# 进入 MySQL 执行重置脚本
mysql -u root -p'Abc@1234' forum-java << 'EOF'
-- ================================================
-- 论坛系统完全重置脚本 v1.0
-- ================================================

USE `forum-java`;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空所有业务表（除了用户表外都清）
TRUNCATE TABLE forum_posts;
TRUNCATE TABLE forum_article;
TRUNCATE TABLE forum_comment;
TRUNCATE TABLE forum_tag_posts_mapping;
TRUNCATE TABLE forum_message;
TRUNCATE TABLE forum_follower;
TRUNCATE TABLE forum_like;
TRUNCATE TABLE forum_collect;
TRUNCATE TABLE forum_admin_account;
TRUNCATE TABLE forum_config;
TRUNCATE TABLE forum_cache;
TRUNCATE TABLE forum_opt_log;

-- 2. 更新标签系统
DELETE FROM forum_tag;
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

-- 3. 验证结果
SELECT '🎉 数据库重置完成！' as status;
SELECT COUNT(*) AS '总标签数' FROM forum_tag;
SELECT id, group_name, name FROM forum_tag ORDER BY id LIMIT 5;

SET FOREIGN_KEY_CHECKS = 1;
QUIT;
EOF
```

### Step 4: 重启应用服务

```bash
systemctl restart forum-java

# 检查状态
systemctl status forum-java
journalctl -u forum-java -n 50 --since "5 minutes ago"

# 验证服务是否正常运行
curl -s http://localhost:8080/tag-rest/all | jq '.data | length'
# 预期输出：12
```

---

## 🔄 如果需要恢复到旧版本标签

由于我们没有删除 data.sql 文件，可以随时从 Git 历史中恢复：

```bash
cd /path/to/project
git log --oneline forum-starter/src/main/resources/data.sql | head -1
git checkout <commit-hash> -- forum-starter/src/main/resources/data.sql

# 然后提取旧的 INSERT 语句重新导入
```

或者更简单的方式：创建一个逆向脚本 `scripts/sql/revert-old-tags.sql`

---

## 📝 后续工作建议

### ✅ 已完成的工作

1. **部署自动化工具集** (`scripts/deploy/`)：
   - `.env.production` - 环境变量配置文件（含 IP、密码等敏感信息）
   - `prod-deploy.sh` - 一键部署主脚本（支持 --skip-build/--data-migration）
   - `README.md` - 详细使用说明和故障排查指南

2. **快速参考手册** (`docs/deployment/quick-reference.md`)：
   - 一分钟速查表
   - SSH/MySQL/Systemd常用命令集合
   - 故障排查流程图
   - 回滚操作指南

3. **数据迁移工具优化** (`scripts/data-migration/`)：
   - `export-local-data.sh` - 导出路径已更新为生产服务器 IP
   - `import-prod-data.sh` - 测试账号改为 zyc3@xx.com

4. **API 测试脚本配置** (`scripts/api-test/smoke-test.sh`)：
   - BASE_URL 默认指向 http://43.136.176.163:8080
   - TEST_EMAIL/TEST_PASSWORD 使用真实测试账号

---

### 🔧 待手动调整的项目

1. **PEM 密钥权限检查**：
   ```bash
   chmod 400 ~/.ssh/ssh_key_1.pem
   ls -l ~/.ssh/ssh_key_1.pem  # 确认输出为 -r--------
   ```

2. **数据库连接测试**（首次部署前）：
   ```bash
   mysqladmin -h 43.136.176.163 -u root -p'Abc@1234' ping
   ```

1. **建立规范的目录结构**：
   ```
   scripts/
   ├── sql/
   │   ├── migration/          # 增量迁移脚本（带日期前缀）
   │   │   └── YYYY-MM-DD-xxx.sql
   │   ├── reset/              # 整库重置脚本
   │   │   └── full-init.sql
   │   └── backup/             # 备份策略脚本
   │       └── daily-backup.sh
   ├── api-test/               # API 测试相关
   │   ├── smoke-test.sh       # 冒烟测试入口
   │   ├── modules/            # 可复用模块
   │   │   ├── auth.sh         # Token 获取函数
   │   │   └── utils.sh        # JSON 解析工具
   │   └── cases/              # 具体测试用例
   │       ├── tag-check.sh
   │       └── article-check.sh
   └── deploy/                 # 部署脚本
       ├── local-build.sh      # 本地打包
       ├── prod-deploy.sh      # 生产部署
       └── rollback.sh         # 快速回滚
   ```

2. **编写自动化脚本封装以上流程**：
   ```bash
   #!/bin/bash
   # scripts/deploy/prod-reset-and-restart.sh
   
   SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
   SQL_FILE="$SOURCE_DIR/scripts/sql/reset/full-database-reset.sql"
   
   echo "Starting production reset..."
   scp "$SQL_FILE" root@$PROD_SERVER:/tmp/
   ssh root@$PROD_SERVER "mysql -u root -p'$DB_PASS' forum-java < /tmp/$(basename $SQL_FILE)"
   ssh root@$PROD_SERVER "systemctl restart forum-java"
   echo "Done!"
   ```

3. **完善 CI/CD 流水线**：每次修改 data.sql 自动触发部署验证

---

## ❌ 为什么放弃保守派做法？

| 问题 | 保守派方案 | 激进方案 |
|------|-----------|---------|
| 实施复杂度 | 高（需事务控制、逐步验证） | 低（一条命令搞定） |
| 出错概率 | 中等（部分失败可能导致脏数据） | 极低（全量覆盖要么成功要么失败） |
| 回滚难度 | 困难（需要还原特定记录） | 简单（再跑一次重置脚本） |
| 维护成本 | 高（每个小改动都要写迁移逻辑） | 低（统一用 init 脚本管理） |
| 适合阶段 | 成熟稳定期（大量 UGC 数据） | 早期探索期（少量测试数据） |

**结论**：当前项目明显属于后者，没必要背弃轻量化的优势！
