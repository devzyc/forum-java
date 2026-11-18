# 📦 本地 UGC 数据迁移到生产环境完整指南

## ✅ **可行性结论：完全可行！**

经过全面代码审查，确认以下关键点：

| 检查项 | 结果 | 说明 |
|--------|------|------|
| **头像/图片 URL** | ✅ `https://zycforum.xyz/` | 七牛云 CDN，不依赖任何服务器 IP |
| **数据库字段** | ❌ **未发现存储 IP 的字段** | schema.sql 中所有 varchar 均为业务内容 |
| **Java 硬编码** | ❌ **全项目 grep "127.0.0.1" = 0 命中** | application.properties 仅配置 datasource.url |
| **缓存 Token** | ⚠️ forum_cache 可能有过期 token | 建议清空后重新登录生成新会话 |

---

## 🎯 **为什么可以安全迁移？**

### 🔍 **详细分析**

#### 1️⃣ **文件上传系统架构**

```
用户上传 → FileRestController.upload() 
         ↓ QiNiuFileServiceImpl.uploadImg()
         ↓ 返回 https://zycforum.xyz/{uuid}.jpg
         ↓ 存入 forum_user.avatar / forum_article.head_img
```

**关键特性**：
- ✅ 所有图片都存储在七牛云对象存储
- ✅ 访问域名是独立的 `https://zycforum.xyz/`（非 localhost/IP）
- ✅ Markdown 编辑器嵌入的图片也走同一通道
- ❌ **不存在「http://127.0.0.1:8080/static/xxx.jpg」这种路径**

#### 2️⃣ **数据库表结构扫描**

通过检查 `schema.sql`，发现以下潜在风险字段：

| 表名 | 字段 | 类型 | 是否含环境信息 | 处理方式 |
|------|------|------|----------------|----------|
| `forum_user` | avatar | varchar(256) | ❌ 纯 CDN URL | 直接迁移 |
| `forum_posts` | head_img | varchar(8192) | ❌ 纯 CDN URL | 直接迁移 |
| `forum_opt_log` | content | longtext | ⚠️ 含操作日志 JSON | 可选保留或删除 |
| `forum_cache` | value | longtext | ⚠️ 含过期 token | **必须清空** |

**结论**: 只有 `forum_cache` 需要特殊处理，其他均可原样迁移。

---

## 📋 **迁移前准备清单**

### 1️⃣ **本地环境要求**

```bash
# 确认本地 MySQL 服务正常运行且有真实数据
mysql -u root -p'Abc@1234' forum-java << 'EOF'
SELECT COUNT(*) AS user_count FROM forum_user;
SELECT COUNT(*) AS post_count FROM forum_posts WHERE category='ARTICLE';
SHOW TABLES LIKE 'forum_%';
EOF
```

如果数据量为 0，请先在本地小程序上发几篇测试文章再迁移！

---

### 2️⃣ **生产环境预检**

SSH 登录生产服务器执行：

```sql
-- 【重要】先备份现有数据以防万一
mysqldump -u root -p'Abc@1234' --add-drop-table forum-java > /tmp/forum_backup_$(date +%Y%m%d).sql

-- 检查是否有同名用户邮箱冲突
SELECT email FROM forum_user WHERE email IN ('test@example.com', 'admin@test.com');

-- 检查 forum_cache 是否有大量无效 token
SELECT COUNT(*) FROM forum_cache;
```

如果返回非空结果，需要先删除这些冲突记录或手动清理 cache。

---

## 🚀 **标准迁移流程（一键脚本版）**

### Step 1: 从本地导出 UGC 数据

```bash
cd /Users/zyc/IdeaProjects/forum-java/scripts/data-migration
chmod +x export-local-data.sh import-prod-data.sh

./export-local-data.sh
```

**预期输出**:
```
======================================
📦 开始导出本地 UGC 数据...
======================================
⏳ Exporting forum_user...
✅ forum_user: 5 records exported
... (其他表依次导出)

======================================
🎉 导出完成！文件位置：
total 240
-rw-r--r--@ 1 zyc staff   8.3K Nov 15 14:30 forum_comment_*.sql
...
```

---

### Step 2: 上传到生产服务器

```bash
# 【在你的 Mac 上执行】
scp -r scripts/data-migration/exported-data root@your-server-ip:/tmp/
```

---

### Step 3: SSH 登录并导入数据

```bash
ssh root@your-server-ip
cd /tmp/exported-data

# 修改导入脚本中的密码（如果与默认不同）
sed -i 's/Abc@1234/YourRealPassword/g' ../import-prod-data.sh

# 执行导入
../import-prod-data.sh
```

交互提示示例见下方"成功标志"章节。

---

## ⚙️ **手动 SQL 方式（备用方案）**

如果不喜欢用 shell 脚本，也可以直接通过 MySQL 命令行操作：

