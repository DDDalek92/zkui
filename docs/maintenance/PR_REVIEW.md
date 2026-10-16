# 上游开放 PR 审核台账（初稿）

> #39现代MySQL运行路径已由准确head两轮六job及合并master六job验证。当前补旧Flyway2/MySQL独立方言历史的第二checksum/location路径，见MYSQL_DIALECT_HISTORY.md；不改原V1或上游状态。#103/#105的维护意图已重写，更多历史/安全/部署限制仍非全部修复。

> MySQL现代路径接续：#38原引擎校验/桥接及合并master六job通过后，现继续统一Flyway13 core/mysql运行路径与旧运行引擎移除，见MODERN_MYSQL_RUNTIME.md。#103/#105的历史版本不直接采纳，实际准入依准确新head；D01其余审计及原环境限制继续保留，不改变上游状态或14/46数量的含义。

> 当前证据（2026-10-15）：#102升级意图已由Fork #37的H22.5.252/现代Flyway独立重写有限验收，准确head两轮六job及合并master六job全成功，三平台各149项；原始2.1.210补丁仍不直接采用，任意历史库迁移仍不普遍放行。下一步MySQL checksum/API/module工作先增加独立引擎桥接预演（MYSQL_FLYWAY_BRIDGE.md），不是应用运行路径切换；上游仍14项，不改其状态。

> H2接续（2026-10-15）：本增量以2.5.252/现代Flyway13.10.3重做#102的运行时升级意图，独立新建方言及原V1离线桥接后的真实Main/HTTP/重启见[MODERN_H2_RUNTIME.md](MODERN_H2_RUNTIME.md)，仍须准确head全部CI准入。不直接采用旧2.1.210补丁；任意历史库/真实权限磁盘/更广时区夹具及正式迁移放行仍延期，不能把这项有限应用验收或14项分类叫做全部问题修复。

> 当前处置补充（2026-10-15）：#103已由Fork #28的Connector/J26.7/MySQL8.4+方言、真实5.6逻辑迁移/TLS路径superseded；#105日志/Jetty/MySQL意图分别由#16/#21/#28重写，不采用其历史目标版本。#102完整H2应用/驱动迁移仍延期；#25仅证明有限合成副本桥接。下方#103/MySQL deferred及旧ORM描述是历史，不是当前全部状态。14项处置不等于全部功能修复，具体head证据见MYSQL_VALIDATION.md、CURRENT_VALIDATION.md和Fork PR。

> D01后续更新（2026-10-15）：#93/#104的旧Jetty方案已由Java17/Jetty12.1.14 EE8整体实现superseded；#105日志/Jetty部分重写，MySQL仍deferred。ActiveJDBC改用参数化JDBC以保留旧H2，见[JAVA17_VALIDATION.md](JAVA17_VALIDATION.md)及对应Fork PR准确head的CI。#103/#102仍延期。下方最初预审和#16决策保留为历史，不能将14项都叫做全部功能修复。

