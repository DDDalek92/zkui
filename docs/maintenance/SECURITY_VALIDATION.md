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

## SEC-03/11：导入来源和资源限制

将无大小限制的磁盘 multipart 解析替换为逐项流式读取：1 MiB 单文件、2 MiB 请求、32 项、8 KiB 字段、单文件及 10000 条记录上限；严格 UTF-8，验证全部记录/路径后才写入，拒绝根和 `/zookeeper` 路径。过大 413，无效输入 400；不返回输入行或远程 URL。导入历史仅记条目数/覆盖策略，不再保存文件名/URL/原始配置值。

URL 默认关闭；开启后只有运维配置的完整 HTTP(S) URL 可用（精确匹配，不是请求自报主机），拒绝 userinfo/fragment、其他协议和所有重定向；HTTP 200、1 MiB、3 秒连接/读取超时及 10 秒正文读取期限。白名单是显式网络授权，需信任该端点及 DNS；不声称在允许任意不可信 URL 的情况下解决 DNS 重绑定。默认隐藏远程导入 UI。

新增 2 项输入单元测试、6 项真实 JAR 集成测试，覆盖 UTF-8/过大/无效字节、默认关闭/未批准、本地批准来源成功、重定向不访问目标、过大响应、超时、上传过大、无效整文件无写入、保留路径、parts/多文件限制。第一次发现 Commons streaming 把文件超限包在 IOException 中导致状态 400，补齐异常映射后验证 413。完整 JDK8 verify 共 42 项通过。分批应用仍非 ZK 事务；依赖组件漏洞扫描另行进行。

## SEC-04/13：输出与新增值日志

新增 3 项真实 JAR 用例在修复前全部失败：节点名恶意标记未转义、搜索值原样输出、普通名称下的配置值进入历史/DEBUG 日志，且多行值缺少保留空白的显示样式。修复后模板文本/属性中的不可信字符串显式 HTML 转义，监控以预格式化纯文本显示；创建/修改/导入的应用日志不输出值，新增历史仅记路径及导入摘要。历史入口改为 ADMIN，并隐藏 USER 入口，避免旧库中的明文历史被只读账户读取；旧数据库记录没有被改写/删除，需要运维审查备份及保留策略。

采纳上游 #109（gouzhuang）多行样式并扩展搜索结果，见 PR_REVIEW.md。OutputSafetyIT 覆盖标记、搜索、历史角色、普通 token 值不落历史/DEBUG 日志、多行空格和本地 CSS。JDK8 verify 共 45 项通过。没有真实浏览器执行结果；HTML 转义不等于已完成前端依赖安全审计。

## 连接可靠性、上游 #86 / Issue #64 / #48

新增 4 项复现在原代码上全部失败：150ms 后返回 CONNECTING、不可达仍返回对象、REST 关闭 UI 会话连接、ensemble 仅使用首地址。修复使用 SyncConnected 事件及独立初始等待期限（zkConnectTimeout，秒，默认5，最大60），超时关闭连接并失败，不返回 null；HTTP 请求报告503。页面会话同步复用已连接客户端，更换失效客户端先关闭；REST 使用专属短期客户端，不创建匿名Session、不关闭UI连接。所有入口传入完整 ensemble（含chroot）。

监控用 ZK 原生地址解析器分离 ensemble/chroot/IPv6；每节点每命令失败不阻断后续节点，1秒连接/读取、3秒响应期限及1Mi字符限制。ConnectionReliabilityIT 5项（延迟服务、不可达、HTTP503、会话ID保留、坏首节点+chroot+监控）及 MonitorEndpointTest 1项（IPv6/chroot）通过；全套 JDK8 verify 51项。

上游 #86 来源和重写说明见 PR_REVIEW.md。#64 的短等待与 #48 的端口/chroot解析问题有直接回归。#33/#77/#98 的历史网络/版本/容器环境尚无同一环境复现，不能仅因改进连接而宣称全部解决。IPv6 地址解析已测，真实双栈网络尚待验证。

## Digest 客户端身份与 ACL（上游 #50/#87）

zkAuthScheme=digest 和 zkAuthCredentials=username:password 提供客户端身份，UI 会话及独立 REST 客户端均在首次操作前添加身份；Web 的 ADMIN 不能绕过 ZK 权限。defaultAcl 只控制新节点授权，支持已认证创建者 auth ACL；Digest ACL 标识要求原生 SHA1 摘要而非明文口令。配置在启动前验证且错误消息不回显原值；ACL权限改为按位 OR，避免 rr 相加误变成写权限，错误/空 ACL 不静默开放。读取缺少节点才返回 null，NoAuth 明确返回 HTTP403。

新增2项配置单元测试、4项真实JAR Digest回归、1项真实ZK重复权限回归；完整JDK8 verify共58项（8单元+50集成）通过。覆盖UI/Export/REST正确身份、错误身份403、Web ADMIN使用只读ZK身份不能写、创建者ACL拒绝匿名、普通日志不含凭据、重复r仍只读、无效配置不回显。配置注释JSON实际解析成功。

PR来源及重写说明见 PR_REVIEW.md，关联 #112/#99/#80/#69/#59/#42/#36 的Digest连接问题。认证身份是应用全局服务身份，Web角色仍独立控制应用操作；没有实现每个Web用户独立ZK身份、Kerberos或任意SASL提供者验证。Digest凭据必须通过可信网络/TLS传输并保护配置文件。

## LDAP 绑定和角色（上游 #107、Issue #28/#52/#106）

