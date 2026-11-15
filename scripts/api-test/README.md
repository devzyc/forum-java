# scripts/api-test/README.md

## 目录说明

这个文件夹存放所有 API 测试相关的脚本和模块。

### 📁 modules/ - 可复用功能模块

#### auth.sh - Token 管理模块
```bash
source modules/auth.sh
get_token "email" "password"     # 登录并设置全局 TOKEN 变量
check_auth_status                # 验证当前 token 是否有效
logout                           # 清除会话
```

**依赖**: `extract_json_value()` from utils.sh  
**环境变量**: BASE_URL, TEST_EMAIL, TEST_PASSWORD

---

#### utils.sh - 工具函数库
```bash
source modules/utils.sh

# JSON 解析（自动适配 jq/python/grep）
value=$(extract_json_value "$json_str" "data.user.id")

# HTTP 请求封装
response=$(make_request POST "/path" '{"key":"value"}' true)

# 断言检查
assert_equals "$expected" "$actual" "Test description"
```

---

### 📁 cases/ - 具体测试用例

每个 .sh 文件对应一个业务场景的完整测试流程：

| 文件名 | 测试内容 | 调用接口 |
|--------|---------|----------|
| tag-check.sh | 标签系统验证 | `/tag-rest/all` |
| article-check.sh | 文章发布流程 | `/article-rest/save`, `/article-rest/{id}` |
| user-check.sh | 用户注册/登录 | `/user-rest/register`, `/user-rest/login` |
| comment-check.sh | 评论功能 | `/comment-rest/create`, `/comment-rest/list/{postId}` |

---

### smoke-test.sh - 主入口脚本

一键运行所有冒烟测试用例：

```bash
./smoke-test.sh                  # 默认使用本地环境
BASE_URL=https://api.xxx.com ./smoke-test.sh  # 指定生产环境
```

预期输出示例：
```
======================================
🧪 Forum API Smoke Test Suite
======================================
Base URL: http://localhost:8080
Timestamp: 2026-11-15 14:30:00

✅ [PASS] Tags count: 12
✅ [PASS] Current user info retrieved
✅ [PASS] Article list accessible (guest mode)
❌ [FAIL] Comment creation requires login

--------------------------------------
Summary: 3 passed, 1 failed
Exit code: 1
```

---

## 🔧 配置优先级

脚本会按以下顺序读取配置：

1. **环境变量** (最高): `export BASE_URL=...`
2. **.env 文件**: `. env/.local.env`
3. **默认值**: 硬编码在脚本中的 fallback

建议为不同环境创建独立的配置文件：
```
config/
├── dev.env       # 开发环境
├── staging.env   # 预发布环境  
└── prod.env      # 生产环境
```

---

## ➕ 添加新测试用例的步骤

1. 在 `cases/` 下创建 `<name>-check.sh`
2. 引用必要的模块：
   ```bash
   source "$(dirname "$0")/../modules/auth.sh"
   source "$(dirname "$0")/../modules/utils.sh"
   ```
3. 编写测试逻辑，返回非零表示失败
4. 在主脚本中添加对该 case 的调用
5. 更新本 README 表格记录

---

## ⚡ 性能优化提示

- 并发执行多个独立测试用例（使用 `&` + `wait`）
- 缓存 token 避免重复登录
- 过滤掉不必要的 HTML 响应体解析
