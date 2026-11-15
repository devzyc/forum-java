# 📂 scripts/deploy/ 部署工具集

**版本**: v1.0 (2026-11-15)  
**适用环境**: 腾讯云 LightHouse 生产服务器（43.136.176.163）

---

## 🎯 目录说明

这个文件夹存放所有与**生产环境自动化部署**相关的脚本和配置文件。

### 核心文件

#### `.env.production` - 环境变量配置
包含敏感信息：SSH 密钥路径、数据库密码等。**已加入 .gitignore，不会提交到仓库**。

```bash
# 使用方式
source scripts/deploy/.env.production
echo $APP_URL  # 查看加载的配置
```

需要修改的参数直接编辑此文件即可。

---

#### `prod-deploy.sh` - 一键部署主脚本
自动完成从本地构建到远程部署的全流程。

**功能特性**：
- ✅ Maven 智能检测（优先 mvn，备选 ./mvnw）
- ✅ JAR 包时间戳备份（防止覆盖丢失）
- ✅ 可选数据迁移同步标签
- ✅ 服务状态 + API 双重验证
- ✅ 详细的执行日志输出

**常用命令**：

```bash
# 完整流程：打包 → 上传 → 重启 → 验证
./scripts/deploy/prod-deploy.sh

# 只打包不部署（测试用）
./scripts/deploy/prod-deploy.sh --build-only

# 跳过打包，快速重新部署现有 jar
./scripts/deploy/prod-deploy.sh --skip-build

# 同时更新标签数据到生产数据库
./scripts/deploy/prod-deploy.sh --data-migration
```

**预期输出示例**：
```
======================================
🚀 Forum Java 生产环境部署工具 v1.0
======================================
目标服务器：root@43.136.176.163
JAR 包路径：/root/server/sns_demo/forum-java.jar
应用端口：8080

⏳ Step 1: 开始 Maven 打包...
✅ 构建完成：forum-starter-0.0.1-SNAPSHOT.jar

⏳ Step 2: 上传 JAR 包到生产服务器...
✅ 已上传：/root/server/sns_demo/forum-java-20261115_143000.jar

⏳ Step 3: 备份旧版本 JAR...
✅ 旧版本已备份为 /root/server/sns_demo/forum-java.jar.backup.20261115_143000

⏳ Step 4: 替换新版本并设置权限...
✅ 新版本已就位：/root/server/sns_demo/forum-java.jar

⏳ Step 5: 同步标签数据到生产数据库...
✅ 标签数据已更新

⏳ Step 6: 重启应用服务...
✅ 服务已重启

⏳ Step 7: 等待启动并验证...
✅ API 正常响应，标签数量：12
🎉 部署成功！所有检查通过！

======================================
✨ 部署完成！
======================================
访问地址：http://43.136.176.163:8080
登录账号：zyc3@xx.com / 123
```

---

## 🔐 安全注意事项

1. **PEM 密钥保护**：
   - `~/.ssh/ssh_key_1.pem` 必须设置为 `chmod 400`
   - 不要将 PEM 文件提交到 Git（已在 .gitignore 中排除）

2. **密码管理**：
   - 生产环境 MySQL 密码硬编码在 `.env.production` 中
   - 如需修改，请同步更新该文件和所有引用它的脚本

3. **回滚策略**：
   - 每次部署都会自动备份旧 JAR 包
   - 如果发现问题，可以手动恢复：
     ```bash
     ssh root@43.136.176.163 "cp /root/server/sns_demo/forum-java.jar.backup.* /root/server/sns_demo/forum-java.jar && systemctl restart forum-java"
     ```

---

## 🆘 常见问题

### Q1: "Permission denied" 错误
A: 确保 PEM 密钥有读取权限：
```bash
chmod 400 ~/.ssh/ssh_key_1.pem
```

### Q2: "Service failed to start"
A: 查看详细日志：
```bash
journalctl -u forum-java -n 50 --since "5 minutes ago"
```

### Q3: "API test returns HTTP 500"
A: 可能是数据库连接问题，检查 MySQL 是否运行：
```bash
systemctl status mysql
mysqladmin -u root -p'Abc@1234' ping
```

### Q4: 如何跳过某个步骤？
A: 目前不支持单独跳过某一步骤，但可以通过参数控制：
- `--skip-build`: 跳过打包
- `--data-migration=false`: 禁用数据迁移

---

## 📊 性能优化建议

当前部署耗时约 **2-3 分钟**（取决于网络速度），主要瓶颈：
1. Maven 编译 (~60 秒)
2. SCP 上传 (~30-60 秒，视文件大小而定)
3. 应用启动 (~10 秒)

**加速方案**：
- 使用增量构建（IDEA 内置编译器比命令行快）
- 减少 JAR 包体积（移除未使用的依赖）
- 启用 SSH 压缩 (`scp -C`)

---

## 🔄 后续改进计划

- [ ] 支持多环境配置（dev/test/prod）
- [ ] 集成钉钉/企微通知（部署成功/失败消息推送）
- [ ] 蓝绿部署零停机升级
- [ ] 健康检查端点自定义配置
