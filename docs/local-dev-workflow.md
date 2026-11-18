# 本地改码后的验证流程

每次代码改动后，按以下三步操作。命令均在项目根目录执行。

## 1. 停掉旧服务

必须 `kill -9` 强制终止，优雅关闭会因 JAR 被覆盖产生噪音日志。

```bash
lsof -ti:8080 | xargs kill -9
```

## 2. 编译打包

主力命令（增量编译，不清空 target，日常改动用这条就够了）：

```bash
./local-run.sh --build
```

兜底命令（clean build，用于排查缓存类问题、MyBatis namespace 完整性检查等场景）：

```bash
MVN=$(find ~/.m2/wrapper/dists -type f -name mvn -path '*/bin/mvn' | head -1) && JAVA_HOME=$(/usr/libexec/java_home -v 11) "$MVN" clean package -DskipTests
```

产物路径：`forum-starter/target/forum-java.jar`

## 3. 启动新服务

```bash
./local-run.sh --fast
```

等待控制台输出 `Started` 字样即服务就绪。