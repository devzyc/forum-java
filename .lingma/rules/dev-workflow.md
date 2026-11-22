---
trigger: glob
glob:
  - "scripts/local-test/**"
  - "local-run.sh"
---
<!-- 本地开发验证流程：构建、冒烟、停服、curl 技巧；本地测试脚本与启动脚本中生效 -->

# 本地开发与验证 SOP

## 标准循环

1. 改代码。
2. `./local-run.sh` 打包并启动；只改了资源或配置时用 `--fast`。
3. 跑冒烟请求确认服务可用。
4. 验证功能接口。
5. 停服。

## 涉及的删除类改动

- 若本次改动删除了 Mapper XML、DO、DAO 等类或资源，必须做一次 clean 重建后再启动。
- 原因：`--build` 是增量 `package`，`target/classes` 会残留旧副本，启动时抛 `ClassNotFoundException`。

## 冒烟测试

- 游客接口：请求 `GET /article-rest/comments/1`，返回 code 200 的 JSON 即服务正常。
- 返回 405 表示服务存活但 HTTP 方法用错，不是启动失败。核对 Controller 上的 `@GetMapping` / `@PostMapping`，例如 `/article-rest/{id}` 是 POST，用 curl 直接访问会 405。
- 连接被拒绝才是服务没起来。
- 需要登录的接口在 Header 带 `token`，取 token 用 `scripts/local-test/token.sh`。
- 旧的 `login-local.sh`、`test-tags.sh` 双脚本模式已废弃，统一走 `token.sh`。

## 停服

- 本地停服直接强杀：先 `lsof -ti:8080 | xargs kill -9`，再启动新版本。
- 不使用不带 `-9` 的 kill：JAR 被覆盖后优雅关闭会卡住并持续占用端口，或抛 `NoClassDefFoundError`，重启必然失败。
- 强杀只适用于本地开发；生产环境走正常停机流程（见部署规则）。

## curl 与 jq 使用技巧

- 需要同时看列表长度和完整 JSON 时，跑两条独立的 curl 管道：一条取 `.data | length`，一条格式化输出。
- 不要用 tee 写临时文件再读，会导致命令不返回、终端卡住。
- 查询结果为空时先确认请求方法与路径，再怀疑数据。

## 日志排查顺序

1. 先看 `systemctl` 或进程状态确认是否存活。
2. 再看端口监听确认是否绑定成功。
3. 最后看应用日志中是否有 `ClassNotFoundException`、权限错误、Bean 初始化失败。
