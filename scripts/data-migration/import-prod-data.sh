#!/bin/bash
# ================================================
# 生产环境数据导入脚本 - import-prod-data.sh
# 功能：将从本地导出的 UGC 数据注入到腾讯云 LightHouse
# ================================================

set -e

PROD_DB="forum-java"
PROD_USER="root"
PROD_PASS="Abc@1234"  # ← 腾讯云 LightHouse 数据库密码
INPUT_DIR="$(dirname "$0")/exported-data"

echo "======================================"
echo "🚀 开始导入数据到生产环境..."
echo "======================================"

if [ ! -d "$INPUT_DIR" ]; then
    echo "❌ Error: Export directory not found!"
    echo "请先运行 export-local-data.sh 导出数据"
    exit 1
fi

# --- 【可选】清空现有业务表（保守策略：只删不相关的数据）---
echo "⚠️  警告：即将清空以下表的所有数据："
echo "   - forum_posts (所有文章/问答)"
echo "   - forum_comment (所有评论)"
echo "   - forum_user_follow (关注关系)"
echo ""
read -p "确认继续？(yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "操作已取消"
    exit 0
fi

mysql -u "$PROD_USER" -p"$PROD_PASS" "$PROD_DB" << 'EOF'
SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖顺序删除子表 -> 父表
TRUNCATE TABLE forum_tag_posts_mapping;
TRUNCATE TABLE forum_comment;
TRUNCATE TABLE forum_user_food;
TRUNCATE TABLE forum_user_follow;
TRUNCATE TABLE forum_posts;
TRUNCATE TABLE forum_article_type;
TRUNCATE TABLE forum_tag;
TRUNCATE TABLE forum_user;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ 旧数据清理完成!' as status;
EOF

# --- 导入新数据 ---
TABLES=(
    "forum_user"
    "forum_article_type"
    "forum_tag"
    "forum_posts"
    "forum_comment"
    "forum_tag_posts_mapping"
    "forum_user_follow"
    "forum_user_food"
)

for table in "${TABLES[@]}"; do
    sql_file=$(ls "$INPUT_DIR"/${table}_*.sql 2>/dev/null | tail -1)
    
    if [ -f "$sql_file" ]; then
        echo "⏳ Importing $table from $(basename $sql_file)..."
        mysql -u "$PROD_USER" -p"$PROD_PASS" "$PROD_DB" < "$sql_file"
        
        rows=$(grep -c "^INSERT INTO" "$sql_file" || true)
        echo "✅ $table: $rows records imported"
    else
        echo "⚠️  No data file for $table, skipping..."
    fi
done

# --- 验证结果 ---
echo ""
echo "🔍 验证导入结果:"
mysql -u "$PROD_USER" -p"$PROD_PASS" "$PROD_DB" << 'EOF'
SELECT '用户总数:' as info, COUNT(*) FROM forum_user;
SELECT '文章总数:' as info, COUNT(*) FROM forum_posts WHERE category='ARTICLE';
SELECT '标签总数:' as info, COUNT(*) FROM forum_tag;
EOF

echo ""
echo "======================================"
echo "🎉 数据迁移完成！"
echo "======================================"
echo ""
echo "建议后续操作："
echo "1. systemctl restart forum-java  # 重启应用刷新缓存"
echo "2. curl -X POST http://localhost:8080/user-rest/login -H 'Content-Type: application/json' -d '{\"loginId\":\"zyc3@xx.com\",\"password\":\"123\"}' | jq '.data'  # 测试登录接口获取 token"
echo "3. 检查前端页面是否正常显示文章列表和头像"
