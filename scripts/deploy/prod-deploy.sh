#!/bin/bash
# ================================================
# 生产环境一键部署脚本 - prod-deploy.sh
# 功能：自动完成本地构建 → 上传 JAR → 重启服务全流程
# 用法：./scripts/deploy/prod-deploy.sh [options]
# ================================================

set -e

# --- 加载环境变量配置 ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
source "$SCRIPT_DIR/.env.production"

echo "======================================"
echo "🚀 Forum Java 生产环境部署工具 v1.0"
echo "======================================"
echo "目标服务器：${SSH_USERNAME}@${PROD_SERVER_IP}"
echo "JAR 包路径：${JAR_DEPLOY_PATH}"
echo "应用端口：${APP_PORT}"
echo ""

# --- 检查 PEM 密钥文件是否存在 ---
if [[ ! -f "${PEM_KEY_PATH/#\~/$HOME}" ]]; then
    echo "❌ Error: PEM key file not found at ${PEM_KEY_PATH}"
    echo "请确认 ~/.ssh/ssh_key_1.pem 存在且可读取"
    exit 1
fi

# --- 函数：显示帮助信息 ---
show_help() {
    cat << EOF
用法：$0 [选项]

选项:
    --build-only      仅打包不部署
    --skip-build      跳过打包直接部署（使用现有 target/*.jar）
    --data-migration  同时执行数据迁移（导入本地 UGC 数据到生产）
    --help            显示此帮助信息

示例:
    $0                  # 完整流程：构建 + 部署
    $0 --build-only     # 只打包
    $0 --skip-build     # 用当前 jar 快速重新部署
    
EOF
}

# --- 解析命令行参数 ---
BUILD_ONLY=false
SKIP_BUILD=false
DATA_MIGRATION=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build-only) BUILD_ONLY=true; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --data-migration) DATA_MIGRATION=true; shift ;;
        --help) show_help; exit 0 ;;
        *) echo "未知选项：$1"; show_help; exit 1 ;;
    esac
done

# =================== 步骤 1: Maven 构建 ===================
if [[ "$SKIP_BUILD" == false ]]; then
    echo "⏳ Step 1: 开始 Maven 打包..."
    cd "$PROJECT_ROOT"
    
    if command -v mvn >/dev/null 2>&1; then
        MVN_CMD="mvn clean package -DskipTests -q"
    elif [[ -f "./mvnw" ]]; then
        MVN_CMD="./mvnw clean package -DskipTests -q"
    else
        echo "❌ Error: Maven not found!"
        exit 1
    fi
    
    eval $MVN_CMD
    
    JAR_FILE=$(find forum-starter/target -name "*.jar" -type f | head -1)
    
    if [[ ! -f "$JAR_FILE" ]]; then
        echo "❌ Error: JAR file not generated!"
        exit 1
    fi
    
    echo "✅ 构建完成：$(basename $JAR_FILE)"
else
    echo "⏭️  Step 1: 跳过构建，查找现有 JAR..."
    JAR_FILE=$(find "$PROJECT_ROOT/forum-starter/target" -name "*.jar" -type f | head -1)
    
    if [[ ! -f "$JAR_FILE" ]]; then
        echo "❌ Error: No existing JAR file found! Run without --skip-build first."
        exit 1
    fi
    echo "✅ 找到 JAR: $(basename $JAR_FILE)"
fi

# =================== 步骤 2: 上传 JAR 包 ===================
echo "⏳ Step 2: 上传 JAR 包到生产服务器..."

REMOTE_TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REMOTE_JAR_NAME="forum-java-${REMOTE_TIMESTAMP}.jar"

scp -i "${PEM_KEY_PATH/#\~/$HOME}" \
    "$JAR_FILE" \
    "${SSH_USERNAME}@${PROD_SERVER_IP}:${JAR_DEPLOY_PATH%/*}/${REMOTE_JAR_NAME}"

echo "✅ 已上传：${JAR_DEPLOY_PATH%/*}/${REMOTE_JAR_NAME}"

# =================== 步骤 3: 备份旧版本 ===================
echo "⏳ Step 3: 备份旧版本 JAR..."

ssh -i "${PEM_KEY_PATH/#\~/$HOME}" \
    "${SSH_USERNAME}@${PROD_SERVER_IP}" \
    "[ -f '${JAR_DEPLOY_PATH}' ] && cp '${JAR_DEPLOY_PATH}' '${JAR_DEPLOY_PATH}.backup.${REMOTE_TIMESTAMP}' || echo 'No old JAR to backup'"

echo "✅ 旧版本已备份为 ${JAR_DEPLOY_PATH}.backup.${REMOTE_TIMESTAMP}"

# =================== 步骤 4: 替换新版本 ===================
echo "⏳ Step 4: 替换新版本并设置权限..."

ssh -i "${PEM_KEY_PATH/#\~/$HOME}" \
    "${SSH_USERNAME}@${PROD_SERVER_IP}" \
    "mv '${JAR_DEPLOY_PATH%/*}/${REMOTE_JAR_NAME}' '${JAR_DEPLOY_PATH}' && chmod +x '${JAR_DEPLOY_PATH}'"

echo "✅ 新版本已就位：${JAR_DEPLOY_PATH}"

# =================== 步骤 5: 数据库更新（可选）===================
if [[ "$DATA_MIGRATION" == true ]]; then
    echo "⏳ Step 5: 同步标签数据到生产数据库..."
    
    SQL_FILE="$PROJECT_ROOT/scripts/sql/migration/2026-11-15-tag-system-update.sql"
    
    scp -i "${PEM_KEY_PATH/#\~/$HOME}" \
        "$SQL_FILE" \
        "${SSH_USERNAME}@${PROD_SERVER_IP}:/tmp/tag-update.sql"
    
    ssh -i "${PEM_KEY_PATH/#\~/$HOME}" \
        "${SSH_USERNAME}@${PROD_SERVER_IP}" \
        "mysql -u root -p'${MYSQL_PASS_PROD}' ${MYSQL_DB} < /tmp/tag-update.sql"
    
    echo "✅ 标签数据已更新"
fi

# =================== 步骤 6: 重启应用服务 ===================
echo "⏳ Step 6: 重启应用服务..."

ssh -i "${PEM_KEY_PATH/#\~/$HOME}" \
    "${SSH_USERNAME}@${PROD_SERVER_IP}" \
    "systemctl restart ${SYSTEMD_SERVICE_NAME}"

echo "✅ 服务已重启"

# =================== 步骤 7: 验证部署结果 ===================
echo "⏳ Step 7: 等待启动并验证..."

sleep 10  # 给应用足够启动时间

# 检查服务状态
SERVICE_STATUS=$(ssh -i "${PEM_KEY_PATH/#\~/$HOME}" \
    "${SSH_USERNAME}@${PROD_SERVER_IP}" \
    "systemctl is-active ${SYSTEMD_SERVICE_NAME}")

if [[ "$SERVICE_STATUS" != "active" ]]; then
    echo "❌ Warning: Service status is '$SERVICE_STATUS', check logs:"
    echo "   journalctl -u ${SYSTEMD_SERVICE_NAME} -n 50"
    exit 1
fi

# 测试 API 是否可用
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://${PROD_SERVER_IP}:${APP_PORT}/tag-rest/all)

if [[ "$RESPONSE" == "200" ]]; then
    TAG_COUNT=$(curl -s http://${PROD_SERVER_IP}:${APP_PORT}/tag-rest/all | jq '.data | length')
    echo "✅ API 正常响应，标签数量：${TAG_COUNT}"
    
    if [[ "$TAG_COUNT" == "12" ]]; then
        echo "🎉 部署成功！所有检查通过！"
    else
        echo "⚠️  警告：预期 12 个标签但得到 ${TAG_COUNT}, 请检查 SQL 是否正确执行"
    fi
else
    echo "❌ API 测试失败 (HTTP ${RESPONSE}), 查看日志:"
    echo "   journalctl -u ${SYSTEMD_SERVICE_NAME} -n 50 --since '5 minutes ago'"
    exit 1
fi

echo "======================================"
echo "✨ 部署完成！"
echo "======================================"
echo "访问地址：http://${PROD_SERVER_IP}:${APP_PORT}"
echo "登录账号：${TEST_EMAIL} / ${TEST_PASSWORD}"
echo ""
echo "下一步建议："
echo "1. ./scripts/api-test/smoke-test.sh tag-check.sh  # 冒烟测试"
echo "2. 浏览器打开前端页面验证功能"
