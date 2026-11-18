# 🎯 **本地 API 测试 - 30 秒速查表**

---

## ✅ **首次使用（只做一次）**

```bash
cd /Users/zyc/IdeaProjects/forum-java
./scripts/local-test/token.sh
```

输出示例：`✅ Token 已更新并缓存 (有效期约 1 天)`

---

## 💡 **之后每次测试 = 裸 curl + 引用 token 文件**

### **查询标签列表**
```bash
curl -s -X POST http://127.0.0.1:8080/tag/all \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token.txt)" \
  -d '{}' | jq '.data | length'
```

预期输出：`12`

---

### **查看全部标签详情**
```bash
curl -s -X POST http://127.0.0.1:8080/tag/all \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token.txt)" \
  -d '{}' | jq '.'
```

---

### **创建文章**
```bash
curl -s -X POST http://127.0.0.1:8080/article-rest/save \
  -H "Content-Type: application/json" \
  -H "token: $(cat /tmp/forum_token.txt)" \
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

## 🔑 **核心原理（为什么不用每天登录？）**

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `custom-config.token.expire-seconds` | 86400 | Token 默认 24 小时过期 |
| **滑动续期机制** | ✅ 启用 | 每次调用接口都会自动延长到新的 24 小时 |

**结论**: 只要你每天至少测一次，token 永远不会失效！

---

## ❓ **常见问题**

### Q1: 提示 `/tmp/forum_token.txt: No such file or directory`
A: 运行一次 `./scripts/local-test/token.sh` 即可。

### Q2: 返回 `{code: 401}` 
A: Token 可能真的过期了（连续 3 天以上没测）。重新跑一遍那个登录命令就行。

### Q3: 想切换到生产环境测试
A: 编辑 `scripts/local-test/.env.local-test`，把 BASE_URL 改成 `http://43.136.176.163:8080`，然后重跑登录脚本。

---

## 📝 **记忆口诀**

> **"先 token.sh 一次，之后随便 curl 加 token header"**

就这么简单！没有之一！😄
