# 安全修复动态验证记录

日期：2026-10-15。仅使用临时目录、loopback ZooKeeper、临时 H2 和随机模拟凭据；没有访问生产服务。

## SEC-01：导入授权

基线：Fork `master` 的 `5a76b3b`（已合并文档 PR #1、测试 PR #2）。

修复前：对原始应用运行新增 `ImportAuthorizationIT`，6 项中 3 项断言失败、1 项读取超时、2 项通过。USER 可以新增、覆盖、删除节点，未知角色也可创建节点；无效上传仍进入解析和 Dao 流程；USER 提交的远程 URL 进入取文件流程并在测试监听端没有响应时超时。匿名正常路径已被 AuthFilter 重定向，ADMIN 正常导入也通过。

修复：`Import.doPost` 首先通过现有 Session 校验登录身份及精确 ADMIN 角色；不满足即 403 并返回。该检查先于 Dao 创建、multipart 解析、历史记录、URL 访问及 ZooKeeper 写入。匿名正常路径仍由 AuthFilter 跳转登录。

回归：`ImportAuthorizationIT` 6 项测试覆盖 USER 新增/覆盖/删除均不改变 ZooKeeper、匿名拒绝、未知角色拒绝、畸形上传先拒绝、URL 不被访问、ADMIN 新增/覆盖/删除正常。使用实际打包 JAR，不以 mock 或隐藏按钮代替服务端验证。

验证命令：JDK 8 + `./mvnw -B -ntp clean verify`。本地共 3 项单元测试、11 项集成测试，0 失败/错误/跳过。远程状态以 PR 的 GitHub Actions 检查为准。

范围限制：管理员的 URL 导入仍有 SSRF、超时和资源边界待处理（SEC-03/11）；CSRF（SEC-07）尚未修复；授权补丁不替代这些边界。SEC-02 见下一节。

## SEC-02：REST 认证与密码读取

基线：SEC-01 修复后的源码（与 Fork `master` `24d28dd` 内容相同）。原配置 `blockPwdOverRest=false` 让匿名和 USER 请求都以 ADMIN 角色查询。使用模拟 host 映射及三种大小写的密码字段复现匿名密码原值读取；新增 10 项测试在修复前 9 失败、1 通过。缺失参数还导致 500。

修复策略与兼容性：

| 调用者/配置 | REST 行为 |
| --- | --- |
| 匿名；未设置/关闭/非法 `allowAnonymousRest` | 401，在创建 Session 和 ZK 连接前拒绝 |
| USER 或未知角色；任意遮蔽设置 | 可以读取普通属性，密码字段始终遮蔽 |
| ADMIN；`blockPwdOverRest` 缺失/true/非法 | 密码字段遮蔽 |
| 已登录 ADMIN；显式 `blockPwdOverRest=false` | 允许读取密码原值 |
| 显式 `allowAnonymousRest=true` | 匿名按 USER 读取，密码始终遮蔽，不因旧 false 设置提升权限 |

过滤器改用精确 Servlet 路径，仅放行登录入口或显式开启的 REST 入口；路径中仅包含这些名称不再绕过认证。普通 UI 未登录仍重定向登录。`pwd/password/passwd` 检测统一大小写；成功 REST 响应禁止缓存。缺失/空白属性名返回 400，未知 host 映射返回 404。

`RestAccessIT` 使用 5 个隔离的真实 JAR 实例分别验证默认、旧 false、缺失、非法、匿名兼容配置；包含匿名/USER/未知角色/ADMIN、普通属性正常读取、混合大小写密码遮蔽、匿名拒绝不创建 Session、旧配置只给 ADMIN 读取原值、参数状态、精确路径及登录入口可用。

最终本地 JDK 8 `./mvnw -B -ntp clean verify`：3 个单元测试 + 21 个集成测试，0 失败/错误/跳过；`git diff --check` 通过。远程 CI 状态以修复 PR 检查为准。

迁移：已有匿名脚本必须先登录并复用 Cookie，README 已更新示例；只在有意公开配置的可信环境开启匿名兼容。密码遮蔽基于字段名，不保证识别任意 token/密钥；当前登录角色仍为全局权限，host/app/cluster 参数不是细粒度授权。会话固定、CSRF、客户端 ZK 连接生命周期等仍有独立待处理项。

## SEC-06：会话标识和退出

在 `c5e186c` 基线上新增 3 项真实 JAR 测试，修复前 2 失败、1 通过：成功登录沿用旧标识，Cookie 也没有 HttpOnly。修复后成功登录调用 Servlet 3.1 `changeSessionId()`，旧 Cookie 无法访问 home；Cookie 标记 HttpOnly，仅启用 COOKIE 跟踪，URL `jsessionid` 不能代替 Cookie。退出只使现有 Session 失效，由 null 安全的监听器关闭已有 ZK 连接，不再为退出创建连接/重复关闭。

JDK 8 完整验证共 27 项通过。GET 退出暂时保留，下一项 CSRF 修复会改为带 token 的 POST；会话 Cookie 的 Secure 依赖实际 HTTPS 部署。

## SEC-07：CSRF

基线新增 5 个 HTTP 复现用例全部失败：无 token/错误 token 可写入、登录未校验预登录 token、GET 退出会销毁 Session、无 token POST 可退出、管理员无 token multipart 可导入。修复在 AuthFilter 校验所有非 GET/HEAD/OPTIONS 请求；multipart 由 Import 在解析字段后、创建 Dao/获取 URL/写 ZK 前校验。Token 使用独立 SecureRandom 32 字节、绑定 Session、恒定时间比较；登录同时轮换 Session 和 token。

表单（登录、主页、导入、历史查询、退出）均带隐藏字段；脚本可用 X-CSRF-Token。退出仅支持 POST，GET 405 且保留登录。7 个 CSRF 集成测试含跨 Session 拒绝、登录轮换 token、header 成功写入；原有 CRUD/导入测试已适配真实 token。完整 JDK 8 verify 共 34 项通过。README 更新脚本迁移示例。尚未验证真实浏览器，上传边界及 XSS 仍独立排队。