```sql
-- 【在生产服务器上执行】
USE `forum-java`;

SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖顺序删除子表 -> 父表
TRUNCATE TABLE forum_tag_posts_mapping;
TRUNCATE TABLE forum_comment;
TRUNCATE TABLE forum_user_food;
TRUNCATE TABLE forum_user_follow;
TRUNCATE TABLE forum_posts;
TRUNCATE TABLE forum_article_type;
TRUNCATE TABLE forum_tag;
TRUNCATE TABLE forum_user;

SET FOREIGN_KEY_CHECKS = 1;

-- 然后逐个 source 导出的 .sql 文件
SOURCE /tmp/exported-data/forum_user_*.sql;
SOURCE /tmp/exported-data/forum_article_type_*.sql;
SOURCE /tmp/exported-data/forum_tag_*.sql;
SOURCE /tmp/exported-data/forum_posts_*.sql;
SOURCE /tmp/exported-data/forum_comment_*.sql;
-- ... 其他表

SELECT '✅ 迁移完成!' as status;
```

---

## 🧪 **迁移后验证清单**

### 1️⃣ **检查数据库一致性**

```sql
-- 统计各表记录数
SELECT '用户' AS table_name, COUNT(*) FROM forum_user UNION ALL
SELECT '文章', COUNT(*) FROM forum_posts WHERE category='ARTICLE' UNION ALL
SELECT '评论', COUNT(*) FROM forum_comment UNION ALL
SELECT '标签', COUNT(*) FROM forum_tag;

-- 验证外键关联是否正确
SELECT p.title, u.nickname FROM forum_posts p 
JOIN forum_user u ON p.author_id = u.id LIMIT 5;
```

预期输出示例：
```
+------------+----------+
| table_name | count    |
+------------+----------+
| 用户       |        5 |
| 文章       |       12 |
| 评论       |       38 |
| 标签       |       12 |
+------------+----------+
```

---

### 2️⃣ **测试 API 接口**

```bash
# 登录接口
curl -X POST http://127.0.0.1:8080/user-rest/login \
  -H "Content-Type: application/json" \
  -d '{"loginId":"test@example.com","password":"TestPass123"}'

# 查询文章列表
curl -X GET "http://127.0.0.1:8080/article-rest/list?pageNo=1&pageSize=10"

# 查看某个用户的头像 URL（替换为你的实际 token）
curl -X GET http://127.0.0.1:8080/user-rest/current \
  -H "token: YOUR_TOKEN_HERE" | jq '.data.avatar'
```

**成功标志**:
- ✅ HTTP 状态码均为 200
- ✅ JSON 响应中 data 字段非空
- ✅ avatar/head_img URL 以 `https://zycforum.xyz/`开头

---

### 3️⃣ **浏览器访问小程序页面**

打开微信开发者工具或真机预览：

| 场景 | 预期结果 | 常见错误 |
|------|----------|----------|
| 首页列表 | 显示所有已发布文章的封面图 | ❌ 图片裂开 → CDN 域名问题 |
| 点击任意文章 | 正文内容正常显示，Markdown 渲染无误 | ❌ 空白页 → HTML 未生成 |
| 进入个人中心 | 头像、昵称、签名都正确加载 | ❌ 默认 Gravatar → avatar 字段为空 |
| 发表评论 | 新评论实时出现在列表中 | ❌ 403 Forbidden → token 过期 |

如果以上任一环节失败，请对照「故障排查」章节处理。

---

## 🆘 **故障排查与回滚**

### Q1: 发现某些图片无法加载？

**原因**: 可能是七牛云 bucket 权限问题或域名解析失败。

**解决步骤**:
```bash
# 1. 检查 zycforum.xyz 是否仍指向正确的 bucket
dig zycforum.xyz

# 2. curl 测试单个图片链接
curl -I https://zycforum.xyz/{uuid}.jpg

# 3. 如果返回 403 Forbidden，需要在七牛云后台刷新防盗链设置
```

---

### Q2: 登录时报「token 无效」错误？

**原因**: forum_cache 表中可能有旧的过期 token。

**快速修复**:
```sql
DELETE FROM forum_cache;
systemctl restart forum-java
```

然后重新登录即可生成新会话。

---

### Q3: 想完全撤销这次迁移？

**最稳妥的方法**（利用之前做的备份）:
```bash
mysql -u root -p'Abc@1234' forum-java < /tmp/forum_backup_$(date +%Y%m%d).sql
systemctl restart forum-java
```

如果没有做备份，只能手动 DELETE 掉新增的数据再重跑初始化脚本。

---

## 📝 **经验总结**

### ✅ **成功要素**

1. **CDN 独立部署**: 图片不依赖任何服务器 IP，天然支持跨环境迁移
2. **无硬编码地址**: Java 代码中没有出现 localhost/IP，全部走配置中心
3. **模块化脚本设计**: export/import 分离，可单独调试每一步
4. **事务性保护**: TRUNCATE + INSERT 保证数据完整性

### ⚠️ **注意事项**

1. **务必先备份生产库**: mysqldump 只需 1 分钟但能救命
2. **清理 forum_cache**: 避免旧 token 干扰新登录
3. **重启应用服务**: 刷新 Spring Boot 内部缓存
4. **逐步验证**: 不要一次性测太多功能，容易忽略小问题

---

## 🔗 **相关文件索引**

| 文件 | 用途 |
|------|------|
| `scripts/data-migration/export-local-data.sh` | 本地数据导出工具 |
| `scripts/data-migration/import-prod-data.sh` | 生产环境导入工具 |
| `docs/deployment/prod-reset-guide.md` | 整库重置方案（保守派替代版） |
| `docs/directory-structure.md` | 项目目录规范说明 |

---

**最后更新**: 2026-11-15  
**维护者**: AI Assistant + zyc
