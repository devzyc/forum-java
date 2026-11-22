---
trigger: glob
glob:
  - "scripts/deploy/**"
  - "docs/deployment/**"
---
<!-- 生产部署规范：先停后传再启、原子替换、部署验证；部署脚本与运维文档中生效 -->

# 生产部署规范

## 核心原则

- 更新可执行 JAR 前必须先停止旧进程，或者用临时文件加原子移动替换，绝不在服务运行中直接覆盖 JAR。
- 顺序固定为：停服 → 传输 → 启动。

## 为什么不能边跑边覆盖

- Spring Boot 可执行 JAR 采用按需加载策略，旧进程在优雅关闭时会继续加载关闭相关类。
- 若此时正在用 scp 覆盖文件，ClassLoader 读到的是新旧混合的无效字节，会抛 `ClassNotFoundException` 或 `NoClassDefFoundError`。
- 这些异常出现在 ShutdownHook 线程中，容易被误判成"新服务启动失败"，实际是旧进程退场的噪音。
- 禁止行为：`systemctl start` 之后直接 scp 覆盖正在运行的 JAR。

## 方案 A：停服后传输（推荐）

```bash
# 1. 停止服务
sudo systemctl stop forum-java

# 2. 上传新 JAR（本地执行，目标路径以实际部署目录为准）
scp target/forum-java.jar root@部署主机:/部署目录/forum-java.jar

# 3. 启动服务
sudo systemctl start forum-java
```

## 方案 B：原子替换（不停机窗口更短）

```bash
# 1. 先上传为临时名
scp target/forum-java.jar root@部署主机:/部署目录/forum-java.jar.new

# 2. 同分区原子重命名
ssh root@部署主机 'mv /部署目录/forum-java.jar.new /部署目录/forum-java.jar'

# 3. 重启服务
sudo systemctl restart forum-java
```

## 部署后验证

- 先查 `systemctl status`，再查端口监听（`ss -ltnp`），两层都通过才算部署成功。
- 不要只看日志里的 `Started` 行，该行可能被日志级别过滤掉，也会在启动后崩溃的情况下出现。
- 再用一个游客接口做冒烟，确认返回 code 200。

## 本地与生产的差异

- 本地：直接 `kill -9` 强杀，避免 shutdown hook 噪音。
- 生产：走 systemd 正常停机，不接受强杀，避免正在处理的请求被中断。

## 敏感信息

- 部署脚本与规则文件中不写服务器地址、私钥路径、数据库口令，用占位符代替。
- 这些信息只保存在本地私有配置或密码管理器中。
- [待确认] 现有记忆条目中记录的生产环境地址、私钥路径与口令明文，建议删除。
