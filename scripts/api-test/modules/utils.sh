#!/bin/bash
# ================================================
# API 测试工具函数库 - utils.sh
# ================================================

# --- JSON 值提取（支持多种解析器）---
extract_json_value() {
    local json_str="$1"
    local key_path="$2"
    
    if command -v jq >/dev/null 2>&1; then
        echo "$json_str" | jq -r ".$key_path // empty"
    elif command -v python3 >/dev/null 2>&1; then
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

# --- HTTP 请求封装 ---
make_request() {
    local method="$1"
    local path="$2"
    local data="${3:-}"
    local use_token="${4:-false}"
    
    local url="${BASE_URL}${path}"
    local headers=("Content-Type: application/json")
    
    [[ "$use_token" == "true" ]] && headers+=("token: ${TOKEN}")
    
    if [[ "$method" =~ ^(GET|DELETE)$ ]]; then
        curl -s -X "$method" "$url" "${headers[@]}"
    elif [[ -n "$data" ]]; then
        curl -s -X "$method" "$url" "${headers[@]}" -d "$data"
    else
        curl -s -X "$method" "$url" "${headers[@]}"
    fi
}

# --- 断言检查 ---
assert_equals() {
    local expected="$1"
    local actual="$2"
    local description="$3"
    
    if [[ "$expected" == "$actual" ]]; then
        echo "✅ [PASS] $description"
        return 0
    else
        echo "❌ [FAIL] $description (Expected: $expected, Got: $actual)"
        return 1
    fi
}

# --- 显示帮助 ---
show_utils_help() {
    cat << EOF
API 测试工具函数库可用功能：

JSON 处理:
    extract_json_value <json_string> <key_path>
    
HTTP 请求:
    make_request <method> <path> [data] [use_token:true/false]
    
断言验证:
    assert_equals <expected> <actual> <description>
    
示例:
    source modules/utils.sh
    response=\$(make_request POST "/user-rest/login" '{"email":"a","password":"b"}')
    token=\$(extract_json_value "\$response" "data")
    assert_equals "abc123" "\$token" "Token extraction test"
EOF
}
