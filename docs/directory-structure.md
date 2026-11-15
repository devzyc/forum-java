# 📂 项目目录结构规范说明

**更新日期**: 2026-11-15  
**维护者**: AI Assistant + zyc

---

## 🎯 设计原则

基于 Java Maven 项目的标准布局，结合论坛项目的实际需求，我们采用以下分层策略：

```
forum-java/
├── src/main/java/              # Java 源代码（Maven 默认）
├── forum-starter/resources/    # Spring Boot 配置文件、SQL 初始化脚本
│   ├── application.properties
│   ├── schema.sql             # 数据库表结构定义
│   └── data.sql               # 内置数据种子（随代码版本控制）
│
├── scripts/                   # ✨ 新增：运维/测试专用脚本（非编译产物）
│   ├── sql/                  #   - 数据库相关操作脚本
│   │   ├── migration/       #     · 增量迁移（带日期前缀）
│   │   │   └── YYYY-MM-DD-xxx.sql
│   │   ├── reset/           #     · 整库重置
│   │   │   └── full-database-reset.sql
│   │   └── backup/          #     · 备份策略
│   │       └── daily-backup.sh
│   │
│   ├── api-test/            #   - API 自动化测试框架
│   │   ├── modules/         #     · 可复用模块
│   │   │   ├── auth.sh      #       Token 管理
│   │   │   └── utils.sh     #       JSON 解析工具
│   │   ├── cases/           #     · 具体用例
│   │   │   ├── tag-check.sh #       标签系统验证
│   │   │   └── ...          #       其他业务场景
│   │   ├── smoke-test.sh    #     · 主入口脚本
│   │   └── README.md        #     · 使用说明
│   │
│   └── deploy/              #   - 部署相关脚本
│       ├── local-build.sh   #     本地打包
│       ├── prod-deploy.sh   #     生产环境一键部署
│       └── rollback.sh      #     快速回滚
│
├── docs/                    # 📚 文档区域
│   ├── miniprogram-token-auth-refactor.md  # 小程序登录态改造方案
│   ├── deployment/          # 部署指南
│   │   └── prod-reset-guide.md           # 生产环境快速重置（唯一推荐）
│   └── my-note.md           # 个人笔记
│
├── .gitignore               # Git 忽略规则（scripts/*.sh, update_tags.sql 等敏感文件不入库）
└── pom.xml                  # Maven 父 POM
```

---

## 🔍 为什么这样划分？

### 1️⃣ **src/main/resources vs scripts/sql/**

| 位置 | 用途 | 是否进入 JAR 包 | 更新频率 |
|------|------|----------------|----------|
| `forum-starter/src/main/resources/data.sql` | Spring Boot 启动时自动执行的初始化脚本 | ✅ 是 | 低（随代码发布） |
| `scripts/sql/migration/*.sql` | 手动执行的生产环境变更脚本 | ❌ 否 | 高（随时可能新增） |

**理由**: 
- `data.sql` 是应用的一部分，需要和代码一起版本管理
- `migration/*.sql` 是运维工具，独立于构建产物

---

### 2️⃣ **api-test/modules vs cases**

这是典型的「关注点分离」模式：

```
modules/auth.sh          # "怎么做" - 提供通用能力
    ↓ imports
    
cases/tag-check.sh       # "做什么" - 调用模块完成特定任务
    ↓ uses
    
smoke-test.sh            # "全部做一遍" - 编排所有 case
```

好处：
- **复用性**: `get_token()` 函数可以在任何地方调用
- **可维护性**: 修改 HTTP 请求封装只需改一处 (`utils.sh`)
- **可扩展性**: 添加新测试只需写一个 case 文件，不用碰主逻辑

---

### 3️⃣ **docs/deployment/**

只存放当前正在使用的标准操作流程：
- `prod-reset-guide.md`: 生产环境数据更新的标准 SOP（大刀阔斧版）

**注意**: 不保留任何过时的备选方案，确保团队认知一致性！

---

## 🛠️ 如何使用这些目录？

### 场景 A: 我想在开发环境重建数据库

```bash
cd /Users/zyc/IdeaProjects/forum-java/scripts/sql/reset
mysql -u root -p'Abc@1234' forum-java < full-database-reset.sql
```

或者更简单的方式：直接运行 `./local-run.sh --clean-db`（如果脚本支持的话）

---

### 场景 B: 我要给生产环境打补丁更新标签

```bash
# 1. 编辑 SQL 脚本
vim scripts/sql/migration/2026-11-16-new-feature-tags.sql

# 2. 上传到服务器
scp scripts/sql/migration/2026-11-16-*.sql root@prod-server:/tmp/

# 3. SSH 执行
ssh root@prod-server "mysql -u root -p'password' forum-java < /tmp/patch.sql"
```

---

### 场景 C: 我要测试某个接口是否正常

```bash
# 方法 1: 使用现成的 case
source scripts/api-test/cases/tag-check.sh
check_tags

# 方法 2: 创建新的 case
cp scripts/api-test/cases/template.sh scripts/api-test/cases/my-feature-check.sh
vim scripts/api-test/cases/my-feature-check.sh
chmod +x scripts/api-test/cases/my-feature-check.sh
./scripts/api-test/smoke-test.sh my-feature-check
```

---

## ⚠️ 注意事项

1. **不要提交包含密码的脚本到 Git** → 已在 `.gitignore` 中添加保护
2. **每个重要变更都应该先在本地验证通过再上生产**
3. **定期清理过期的 migration 脚本** → 合并到 `reset/full-init.sql` 中
4. **README.md 要保持同步更新** → 每次新增子目录都要补充说明

---

## 📈 后续优化方向

- [ ] 为 `deploy/prod-deploy.sh` 添加参数化配置（IP/路径通过环境变量传入）
- [ ] 在 CI/CD pipeline 中加入冒烟测试步骤
- [ ] 为常用的 SQL 查询建立索引视图（如：统计各分类文章数）
- [ ] 编写 Makefile 简化常用命令 (`make build`, `make test`, `make deploy-prod`)

---

## 💬 常见问题

**Q: 为什么不把 SQL 放在 resources/db/migration 下？**  
A: Flyway/Liquibase 这类专业迁移工具才这么做。我们是手工管理，用 `scripts/` 更直观。

**Q: test_forum_api.sh 和 smoke-test.sh 有什么区别？**  
A: 前者已被废弃并重构为后者。smoke-test.sh 采用了模块化设计，更易扩展。

**Q: 我改了 data.sql 还需要手动跑 SQL 吗？**  
A: 不需要！只要重启应用，Spring Boot 会自动重新加载（前提是配置了 `spring.sql.init.mode=always`）。但我们目前没开这个选项，因为想精确控制何时刷新缓存。

---

**记住**: 好的目录结构应该让新人一看就知道东西该放哪里！🎯
