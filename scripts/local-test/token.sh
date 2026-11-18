#!/bin/bash
# ================================================
# 一键登录取 token - token.sh
#
# 标志:
#   -u EMAIL   账号(默认 zyc3@xx.com)
#   -p PASS    密码(默认 123)
#   -n         切到生产环境(不加=本机 127.0.0.1)
#   -h         帮助
#
# 例:
#   ./token.sh              # 本机 + 默认账号
#   ./token.sh -n           # 生产 + 默认账号
#   ./token.sh -u a@b.com -p pwd   # 本机 + 指定账号
#   ./token.sh -n -u a@b.com -p pwd # 生产 + 指定账号
# ================================================

LOCAL_URL="http://127.0.0.1:8080"
PROD_URL="http://43.136.176.163:8080"
EMAIL="zyc3@xx.com"
PASS="123"
BASE_URL="$LOCAL_URL"

usage() { sed -n '2,/^# ==/p' "$0" | sed 's/^# \{0,1\}//'; }

while getopts ":u:p:nh" opt; do
    case "$opt" in
        u) EMAIL="$OPTARG" ;;
        p) PASS="$OPTARG" ;;
        n) BASE_URL="$PROD_URL" ;;
        h) usage; exit 0 ;;
        \?) echo "未知选项 -$OPTARG"; usage; exit 1 ;;
        :) echo "选项 -$OPTARG 需要参数"; exit 1 ;;
    esac
done

ENV_LABEL="本机"; [[ "$BASE_URL" == "$PROD_URL" ]] && ENV_LABEL="生产"
echo "🔐 登录 $EMAIL @ $ENV_LABEL ($BASE_URL)"

RESPONSE=$(curl -s -X POST "${BASE_URL}/user-rest/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASS}\"}")

if command -v jq >/dev/null 2>&1; then
    TOKEN=$(echo "$RESPONSE" | jq -r '.data // empty')
elif command -v python3 >/dev/null 2>&1; then
    TOKEN=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',''))")
else
    TOKEN=$(echo "$RESPONSE" | grep -o '"data"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
fi

if [[ -z "$TOKEN" ]]; then
    echo "❌ 登录失败：$RESPONSE"
    exit 1
fi

TOKEN_FILE="/tmp/forum_token.txt"
echo "$TOKEN" > "$TOKEN_FILE"
chmod 600 "$TOKEN_FILE"

echo "✅ Token: ${TOKEN:0:12}...  → $TOKEN_FILE"
echo "用法: curl -s -X POST ${BASE_URL}/tag/all -H 'Content-Type: application/json' -H \"token: \$(cat /tmp/forum_token.txt)\" | jq '.'"