> 2026-10-15；基线 `1b3e7c1`。上游：[DeemOpen/zkui/pulls](https://github.com/DeemOpen/zkui/pulls)。共 14 个开放 PR。分类是**静态预审建议**，不等于通过测试、批准合并或全部逐行审核。每项整合前须检查完整差异、讨论、许可证、构建与回归测试。任何上游 PR 均不能直接合并到本 Fork 的 `master`。

| PR | 标题 / 目标 | 初步决策 | 关键检查和依赖 |
| --- | --- | --- | --- |
| [#87](https://github.com/DeemOpen/zkui/pull/87) | 修正 defaultAcl 示例注释 | 已重做，Fork PR #10 | JSON 示例修正；凭据/ACL 分离，Digest/Auth ACL 回归 |
| [#109](https://github.com/DeemOpen/zkui/pull/109) | 保留多行节点文本空格和换行 | 已采纳并扩展，Fork PR #8 | OutputSafetyIT；HTML 转义及搜索结果同步处理，真实浏览器待验证 |
| [#54](https://github.com/DeemOpen/zkui/pull/54) | 配置根路径、子树搜索 | 已重做，Fork PR #13 | 全部HTTP数据入口受根路径限制，搜索当前子树，RootNamespaceIT |
| [#110](https://github.com/DeemOpen/zkui/pull/110) | 表格排序 | 已重做，Fork PR #14 | 本地原生脚本，不增加CDN/运行时依赖；真实模板浏览器检查 |
| [#88](https://github.com/DeemOpen/zkui/pull/88) | 修复中文乱码 | 已重做，Fork PR #12 | HTTP 表单/节点写入/导入/导出 UTF-8；不采纳原始值日志 |
| [#86](https://github.com/DeemOpen/zkui/pull/86) | 多 ZooKeeper 地址与监控 | 已重做并验证，Fork PR #9 | 完整 ensemble、chroot、监控失败隔离及超时；#64/#48 有直接回归 |
| [#50](https://github.com/DeemOpen/zkui/pull/50) | ZooKeeper 客户端认证 | 已重做，Fork PR #10 | 独立客户端凭据、正确/错误身份、只读 ZK 权限及创建者 ACL 测试 |
| [#107](https://github.com/DeemOpen/zkui/pull/107) | LDAP 登录调整 | 已重做，Fork PR #11 | DN 模板与原域登录兼容、空密码/超时/角色回归；真实 AD/LDAPS 待验证 |
| [#41](https://github.com/DeemOpen/zkui/pull/41) | 输入修剪及其他功能 | 已拆分处置，Fork PR #15 | 历史限额/默认值/连接关闭重写；值trim和拼接SQL拒绝；描述功能延期 |
| [#105](https://github.com/DeemOpen/zkui/pull/105) | Jetty/日志/MySQL 依赖升级 | 吸收升级意图，重新制定版本 | 2022 年版本已过时；需完整依赖树与兼容性测试 |
| [#103](https://github.com/DeemOpen/zkui/pull/103) | MySQL Connector/J 版本更新 | 归入统一依赖迁移 | 驱动坐标和 JDBC URL、ActiveJDBC 兼容 |
| [#102](https://github.com/DeemOpen/zkui/pull/102) | H2 1.3 到 2.1 | 独立数据库迁移任务 | 备份/恢复、Flyway、SQL、历史数据不可直接无验证升级 |
| [#104](https://github.com/DeemOpen/zkui/pull/104) | Jetty Server 9 到 10 | 归入 Jetty 迁移设计 | 对齐所有 Jetty 模块、Servlet API 和运行环境 |
| [#93](https://github.com/DeemOpen/zkui/pull/93) | Jetty 9 到 11 | 不能按原样直接合并 | 现有 `javax.servlet` 与 Jetty 11 `jakarta.servlet` 不兼容；需整体迁移 |

## 已完成的整合

- **#109 → Fork PR #8，adopted**。作者 `gouzhuang`，提交 `4fcdc0a893445836840158ebcd7549f08a432162`；完整差异及讨论已审（无评论）。采纳 `.mono-pre-wrap` 和主页 span；扩展至搜索值且补齐 HTML 转义。`OutputSafetyIT` 验证多行中文/空格输出与本地 CSS、恶意标记转义。未运行真实浏览器计算样式；上游 PR 不会被自动关闭。

- **#86 → Fork PR #9，rewritten**。作者 `wupeixuan`，提交 `eb55fda5f5f5311a6d29a8969fbec6df67fc6e3e`；全量差异/讨论已审（无评论）。采纳完整 ensemble 传递和监控失败隔离的意图；使用 ZK 原生 ConnectStringParser，支持 chroot/IPv6，监控有超时及容量边界；同时修复连接就绪等待和 REST 关闭 UI 连接。ConnectionReliabilityIT、MonitorEndpointTest 验证；不把所有 ConnectionLoss 问题一律视为解决。

- **#50 → Fork PR #10，rewritten**。作者 `xyzker`，提交 `5538e61893c43691f62576f5d1e504ed86fb7bb8`，完整差异及2条讨论已审。采纳客户端认证需求；不从 defaultAcl 取凭据，改为独立 zkAuthScheme/zkAuthCredentials，UI/REST 同路径应用；DigestAuthenticationIT 验证正确/错误身份、只读ZK身份、创建者ACL和匿名拒绝。
- **#87 → Fork PR #10，rewritten**。作者 `dgqypl`，提交 `01fdd110d4de4819a213661b5e9f750d2b40f709`，完整差异/讨论已审（无评论）。采纳 JSON 引号/闭括号修正（样例实际解析2条ACL）；不采纳明文密码作为Digest ACL标识的示例，改为明确区分凭据/摘要及auth创建者ACL。依据 [ZooKeeper 3.5.7 ACL 文档](https://zookeeper.apache.org/doc/r3.5.7/zookeeperProgrammers.html#sc_ZooKeeperAccessControl)。

- **#107 → Fork PR #11，rewritten**。作者 `zhangdong1015`，提交 `d584537b73d6d04cffbad7b20781ce2e60cbf6c0`；全量差异/讨论已审（无评论）。采纳可配置LDAP绑定目标的需求，不强制固定uid/OU、不删除AD域/UPN或通配角色、不解析并回显服务端reason错误。新增DN模板与转义、空口令拒绝、超时、上下文关闭、精确角色优先（大小写不敏感）及失败重登录清除身份。真实隔离 LDAP 和 HTTP 测试通过；真实 AD/LDAPS 链路另列待验证。

## 已关闭历史 PR 的复查

上游搜索另外显示 27 个已关闭 PR（**包括合并与未合并的 PR**），不能将它们全部当成待合并。复查优先对象：[#97](https://github.com/DeemOpen/zkui/pull/97) JSON 验证、[#83](https://github.com/DeemOpen/zkui/pull/83) 字符串值、[#73](https://github.com/DeemOpen/zkui/pull/73) JS 安全、[#78](https://github.com/DeemOpen/zkui/pull/78) Docker、[#27](https://github.com/DeemOpen/zkui/pull/27) 与 [#16](https://github.com/DeemOpen/zkui/pull/16) 涉及相互撤销的认证修改。须查询 `merged` 状态、上游当前代码是否已经包含效果，禁止重复应用已合并内容。

## 第二轮差异检查补充（2026-10-15）

已重新取得全部 14 个开放 PR 的文件补丁，以下是本轮静态检查发现；PR 讨论、作者身份/署名和对应动态回归仍需继续审核，本轮没有合入上游补丁。

| PR | 具体证据 | 整合要求 |
| --- | --- | --- |
| #41 | `home.js` 对所有文本输入及 textarea 执行 trim；`Dao` 新增路径 SQL 字符串拼接；直接修改 `V1__Core.sql`；historyLimit 判断条件也可疑 | 避免静默改变配置值；描述功能与输入校验拆开；参数化 SQL；新迁移脚本和历史数据库测试 |
| #50 | 从 ACL 的 scheme/id 向 `addAuthInfo` 逐项传参，把节点授权与客户端身份混在同一配置中 | 分离认证凭据和新节点 ACL；验证 Digest、IP、auth 等方案，不把原始口令作为持久化 ACL 标识 |
| #87 | 新增 `digest` ACL 使用 `auth_username:auth_password` | ZooKeeper Digest 认证发送用户名/口令，ACL 标识使用用户名及摘要；两者应分别说明，参考[官方 ACL 文档](https://zookeeper.apache.org/doc/r3.8.5/zookeeperProgrammers.html#sc_ZooKeeperAccessControl) |
| #88 | UTF-8 修改之外在 `LeafBean.getStrValue()` 增加 INFO 日志，包含原始节点值且使用 `%v` 格式 | 只采纳端到端编码改进；删除值日志，覆盖中文、换行、二进制和导入导出往返 |
| #107 | 域登录拼接被固定替换为 `uid=...,ou=...`，移除 LDAP 角色通配符；错误消息解析假定一定包含 `reason` | 保留 AD/LDAP 配置兼容；可配置 DN；稳健解析错误；分别验证角色映射、空口令与失败流程 |
| #110 | 从 unpkg 引入额外 JS/CSS | 明确离线部署与前端依赖管理方案后实现排序 |
| #93/#104/#105/#103/#102 | 多份 POM 补丁修改同一 Jetty/MySQL 属性，且目标均是历史版本；H2 跨大版本 | 按统一依赖方案替代，不能按 PR 编号顺序累加。H2 升级必须包含导出/导入与旧库副本验证 |

本轮新增测试基线详见 [BUILD_VALIDATION.md](BUILD_VALIDATION.md)，没有将这些待整合 PR 标成测试通过。

## 整合流程

1. 给每个 PR 写清原链接、作者、提交/差异、业务意图、关联 Issue、风险和方案。
2. 建立测试用例（失败的复现用例先行），在独立分支 cherry-pick 部分提交或重做修复。
3. 运行 CI + 集成验证，尤其关注认证、数据库和导入导出。
4. 为本 Fork 新建独立 PR，标明 `Upstream PR: URL` 并保留原作者贡献和来源。
5. 本人审核后合并；外部上游 PR 不会因此自动关闭。对“采纳意图、重写实现”的情况保留说明。

## 审核清单字段

最终每项应补齐：`代码差异已审`、`讨论已审`、`测试编号`、`是否重现`、`合入 Fork 的 PR URL`、`处理状态`。除上述 #109/#86/#50/#87/#107 已完成记录外，其余项目上述字段仍 **待完成**。

- **#88 → Fork PR #12，rewritten**。作者 `vzardlloo`，提交 `f2103f8dcbbc7e8ce355392f280e3908c0913710`；全量差异及讨论已审（无评论）。采纳 UTF-8 读取意图，命令输出编码已在 #9 处理；补齐 HTTP 表单解码、所有节点写入及导出编码。不采纳 LeafBean 原始值 INFO 日志。TextAndNodeValuesIT 在默认 US-ASCII 的真实应用 JAR 下验证中文表单/上传/导出及值空白保留；同时修复父节点自身值不显示（#62），继续按角色遮蔽和 HTML 转义。

- **#54 → Fork PR #13，rewritten**。作者 `clevertension`，提交 `bc047739818b587a430f8a4a7966485421a00aa5`；通过公开git ref核对完整4文件差异，讨论已查（无评论）。采纳可配置根和当前子树搜索，扩展为Home/Export/Import/REST一致的应用命名空间限制；路径使用原生PathUtils校验，拒绝越界/相似前缀/保留根删除，批量目标预检，导航停在根。REST新增可配置根内hosts位置且禁止向根外祖先回退。RootNamespaceIT验证真实JAR；原PR仅默认页面/搜索起点变化，不直接提供完整隔离，故重写。

- **#110 → Fork PR #14，rewritten**。作者 `chenws1012`，提交 `07c47b3373a3d76a45cb0cd9bd625e74e55854cd`；全量差异和讨论已审（无评论）。采纳主页Name/Value及搜索Path/Name/Value排序，使用本地原生JS而非新增unpkg上的Bootstrap Table。稳定文本排序移动原有行，保留选中/编辑事件；type=button避免提交表单，键盘与aria-sort支持。BrowserPagesIT从真实JAR生成合成数据页面，Node/Playwright在CI中的Chrome校验；本机浏览器下载得到空/损坏压缩包，不能声称本地浏览器已通过。

## #41 的拆分决定

作者 `kerwin612`，提交 `2843e7c28f7d41968a890a2dfe0beafae6f9a2bf`；15文件完整差异及讨论已审（无评论）。**rewritten，Fork PR #15**，不按原补丁整体合并。

| 部分 | 处置 | 证据/替代实现 |
| --- | --- | --- |
| 表单/导入值一律trim | 拒绝 | 配置值的前后空格/换行可能有业务意义；#12 TextAndNodeValuesIT验证保留，#13服务端校验路径和单段名称而不修剪值 |
| pathFormat折叠分隔符、update自动upsert | 拒绝 | 不静默重写节点标识或改变Update语义；PathUtils拒绝非规范路径 |
| https/代理标记缺失容错 | 重写采纳 | 缺失默认为false；ConfigDefaultsIT真实JAR验证；env缺失采用prod并验证旧历史不被清空 |
| historyLimit | 重写采纳 | 默认50，1–1000整数，错误配置启动失败且不回显值；所有/筛选查询均限额，标题同步；原补丁空白条件反向且静默吞错 |
| Dao异常时close | 重写采纳 | fetch/insert用finally并检查连接；真实H2缺表异常后无线程连接泄漏，可恢复新连接 |
| Description模型/表/CRUD | deferred | 原V1迁移不可改写；必须新迁移、历史库副本/回滚测试、参数化SQL、文本输出转义和ADMIN/CSRF约束，纳入D01之后功能队列 |
| Description路径拼接SQL及未转义输出 | 拒绝 | 原IN/DELETE/LIKE拼接节点路径，描述直接输出HTML；不引入这些查询/模板 |
| Import/Export重排imports和局部变量、树行外观 | 不采纳原补丁 | 无独立必要行为；连接/编码/模板已在#9/#12/#13更新，外观留后续实际需求 |

验证：独立旧代码工作区HistoryStorageIT 2失败+1错误（限额无效、读异常泄漏、缺env初始化失败）；修复后完整Java8 verify84项（14单元+70集成）通过，含隔离H2历史保留/失败恢复、参数化筛选和缺标记HTTP启动。未更改V1、未创建描述表，未访问真实数据库。

## 依赖PR最终审查与迁移状态（Fork PR #16）

以下完整差异及讨论均已审（均无评论）；#102连接器读取404，已通过公开git ref核对原提交和POM单行修改。原目标版本不作为当前维护目标。具体执行和残余依赖见DEPENDENCIES.md及DEPENDENCY_TREE.txt。

| 上游 | 作者/提交 | 处置及原因 |
| --- | --- | --- |
| #105 | lyhu / e073cf0bb925f19115ccda450aaba9c4ee8e32ee | rewritten（日志部分）：SLF4J1.7.36，显式reload4j1.2.27替代Log4j1并排除ZK旧绑定；Jetty9.4.48/MySQL8.0.30不直接采纳，留迁移验证 |
| #104 | dependabot[bot] / 3635c4474e11a406653ac0fdcef5682c36e0f48f | deferred：Jetty10需Java11且当前已EOL，不能只改属性作为支持声明；现代目标为Java17/Jetty12，EE8可保留javax |
| #93 | dependabot[bot] / 822ac030ec183e585e8200722810dcedca0d803f | deferred：Jetty11需Java11/Jakarta5，与当前javax3.1不兼容且已EOL；采用当前Jetty12整体方案 |
| #103 | dependabot[bot] / bb0c00f4933de6ddd392c7612551262429dea84a | deferred：8.0.28是历史版本；旧Flyway/MySQL5.7建表问题和现有历史库尚需独立新驱动验证，不能用H2测试替代 |
| #102 | dependabot[bot] / 604f1de7b40a966a14c5785f4b95c0ff8c8a9f43 | deferred：H21.3到2跨文件/SQL兼容边界；须旧驱动SCRIPT导出、新驱动新建/RUNSCRIPT和备份恢复，不能直接替换旧库JAR或重写V1 |

#16另将Commons FileUpload1.4升级到Java8兼容的1.6.0，显式512字节part-header上限；原1.4在新回归上错误接受超大头并写入（302），升级后413且无写入。日志包检查确保组装JAR不含旧Log4j/旧SLF4J绑定。全套86项Java回归通过。此阶段不是所有第三方漏洞已清零或现代Java迁移已完成的声明。
