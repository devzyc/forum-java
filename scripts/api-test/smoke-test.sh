#!/bin/bash
# ================================================
# 论坛 API 冒烟测试套件 - smoke-test.sh (v2.0)
# 功能：可组合的模块化测试框架
# 用法：./smoke-test.sh [case_name]
# ================================================

set -e

# =================== 配置区域 ===================
BASE_URL="${BASE_URL:-http://43.136.176.163:8080}"
TEST_EMAIL="${TEST_EMAIL:-zyc3@xx.com}"
TEST_PASSWORD="${TEST_PASSWORD:-123}"
TOKEN_HEADER_KEY="token"  # 根据项目实际使用 token header 名
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# ================================================

# 加载所有模块
source "$SCRIPT_DIR/modules/auth.sh"
source "$SCRIPT_DIR/modules/utils.sh"

# --- 发现并执行所有测试用例 ---
discover_and_run_cases() {
    echo "======================================"
    echo "🧪 Forum API Smoke Test Suite v2.0"
    echo "======================================"
    echo "Base URL: $BASE_URL"
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    
    local cases_dir="$SCRIPT_DIR/cases"
    local passed=0
    local failed=0
    
    for case_file in "$cases_dir"/*.sh; do
        if [[ -f "$case_file" ]]; then
            local case_name=$(basename "$case_file" .sh)
            echo "Running test case: $case_name..."
            
            # 尝试 source 执行（不退出当前 shell）
            if bash -c "source '$case_file'; ${case_name//-/_}"; then
                ((passed++)) || true
            else
                ((failed++)) || true
            fi
            echo ""
        fi
    done
    
    show_summary $passed $failed
}

# --- 显示总结报告 ---
show_summary() {
    local passed=$1
    local failed=$2
    
    echo "======================================"
    echo "📊 Test Summary"
    echo "======================================"
    echo "✅ Passed: $passed"
    echo "❌ Failed: $failed"
    echo ""
    
    if [[ $failed -eq 0 ]]; then
        echo "🎉 All tests passed!"
        exit 0
    else
        echo "⚠️  Some tests failed. Check output above for details."
        exit 1
    fi
}
show_help() {
    cat << EOF
论坛 API 冒烟测试工具

用法:
    source test_forum_api.sh          # 加载所有函数到当前 shell
    ./test_forum_api.sh               # 直接运行完整测试流程

可用函数:
    get_token <email> <password>      # 登录并提取 token
    make_request <method> <path> [data] [use_token]  # 通用请求封装
    
    # 业务测试用例
    check_tags                        # 验证标签查询接口
    check_login                       # 验证登录注册接口
    
示例:
    export BASE_URL=https://api.yourdomain.com
    export TEST_EMAIL=admin@test.com
    export TEST_PASSWORD=securepass
    source test_forum_api.sh
    get_token "\$TEST_EMAIL" "\$TEST_PASSWORD"
    check_tags
EOF
}

# --- 核心函数：从 JSON 中提取值（支持多种解析器）---
extract_json_value() {
    local json_str="$1"
    local key_path="$2"  # 如："data.token", "code", "message"
    
    if command -v jq >/dev/null 2>&1; then
        # 优先使用 jq
        echo "$json_str" | jq -r ".$key_path // empty"
    elif command -v python3 >/dev/null 2>&1; then
        # 备选 Python
        echo "$json_str" | python3 -c "
import sys, json
try:
    d = json.loads(sys.stdin.read())
    keys = '${key_path}'.split('.')
    val = d
    for k in keys:
        val = val.get(k) if isinstance(val, dict) else None
    print(val or '')
except:
    print('')
"
    else
        # 最后用 grep/sed (简单场景，不支持嵌套路径)
        local simple_key=$(basename "${key_path}")
        echo "$json_str" | grep -o "\"${simple_key}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | cut -d'"' -f4 || true
    fi
}

# --- 函数：HTTP 请求封装 ---
make_request() {
    local method="$1"
    local path="$2"
    local data="${3:-}"
    local use_token="${4:-false}"
    
    local url="${BASE_URL}${path}"
    local headers=(
        "Content-Type: application/json"
    )
    
    # 如果需要携带 token
    if [[ "$use_token" == "true" ]]; then
        if [[ -z "$TOKEN" ]]; then
            echo "❌ Error: TOKEN not set. Call 'get_token' first!" >&2
            return 1
        fi
        headers+=("${TOKEN_HEADER_KEY}: ${TOKEN}")
    fi
    
    # 执行 curl
    if [[ "$method" == "GET" ]] || [[ "$method" == "DELETE" ]]; then
        curl -s -X "$method" "$url" "${headers[@]}"
    elif [[ -n "$data" ]]; then
        curl -s -X "$method" "$url" "${headers[@]}" -d "$data"
    else
        curl -s -X "$method" "$url" "${headers[@]}"
    fi
}

# --- 函数：登录并获取 Token ---
get_token() {
    local email="${1:-$TEST_EMAIL}"
    local password="${2:-$TEST_PASSWORD}"
    
    echo "🔐 Logging in as ${email}..."
    
    local response=$(make_request POST "/user-rest/login" "{
        \"loginId\": \"${email}\",
        \"password\": \"${password}\"
    }")
    
    # 尝试从响应中提取 token
    TOKEN=$(extract_json_value "$response" "data")
    
    if [[ -z "$TOKEN" ]]; then
        echo "❌ Failed to extract token from response:"
        echo "$response"
        return 1
    fi
    
    echo "✅ Login successful! Token: ${TOKEN:0:20}...$(echo $TOKEN | tail -c 20)"
    export TOKEN
}

# --- 业务测试：检查标签列表 ---
check_tags() {
    echo ""
    echo "🏷️  Checking tags list endpoint..."
    
    local response=$(make_request POST "/tag-rest/all" '{}' false)
    
    # 统计标签数量
    local tag_count=$(echo "$response" | grep -o '"id":[0-9]*' | wc -l)
    
    echo "📊 Response received:"
    if command -v jq >/dev/null 2>&1; then
        echo "$response" | jq '.'
    else
        echo "$response"
    fi
    
    echo ""
    echo "✅ Tags count: $tag_count"
    
    if [[ $tag_count -eq 12 ]]; then
        echo "🎉 Expected 12 built-in tags found!"
        
        # 展示前几个标签
        echo ""
        echo "✨ Preview of first 5 tags:"
        if command -v jq >/dev/null 2>&1; then
            echo "$response" | jq -r '.data[:5][] | "  \(.groupName) - \(.name)"'
        fi
    else
        echo "⚠️  Warning: Expected 12 tags but got $tag_count"
        echo "💡 Check if database update was applied correctly"
    fi
}

# --- 业务测试：检查用户登录状态 ---
check_current_user() {
    echo ""
    echo "👤 Checking current user info..."
    
    local response=$(make_request GET "/user-rest/current" '' true)
    
    echo "Response:"
    if command -v jq >/dev/null 2>&1; then
        echo "$response" | jq '.'
    else
        echo "$response"
    fi
}

# --- 主流程：完整测试 ---
run_full_test() {
    echo "======================================"
    echo "🧪 Forum API Smoke Test Suite"
    echo "======================================"
    echo "Base URL: $BASE_URL"
    echo "Test User: $TEST_EMAIL"
    echo ""
    
    # 步骤 1: 登录
    get_token
    
    # 步骤 2: 检查标签
    check_tags
    
    # 步骤 3: 检查当前用户
    check_current_user
    
    echo ""
    echo "======================================"
    echo "✅ All tests completed!"
    echo "======================================"
}

# =================== 入口逻辑 ===================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # 直接执行模式
    discover_and_run_cases
else
    # source 模式：只提供函数不自动执行
    show_help
fi