保留 DOMAIN\username 和 UPN，不重复拼接已限定域的用户名；新增 ldapUserDnPattern（兼容 userDNPattern 别名），使用 Rdn.escapeValue 和 LdapName 防止用户名注入DN。空用户名/空口令在联网前拒绝；单次绑定连接/读取超时默认3000ms，范围1–30000。支持原生JNDI多URL列表（最多8个）及多域（最多8个），不跟随认证 referral；成功/失败均关闭上下文，UI仅显示通用登录失败，不解析或回显目录错误。

LDAP精确账号规则优先于通配规则，匹配大小写不敏感，避免后置星号/大小写变化绕过明确的USER规则。失败重登录清除旧认证。新增4项策略单元测试和5项隔离LDAP/真实JAR集成测试，包括真实DN绑定、错误/空密码、DN转义、URL故障转移、HTTP角色优先级、失败重登录和无响应目录超时；完整JDK8 verify共67项（12单元+55集成）通过。测试用UnboundID LDAP SDK 7.0.5仅为test依赖，不进入应用JAR。

#28 的DN配置问题有直接修复。#52补充域/UPN及角色配置说明，但没有访问原AD服务；#106没有正文/环境，标为无法复现，不把通用LDAP改进当作原故障根因。实际AD、受信LDAPS证书链和浏览器仍待部署验证；JNDI原生LDAPS使用JVM信任设置，不关闭证书检查，未新增StartTLS功能。

## UTF-8 与节点自身值（上游 #88、Issue #43/#62）

新增4项真实JAR回归在修复前全部失败：无 charset 的中文表单（缺失 searchStr）、非UTF-8默认JVM上的中文导入、父节点自身值显示、父节点密码遮蔽。过滤器在首次参数读取前指定UTF-8；所有节点写入与文本导出显式UTF-8，配置值不trim。Home展示当前节点自身值且HTML转义/按角色遮蔽，null数据视为空文本。

完整JDK8 clean verify共72项（13单元+59集成）通过；TextAndNodeValuesIT运行子JVM为US-ASCII。当前节点自身值先提供只读显示；旧导出格式仍把换行编码成字面\n并移除CR，不能无损表达任意二进制或区分字面反斜杠n，此格式限制未宣称解决。

## 根路径和子树搜索（上游 #54）

zkRootPath默认/，是应用数据入口的命名空间边界，相对于zkServer原生chroot。Home、Export、Import、REST均验证路径；原生PathUtils拒绝非规范路径，边界比较拒绝/appconfig2冒充/appconfig。批量删除/导入在首次写入前验证全部目标，根本身和系统路径不可修改/删除；导入要求根预先存在，不能借自动创建父路径写入根外祖先。REST hosts映射位置必须在根内，映射值/参数校验且祖先回退止于根。导航/面包屑停在根，查询参数URL编码，搜索使用当前节点子树。应用限制不替代ZK ACL或chroot。

独立修复前工作区5组真实JAR测试全失败。修复后增加正常写入/中文导入、含&节点链接、保护根删除和缺失根无外部祖先创建；6组RootNamespaceIT通过。全套共78项（13单元+65集成）。原打包冒烟断言按URL编码后的链接更新；不改变实际目标。搜索/导出仍递归收集，超大树性能和父节点数据导出另列限制。

## 离线排序（上游 #110）

表格增加本地table-sort.js，稳定、大小写不敏感的文本升/降序，移动原行而不生成innerHTML，不改变复选框/模态框事件。按钮不提交表单，支持原生键盘和aria-sort。未新增生产前端依赖或外部网络资源。Java完整verify79项（13单元+66集成）通过，其中BrowserPagesIT从隔离真实JAR生成ADMIN主页、搜索、USER主页。3组Playwright检查覆盖键盘/升降/稳定排序、选择/编辑事件、样式、HTML不执行和密码遮蔽；CI结果为最终依据。本机Chromium及headless-shell下载失败（空/损坏压缩包），仅JS语法校验成功。

## 历史限额、缺失配置与数据库连接（#41部分重写）

historyLimit默认50且校验1–1000，所有/筛选查询同限额，标题显示实际限额。https与X-Forwarded-For缺失时false，env缺失时prod；不会因缺配置清空历史。Dao读写成功/失败都在finally释放线程连接。HistoryStorageIT使用一次性H2验证限额、参数化筛选、故意删除测试表后的读写失败无连接泄漏、另一测试库恢复及重复启动保留历史；ConfigDefaultsIT验证缺失标记启动和历史标题，配置单元测试覆盖越界/非法值。旧代码独立工作区2失败+1错误；完整verify84项（14单元+70集成）通过。不引入#41的值trim、描述SQL或旧V1改写；描述功能延期条件见PR_REVIEW.md。

## 上传头边界与日志依赖（#105部分重写）

FileUpload1.6.0（Java8兼容）配置512字节part头上限。新增真实HTTP回归在1.4上返回302并接受1024字节文件名头，修复后返回413且无节点写入；其余大小/parts/UTF-8/权限/CSRF回归保留。SLF4J1.7.36+reload4j1.2.27，排除ZooKeeper传递Log4j1和旧绑定，保留现有log4j.properties格式；组装JAR元数据验证仅选定绑定，既有敏感日志回归继续通过。完整Java8 verify86项（14单元+72集成）通过。依赖树用maven-dependency-plugin3.8.1实取，见DEPENDENCY_TREE.txt；这是依赖清单，不是漏洞扫描通过报告。Jetty/H2/MySQL/ZK等老组件仍需后续迁移，不宣称已完成安全发行验收。
