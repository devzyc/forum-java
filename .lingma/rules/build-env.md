---
trigger: glob
glob:
  - "**/pom.xml"
  - "local-run.sh"
  - "forum-starter/src/main/resources/**"
  - "**/logback*.xml"
  - "**/application*.properties"
  - "**/application*.yml"
---
<!-- 构建与环境配置：Maven 依赖、本地构建启动、日志与数据库配置位置；pom/sh/properties/logback 中生效 -->

# 构建与环境配置

## Maven 依赖管理

- 构建工具 Maven，父工程为 `spring-boot-starter-parent 2.3.3.RELEASE`，Java 版本 1.8。
- 版本统一在 `dependencyManagement` 中声明，子模块不写版本号，避免同一依赖多版本。
- 现有基线版本：Lombok 1.16.10、MyBatis Starter 2.1.3、Fastjson 1.2.69、PageHelper 5.1.8、Guava 15.0、Commons Codec 1.8。
- 新增依赖时先查 `dependencyManagement` 是否已有同类，没有再加版本声明，不要直接在子模块 pom 里写死。

## 本地构建（mvnw 不可用）

- 根目录 `./mvnw` 不可用：缺 `.mvn/wrapper/maven-wrapper.properties` 与 wrapper jar，执行会报 `ClassNotFoundException: MavenWrapperMain`；系统 PATH 里也没有 mvn。
- 可用做法：直接调用 `~/.m2/wrapper/dists` 下已下载的发行版可执行文件，路径中间夹一层哈希目录，用 find 定位最稳。

```bash
# Do：定位可用的 mvn
find "$HOME/.m2/wrapper/dists" -type f -name mvn -path '*/bin/mvn'
```

- 配合 `JAVA_HOME` 指向 JDK11（corretto-11）与 `-o` 离线模式；产物在 `forum-starter/target/forum-java.jar`。

## local-run.sh 启动脚本

- 脚本位于仓库根目录，已加入 `.gitignore` 不进仓库；新环境需要自行放置。
- 封装四件事：
  - 用 `/usr/libexec/java_home -v 11` 探测 JDK11，失败时兜底固定 corretto-11 路径。
  - 生成日志覆盖配置到 `~/.forum-local/logback-local.xml`，缺失时自动重建。
  - find 定位 Maven 后执行 `-o -DskipTests package`。
  - 用两个 `-D` 参数（`logback.configurationFile` 与 `logging.config`）启动 jar。
- 用法：`./local-run.sh` 打包并启动；`--fast` 跳过打包直接启动；`--build` 只打包不启动。

## 删除类或资源后必须 clean 重建

- `--build` 底层是 `package` 而不是 `clean package`，属于增量编译，不会清理各模块 `target/classes` 里的旧资源副本。
- 删除 Mapper XML、DO、DAO 之后，旧的 `target/classes` 副本仍在 classpath 中，jar 包本身干净但启动会因引用已删类抛 `ClassNotFoundException`。
- 正确做法：删除类或资源后执行一次 `clean package`，或手工清掉 stale 副本再 `--fast` 启动。
- 验证产物是否干净：

```bash
unzip -l forum-starter/target/forum-java.jar | grep -i 被删类名
```

## 日志配置位置

- 本地 logback 配置不放在 `/tmp`（macOS 会定期清理），放在 `~/.forum-local/logback-local.xml`。
- 启动时必须同时设置 `-Dlogback.configurationFile` 和 `-Dlogging.config`，两者都指向该文件。
- 只设其中一个会导致 Logback 回退到 jar 内置配置，而内置配置硬编码了生产日志路径，本地会报权限错误。

## 数据库与测试脚本

- 本地数据库参数集中在 `application.properties`，不在代码中散写；连接参数只放本地配置，不写入规则文件。
- 本地 API 测试脚本统一存放在 `scripts/local-test` 目录，取 token 的脚本 `token.sh` 即在此目录。
- 本地库无表结构变更时可直接复用旧数据。
