#!/bin/bash
# ================================================
# 本地数据导出脚本 - export-local-data.sh
# 功能：从 MacBook MySQL 导出 UGC 数据用于生产迁移
# ================================================

set -e

LOCAL_DB="forum-java"
LOCAL_USER="root"
LOCAL_PASS="Abc@1234"
OUTPUT_DIR="$(dirname "$0")/exported-data"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "======================================"
echo "📦 开始导出本地 UGC 数据..."
echo "======================================"

mkdir -p "$OUTPUT_DIR"

# --- 只导出需要的业务表（排除 cache、opt_log 等临时数据）---
TABLES=(
    "forum_user"           # 用户账户
    "forum_article_type"   # 文章分类
    "forum_tag"            # 标签系统
    "forum_posts"          # 帖子内容（含文章/问答）
    "forum_comment"        # 评论数据
    "forum_tag_posts_mapping"  # 标签-帖子关系
    "forum_user_follow"    # 关注关系
    "forum_user_food"      # 收藏记录
)

for table in "${TABLES[@]}"; do
    echo "⏳ Exporting $table..."
    
    mysqldump -u "$LOCAL_USER" -p"$LOCAL_PASS" \
        --no-create-info \
        --skip-add-locks \
        --disable-keys \
        --extended-insert=false \
        "$LOCAL_DB" "$table" > "$OUTPUT_DIR/${table}_${TIMESTAMP}.sql"
    
    rows=$(grep -c "^INSERT INTO" "$OUTPUT_DIR/${table}_${TIMESTAMP}.sql" || true)
    echo "✅ $table: $rows records exported"
done

echo ""
echo "======================================"
echo "🎉 导出完成！文件位置："
ls -lh "$OUTPUT_DIR"
echo "======================================"
echo ""
echo "下一步操作："
echo "1. scp -i ~/.ssh/ssh_key_1.pem -r $OUTPUT_DIR root@43.136.176.163:/tmp/"
echo "2. ssh -i ~/.ssh/ssh_key_1.pem root@43.136.176.163 'cd /tmp && bash import-prod-data.sh'"
