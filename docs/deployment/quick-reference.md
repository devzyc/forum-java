# 📖 生产环境部署快速参考卡

**适用服务器**: 腾讯云 LightHouse (43.136.176.163)  
**更新日期**: 2026-11-15  

---

## ⚡️ 一分钟速查

### 场景 A: 代码改了，要重新部署

```bash
cd /Users/zyc/IdeaProjects/forum-java
./scripts/deploy/prod-deploy.sh --skip-build
```

**说明**: 跳过 Maven 打包（因为已经手动 build 过），直接上传现有 JAR。

---

### 场景 B: 完整构建 + 部署

```bash
./scripts/deploy/prod-deploy.sh
```

**流程**: `Maven clean package` → `SCP 上传` → `systemctl restart forum-java`

---

### 场景 C: 只更新标签数据到生产数据库

```bash
# 先确保本地 data.sql 已修改为新的 12 个购物生活化标签
mysql -u root -p'Abc@1234' forum-java < scripts/sql/migration/2026-11-15-tag-system-update.sql
```

然后登录生产服务器执行相同 SQL（或通过 `--data-migration` 参数自动同步）。

---

### 场景 D: 验证部署是否成功

```bash
# 检查服务状态
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163 "systemctl status forum-java"

# 测试 API
curl http://43.136.176.163:8080/tag-rest/all | jq '.data | length'
# 预期输出：12

# 运行冒烟测试
BASE_URL=http://43.136.176.163:8080 TEST_EMAIL=zyc3@xx.com TEST_PASSWORD=123 \
    ./scripts/api-test/smoke-test.sh tag-check.sh
```

---

## 🔑 关键信息备忘

| 项目 | 值 |
|------|-----|
| **公网 IP** | 43.136.176.163 |
| **SSH 用户** | root |
| **PEM 密钥路径** | ~/.ssh/ssh_key_1.pem |
| **MySQL 密码** | Abc@1234 （与本地相同） |
| **JAR 位置** | /root/server/sns_demo/forum-java.jar |
| **Systemd 服务名** | forum-java |
| **应用端口** | 8080 |
| **测试账号** | zyc3@xx.com / 123 |

---

## 🛠️ 常用命令集合

### SSH 连接

```bash
# 正常登录
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163

# 免交互执行单条命令
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163 "df -h"
```

### MySQL 操作

```bash
# 进入数据库 CLI
mysql -u root -p'Abc@1234' forum-java

# 查看表记录数
SELECT COUNT(*) FROM forum_tag;

# 清空并导入新标签
TRUNCATE TABLE forum_tag;
INSERT INTO forum_tag ... ;
```

### Systemd 控制

```bash
# 启动/停止/重启
sudo systemctl start forum-java
sudo systemctl stop forum-java
sudo systemctl restart forum-java

# 查看日志
journalctl -u forum-java -n 50 --since "5 minutes ago"

# 设置开机自启
sudo systemctl enable forum-java
```

### SCP 文件传输

```bash
# 上传单个文件
scp -i ~/.ssh/ssh_key_1.pem local-file.txt root@43.136.176.163:/tmp/

# 下载整个目录
scp -r -i ~/.ssh/ssh_key_1.pem root@43.136.176.163:/var/log/forum logs-backup/
```

---

## 🆘 故障排查流程图

```
问题现象：部署后无法访问？
│
├─ Step 1: 检查服务状态
│   └─ journalctl -u forum-java -n 50
│       ├─ 看到 "Started Application" → 继续下一步
│       └─ 看到异常堆栈 → 根据错误修复代码/配置
│
├─ Step 2: 检查端口监听
│   └─ netstat -tlnp | grep 8080
│       ├─ java 进程在 LISTENING → 继续下一步
│       └─ 没有输出 → 应用未启动或崩溃
│
├─ Step 3: 检查防火墙
│   └─ ufw status  # 腾讯云可能还需要安全组规则
│       ├─ allow 8080/tcp → 外部可访问
│       └─ deny/inactive → 开放端口：ufw allow 8080/tcp
│
└─ Step 4: 测试 API
    └─ curl http://localhost:8080/tag-rest/all
        ├─ HTTP 200 + JSON → 功能正常
        └─ Connection refused → 回到 Step 1
```

---

## 💾 数据迁移注意事项

### 从本地导出 UGC 数据

```bash
cd scripts/data-migration
./export-local-data.sh
```

生成文件位于：`scripts/data-migration/exported-data/*.sql`

### 上传到生产服务器

```bash
scp -i ~/.ssh/ssh_key_1.pem -r exported-data root@43.136.176.163:/tmp/
```

### 在生产环境导入

```bash
ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163
cd /tmp && bash import-prod-data.sh
```

⚠️ **重要提醒**：
- 导入前会要求确认是否清空旧数据（输入 `yes` 才继续）
- 建议先备份用户表以防万一
- 导入完成后必须重启应用清理缓存

---

## 🔄 回滚操作指南

如果新版本有问题，如何快速恢复？

### 方法 1: 使用自动备份的 JAR

```bash
# 查找最近一次的备份
ls -lht /root/server/sns_demo/forum-java.jar.backup.*

# 恢复指定版本
cp /root/server/sns_demo/forum-java.jar.backup.YYYYMMDD_HHMMSS \
   /root/server/sns_demo/forum-java.jar
   
# 重启服务
systemctl restart forum-java
```

### 方法 2: Git 拉取稳定版本重新部署

```bash
cd /path/to/project
git checkout v1.0-stable  # 切换到某个 release tag
mvn clean package -DskipTests
./scripts/deploy/prod-deploy.sh --skip-build
```

---

## 📞 紧急联系清单

| 情况 | 联系方式 |
|------|----------|
| 服务器宕机 | 腾讯云控制台工单系统 |
| 域名解析失效 | DNSPod 管理后台 |
| CDN 图片不显示 | 七牛云对象存储控制台 |
| 数据库损坏 | MySQL binlog 恢复方案（需提前开启） |

---

## ✨ 最佳实践总结

1. ✅ **每次大改前先 git commit** - 方便随时回退
2. ✅ **部署前跑一遍冒烟测试** - `smoke-test.sh tag-check.sh`
3. ✅ **保留至少 3 个历史 JAR 备份** - 防止误删
4. ✅ **监控 CPU/内存使用率** - LightHouse 控制台有告警功能
5. ✅ **定期导出数据做离线备份** - 每月一次即可

---

**最后更新**: 2026-11-15 by AI Assistant + zyc
