#!/bin/bash
# ================================================
# 标签系统检查用例 - tag-check.sh
# ================================================

# 加载依赖模块
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../modules/auth.sh"
source "$SCRIPT_DIR/../modules/utils.sh"

check_tags() {
    echo ""
    echo "🏷️  [Case] Tags List Endpoint Test"
    
    # 确保已登录（虽然这个接口不需要 token，但为了统一流程）
    if [[ -z "$TOKEN" ]]; then
        get_token > /dev/null 2>&1 || true
    fi
    
    local response=$(make_request POST "/tag-rest/all" '{}' false)
    
    # 统计标签数量
    local tag_count=$(echo "$response" | grep -o '"id":[0-9]*' | wc -l)
    
    # 验证结果
    assert_equals "12" "$tag_count" "Tags count should be 12"
    
    if command -v jq >/dev/null 2>&1; then
        echo "✨ First 5 tags preview:"
        echo "$response" | jq -r '.data[:5][] | "  \(.groupName) - \(.name)"'
    fi
    
    return $((tag_count != 12))
}

# 如果直接执行此脚本则运行测试
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    check_tags
fi
