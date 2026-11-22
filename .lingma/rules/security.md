<!-- 敏感数据安全：日志与审计脱敏、口令不入规则；Controller/Manager/Util/持久化层生效 -->

# 敏感数据与日志安全

## 明文密码禁止落盘（硬约束）

- 请求入参中字段名命中 `password`、`oldPassword`、`newPassword` 时，在序列化出口统一掩码为 `***`。
- 覆盖必须包含两处：
  - stdout 接口日志：`ExceptionHandlerAndLogAspect` 等 4 处切面，统一调用 `StringUtil.toJSONString(args)`。
  - 审计库：`forum_opt_log` 表，由 `AbstractLoginManager.login` 与 `OptLog.createUserRegisterRecordLog` 写入。
- 实现位置选在 `StringUtil.toJSONString` 内部的 ValueFilter，按字段名命中后返回掩码值。
- 选择集中控制而不是逐个请求类加注解：避免模块间依赖问题，也避免新接口漏配。

```java
// Do：统一出口做掩码，按字段名判断
if (SENSITIVE_FIELDS.contains(fieldName)) {
    return "***";
}
```

## 不要假设请求类自带安全

- 仅在 Request 对象上加 `@JSONField(serialize=false)` 不够，它只影响序列化，可能漏掉数据库入库路径。
- 新增敏感字段时，同步检查三处：接口调用日志、审计日志、缓存实体。
- 审计日志属于长期留存数据，敏感字段一旦落库需要走清理流程，代价远高于事先掩码。

## 例外：登录缓存不能掩码

- `cacheLoginUser` 中保存的是含 MD5 密码的用户信息，不能简单套用掩码，否则改密校验会失效。
- 判定标准：掩码只作用于"对外输出的序列化"，不作用于内部缓存实体与校验入参。

## 验证方式

- 改完后跑全链路：注册 → 登录 → 改密 → 用新密码重登。
- 检查点：stdout 检索密码原文应为 0 命中；`forum_opt_log` 中相关三行 content 应为 `***`；改密后新密码可登录、旧密码失败。

## 敏感配置不入规则与日志

- 生产服务器地址、SSH 私钥路径、数据库口令、测试账号口令等，只保存在本地配置或密码管理器，不写入规则文件、不写入代码注释、不打印到日志。
- 排查问题时如果必须引用连接信息，用占位符表述（例如"本地 MySQL 实例，参数见 application.properties"）。
- [待确认] 现有记忆中记录了生产环境地址、私钥路径与数据库明文口令，建议从记忆条目中删除，只保留"配置在何处"这类定位信息。
