# scripts/sql/README.md

## 目录说明

这个文件夹存放所有数据库相关的 SQL 脚本。

### 📁 migration/ - 增量迁移脚本（带日期前缀）

用于对现有数据进行修改、新增字段的场景。

**命名规则**: `YYYY-MM-DD-描述.sql`  
**示例**: `2026-11-15-tag-system-update.sql`

特点：
- 只包含必要的 DELETE/INSERT/UPDATE 语句
- 使用事务控制保证一致性
- 可回滚（保留旧数据的备份查询）

---

### 📁 reset/ - 整库重置脚本

从零开始初始化整个数据库结构和数据。

**适用场景**: 
- 新环境首次部署
- 开发阶段频繁重建测试数据
- 生产环境快速恢复出厂设置

**包含内容**:
```sql
-- full-database-reset.sql
├── schema.sql (表结构)
├── data-core.sql (核心业务数据：分类、标签等)
└── data-admin.sql (管理员账户配置)
```

执行方式：
```bash
mysql -u root -p'password' forum-java < reset/full-database-reset.sql
```

---

### 📁 backup/ - 备份策略脚本

自动化定期备份的 shell 包装器。

**daily-backup.sh**: 每天凌晨 2 点全量备份到 /opt/backups/forum-db/

---

## 🔧 工具函数

各子目录中的 `.sh` 文件会用到以下公共函数：

| 函数名 | 位置 | 功能 |
|--------|------|------|
| `extract_json_value()` | smoke-test.sh | JSON 解析兼容层 |
| `make_request()` | smoke-test.sh | HTTP 请求封装 |
| `get_token()` | auth.sh | Token 获取模块 |

---

## ⚠️ 注意事项

1. **不要直接在生产环境执行未经验证的脚本**
2. **每个重要变更都应该先在本地测试通过**
3. **敏感信息（密码/IP）建议使用环境变量而非硬编码**

---

## 🆕 添加新脚本的流程

1. 确定类型：migration(增量) or reset(重置)?
2. 创建对应目录下的 .sql 文件，按规范命名
3. 如果是复杂逻辑，同时写一个对应的 test-case.sh
4. 更新本 README 记录新增脚本的功能
