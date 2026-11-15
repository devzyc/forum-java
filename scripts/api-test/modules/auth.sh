#!/bin/bash
# ================================================
# Token 管理模块 - auth.sh
# ================================================

# 加载依赖
source "$(dirname "$0")/utils.sh"

# --- 登录并获取 Token ---
get_token() {
    local email="${1:-$TEST_EMAIL}"
    local password="${2:-$TEST_PASSWORD}"
    
    echo "🔐 Logging in as ${email}..." >&2
    
    local response=$(make_request POST "/user-rest/login" "{
        \"loginId\": \"${email}\",
        \"password\": \"${password}\"
    }")
    
    TOKEN=$(extract_json_value "$response" "data")
    
    if [[ -z "$TOKEN" ]]; then
        echo "❌ Failed to extract token from response:" >&2
        echo "$response" >&2
        return 1
    fi
    
    echo "✅ Login successful! Token: ${TOKEN:0:20}...$(echo $TOKEN | tail -c 20)" >&2
    export TOKEN
}

# --- 检查当前 Token 是否有效 ---
check_auth_status() {
    if [[ -z "$TOKEN" ]]; then
        echo "⚠️  No token available. Call 'get_token' first." >&2
        return 1
    fi
    
    echo "👤 Checking authentication status..." >&2
    
    local response=$(make_request GET "/user-rest/current" '' true)
    local code=$(extract_json_value "$response" "code")
    
    if [[ "$code" == "200" ]]; then
        echo "✅ Authenticated successfully!" >&2
        return 0
    else
        echo "❌ Authentication failed (code: $code)" >&2
        return 1
    fi
}

# --- 登出清理会话 ---
logout() {
    if [[ -n "$TOKEN" ]]; then
        echo "🚪 Logging out..." >&2
        make_request POST "/user-rest/logout" '{}' true > /dev/null
        unset TOKEN
        echo "✅ Logged out!" >&2
    fi
}

# --- 显示帮助 ---
show_auth_help() {
    cat << EOF
Token 管理模块可用功能：

核心函数:
    get_token [email] [password]   # 登录并设置全局 TOKEN 变量
    check_auth_status              # 验证当前 token 是否有效
    logout                         # 清除会话
    
环境变量:
    BASE_URL       API 基础地址 (默认：http://localhost:8080)
    TEST_EMAIL     测试账号邮箱
    TEST_PASSWORD  测试账号密码
    
示例:
    source modules/auth.sh
    get_token "test@example.com" "password123"
    check_auth_status || exit 1
EOF
}
