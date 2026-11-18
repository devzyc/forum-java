# 🚀 本地 API 测试工具（终极简化版·滑动续期）

**适用场景**: MacBook 本机开发环境，一次登录长期有效  
**更新日期**: 2026-11-17  

---

## ✅ **两个命令搞定一切（利用 Token 滑动续期）**

### **步骤 1: 首次登录拿 Token（只需做一次！）**

```bash
cd /Users/zyc/IdeaProjects/forum-java
./scripts/local-test/token.sh
```

预期输出：
``bash
🔐 正在登录：zyc3@xx.com (@ 本机)
   API 地址：http://127.0.0.1:8080

✅ 登录成功！Token: abcdef123456...xyz7890123456

💡 使用方法：
   curl -s -X POST http://127.0.0.1:8080/tag/all \
     -H 'Content-Type: application/json' \
     -H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)" \
     -d '{}' | jq '.'

📝 Token 已缓存到 /tmp/forum_token_zyc3_xx_com.txt (按账号隔离，不会串号)
```

**关键原理**: 
- `application.properties` 配置了 `custom-config.token.expire-seconds=86400`（24 小时有效期）
- **LoginUserAspect.java 实现了滑动窗口续期机制** → 每次使用 token 都会自动延长到新的 24 小时
- **结论**: 只要你每天至少测一次接口，token 永远不会过期！

---

### **步骤 2: 直接裸调任何接口（不用管 token）**

#### **示例 A: 查询标签列表（验证 12 个内置标签）**

```bash
curl -s -X POST http://127.0.0.1:8080/tag/all \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)" \
  -d '{}' | jq '.data | length'
```

预期输出：`12`（表示 12 个标签）

---

#### **示例 B: 查看全部标签详情**

```bash
curl -s -X POST http://127.0.0.1:8080/tag/all \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)" \
  -d '{}' | jq '.'
```

返回包含 id/groupName/name/description 的完整 JSON 数据。
#### **示例 C: 创建文章**

```bash
curl -s -X POST http://127.0.0.1:8080/article-rest/save \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)" \
  -d '{
    "typeId": 1,
    "title": "我的第一篇文章",
    "contentType": "MARKDOWN",
    "markdownContent": "# Hello World\n这是一篇测试文章",
    "htmlContent": "<h1>Hello World</h1>",
    "headImg": "",
    "tagIds": [188, 189]
  }' | jq '.'
```

---

## 📁 **文件清单**

| 文件 | 作用 |
|------|------|
| `token.sh` | 一键登录脚本，支持邮箱/密码/prod 开关参数；token 按账号隔离缓存到 `/tmp/forum_token_<邮箱>.txt` |

**不再需要的文件（已删除）**:
- ~~`.env.local-test`~~ - 配置已全部写死进脚本，改用命令行参数覆盖
- ~~`test-tags.sh`~~ - 直接用裸 curl 命令更简单
- ~~`auto-login.sh`~~ - 滑动续期机制不需要独立检测

---

## 🔧 **参数化用法（token.sh）**

标准 getopt 标志，顺序任意：

| 标志 | 含义 |
|------|------|
| (无) | 本机 localhost + zyc3@xx.com/123 |
| `-n` | 切到生产 IP(43.136.176.163)，不加=本机 |
| `-u EMAIL` | 指定账号，默认 zyc3@xx.com |
| `-p PASS` | 指定密码，默认 123 |
| `-h` | 帮助 |

```bash
./token.sh                    # 本机 + 默认账号
./token.sh -n                 # 生产 + 默认账号
./token.sh -u a@b.com -p pwd  # 本机 + 指定账号
./token.sh -n -u a@b.com -p pwd  # 生产 + 指定账号
```

---

## ⚙️ **切换环境（本地 vs 生产）**

只需一个 `-n` 开关，无需改任何配置文件：

```bash
./scripts/local-test/token.sh       # 本机(默认)
./scripts/local-test/token.sh -n    # 腾讯云 LightHouse 生产
```

两个环境端口都是 8080，IP 已在脚本内硬编码为 `LOCAL_URL` / `PROD_URL`。

---

## 💡 **最佳实践总结**

### ✅ **正确用法（推荐）**

1. **首次启动程序后只做一次**：
   ```bash
   cd /Users/zyc/IdeaProjects/forum-java
   ./scripts/local-test/token.sh
   ```

2. **之后随便什么时候测都 OK**（因为滑动续期）：
   ```bash
   curl -s -X POST http://127.0.0.1:8080/tag/all \
     -H "Content-Type: application/json" \
     -H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)" \
     -d '{}' | jq '.'
   ```

3. **新增测试用例 = 复制上面的 curl，改 URL 和参数**

---

### ❌ **已废弃的复杂方案**

- ~~source modules/auth.sh~~ （模块加载系统太繁琐）
- ~~smoke-test.sh + cases/*.sh~~ （专用脚本没必要）
- ~~export BASE_URL=...~~ （环境变量容易忘记设置）

---

## 🎯 **核心理念：对 Token 完全无感**

| 问题 | 答案 |
|------|------|
| **每次都要先跑登录脚本吗？** | 不需要！只需首次运行一次 |
| **Token 过期了怎么办？** | 极少发生（除非连续 3 天以上不测试），如果真的过期就重跑 token.sh |
| **要记住很长的命令吗？** | 不用！只需要记住 `-H "token: $(cat /tmp/forum_token_zyc3_xx_com.txt)"` 这一行 |
| **能用在其他接口吗？** | 当然！所有需要 token 的接口都可以用这个 header |

这就是你要的"无感 token"终极形态 —— 没有之一！😄
