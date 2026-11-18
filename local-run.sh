#!/usr/bin/env bash
# forum-java 本地启动脚本（已加入 .gitignore，不会进仓库）
# 用法:
#   ./local-run.sh          打包最新代码并启动
#   ./local-run.sh --fast   跳过打包，直接启动现有 jar
#   ./local-run.sh --build  只打包不启动
set -e
cd "$(dirname "$0")"

# ① JAVA_HOME: 用 macOS 自带探测器找 JDK 11, 找不到再兜底固定路径 (JDK 升级后无需改脚本)
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 11 2>/dev/null \
    || echo "$HOME/Library/Java/JavaVirtualMachines/corretto-11.0.21/Contents/Home")
fi
export JAVA_HOME

# ② 日志覆盖配置: /tmp 会被 macOS 定期清理, 改放 ~/.forum-local; 缺失时自动重建,
#   避免启动时写入 jar 内 logback.xml 硬编码的 /opt/logs 生产路径报 Permission denied
LOGDIR="$HOME/.forum-local"
LOGBACK="$LOGDIR/logback-local.xml"
mkdir -p "$LOGDIR"
if [ ! -f "$LOGBACK" ]; then
  cat > "$LOGBACK" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern></encoder>
    </appender>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</configuration>
EOF
  echo "[local-run] 已生成日志覆盖配置: $LOGBACK"
fi

# ③ 打包 (默认执行; --fast 跳过; --build 只打包)
if [ "$1" != "--fast" ]; then
  # Maven: ./mvnw 缺 wrapper 文件不可用; 用 ~/.m2 下已下载的 wrapper 发行版
  # (路径中间夹一层哈希目录, 用 find 定位最稳), 找不到再回退 PATH 里的 mvn
  MVN=$(find "$HOME/.m2/wrapper/dists" -type f -name mvn -path '*/bin/mvn' 2>/dev/null | head -1)
  [ -n "$MVN" ] || MVN=$(command -v mvn || true)
  [ -n "$MVN" ] || { echo "[local-run] 未找到可用的 Maven, 无法打包 (可用 --fast 跳过打包)"; exit 1; }
  echo "[local-run] 打包中 ($MVN -o -q -DskipTests package) ..."
  "$MVN" -o -q -DskipTests package
  echo "[local-run] 打包完成: forum-starter/target/forum-java.jar"
fi
if [ "$1" = "--build" ]; then exit 0; fi

# ④ 启动 (两个 -D 缺一不可: logback 先于 Spring 初始化, 只给 logging.config 会先读 jar 内配置刷权限错误)
echo "[local-run] 启动服务 http://127.0.0.1:8080  (Ctrl+C 停止)"
exec "$JAVA_HOME/bin/java" \
  -Dlogback.configurationFile="$LOGBACK" \
  -Dlogging.config="$LOGBACK" \
  -jar forum-starter/target/forum-java.jar
