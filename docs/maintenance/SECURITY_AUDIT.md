# ZKUI 安全审计与修复跟踪

> 来源：Fork `master` 基线 `1b3e7c1` 静态代码审查，2026-10-15。**SEC-01/02 已在隔离环境动态复现并修复；其余项仍是待验证风险，不是 CVE 报告。** 不应直接将当前版本暴露到公网。

| ID | 优先级 | 静态发现与证据 | 建议的验证及处置 |
| --- | --- | --- | --- |
| SEC-01 | P0（已修复） | [`AuthFilter.java`](../../src/main/java/com/deem/zkui/filter/AuthFilter.java) 只检查登录态；[`Import.java`](../../src/main/java/com/deem/zkui/controller/Import.java) 的 `doPost` 无 ADMIN 角色校验，与 [`Home.java`](../../src/main/java/com/deem/zkui/controller/Home.java) 中写操作的校验不一致 | 已复现 USER/未知角色可写入。`Import.doPost` 入口校验 ADMIN，在解析上传、创建 Dao、获取 URL 前返回 403。6 项真实 JAR 回归通过，见 [SECURITY_VALIDATION.md](SECURITY_VALIDATION.md) |
| SEC-02 | P0（已修复） | [`RestAccess.java`](../../src/main/java/com/deem/zkui/controller/RestAccess.java) 与 `AuthFilter` 允许匿名访问 `/acd/appconfig`；[`config.cfg`](../../config.cfg) 默认 `blockPwdOverRest=false`，REST 路径会将角色设置成 ADMIN | 已复现匿名及 USER 获得密码原值。默认要求登录、默认遮蔽密码，显式关闭遮蔽仅对已登录 ADMIN 有效；匿名兼容显式开启仍按 USER 读取。10 项真实 JAR 回归，见 [SECURITY_VALIDATION.md](SECURITY_VALIDATION.md) |
| SEC-03 | P1 | [`Import.java`](../../src/main/java/com/deem/zkui/controller/Import.java) 将请求构造的地址交给 `new URL(...).openConnection()` | 在隔离环境测试服务端请求伪造（SSRF）路径；限制协议/目的地址并配置超时，考虑禁用远程 URL 导入 |
| SEC-04 | P1 | [`ZooKeeperUtil.java`](../../src/main/java/com/deem/zkui/utils/ZooKeeperUtil.java) 在节点创建、修改、导入时可输出原始值；`Import` 历史记录可能包含导入行 | 对密钥、令牌、密码以及普通值统一脱敏，避免将原始数据写入日志和持久化历史 |
| SEC-05 | P1 | [`config.cfg`](../../config.cfg) 含示例默认管理账号、明文密码和 `https=false`；ZooKeeper ACL 留空时使用 `OPEN_ACL_UNSAFE` | 移除生产环境默认凭据，启动时警示或拒绝不安全组合，文档要求可信网络及 TLS |
| SEC-06 | P1 | [`Login.java`](../../src/main/java/com/deem/zkui/controller/Login.java) 登录成功在现有 Session 设置属性，未见 `changeSessionId()` 或重新生成会话的逻辑 | 验证会话固定风险，设计会话轮换、Cookie 属性、过期和退出行为 |
| SEC-07 | P1 | `Home`、`Import` 使用 POST 修改数据，所审查的类中未见 CSRF token 检查 | 检查全局是否另有 CSRF 防护并做浏览器测试；若缺失，为状态修改请求增加保护 |
| SEC-08 | P1 | [`pom.xml`](../../pom.xml) 中多个依赖多年未升级；Jetty 9、H2 1.x、旧日志绑定、旧前端库 | 导出 Maven 依赖树，运行依赖漏洞扫描并核对实际影响面；统一升级，不要盲合旧 Dependabot PR |
| SEC-09 | P2 | [`LdapAuth.java`](../../src/main/java/com/deem/zkui/utils/LdapAuth.java) 用简单绑定，DN 拼装为 `domain\\username`；部分认证异常被忽略 | 验证空密码、不同 LDAP/AD 域、LDAPS/TLS、失败行为与连接超时，关联 PR #107 |
| SEC-10 | P2 | `ServletUtil.getRemoteAddr` 直接信任请求里的 `X-Forwarded-For`，`Main` 存在代理自定义处理 | 只信任来自配置的反向代理链路，不把任意客户端报头作为可信审计身份 |
| SEC-11 | P1 | `Import` 创建 `ServletFileUpload`，未见显式限制上传大小、文件条目数 | 在隔离环境验证大文件、重复导入等资源消耗边界并设置上限 |
| SEC-12 | P1 | `Dao.checkNCreate` 在 `env=dev` 执行 `flyway.clean()`；数据库迁移错误被捕获后忽略 | 将破坏性清理限定为隔离测试库、提前备份、迁移失败应显式失败 |

## 限制与执行准则

1. 先在隔离环境建立 USER/ADMIN 两组凭据及含模拟敏感数据的测试节点。禁止使用真实生产密码。
2. 对 SEC-01/02 分别验证“未授权请求被拒绝”和“已授权请求正常”，不以页面按钮隐藏代替服务端权限检查。
3. 安全变更单独 PR，附复现与修复前后的测试；高风险事项在 CI/功能完成前也可优先处理。
4. 当前没有依赖扫描结果或 CVE 关联；除明确标记已动态验证的项目外，其余风险仍须验证。
