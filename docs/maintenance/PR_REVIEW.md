# 上游开放 PR 审核台账（初稿）

> 2026-10-15；基线 `1b3e7c1`。上游：[DeemOpen/zkui/pulls](https://github.com/DeemOpen/zkui/pulls)。共 14 个开放 PR。分类是**静态预审建议**，不等于通过测试、批准合并或全部逐行审核。每项整合前须检查完整差异、讨论、许可证、构建与回归测试。任何上游 PR 均不能直接合并到本 Fork 的 `master`。

| PR | 标题 / 目标 | 初步决策 | 关键检查和依赖 |
| --- | --- | --- | --- |
| [#87](https://github.com/DeemOpen/zkui/pull/87) | 修正 defaultAcl 示例注释 | 已重做，Fork PR #10 | JSON 示例修正；凭据/ACL 分离，Digest/Auth ACL 回归 |
| [#109](https://github.com/DeemOpen/zkui/pull/109) | 保留多行节点文本空格和换行 | 已采纳并扩展，Fork PR #8 | OutputSafetyIT；HTML 转义及搜索结果同步处理，真实浏览器待验证 |
| [#54](https://github.com/DeemOpen/zkui/pull/54) | 配置根路径、子树搜索 | 修改后整合候选 | 根路径安全边界、搜索性能、空路径与路径遍历 |
| [#110](https://github.com/DeemOpen/zkui/pull/110) | 表格排序 | 修改后整合候选 | 外部 CDN 供应链和离线部署、前端版本适配 |
| [#88](https://github.com/DeemOpen/zkui/pull/88) | 修复中文乱码 | 修改后整合候选 | 确保 UTF-8 端到端一致；新增日志可能泄露原始节点值，需删除/脱敏 |
| [#86](https://github.com/DeemOpen/zkui/pull/86) | 多 ZooKeeper 地址与监控 | 已重做并验证，Fork PR #9 | 完整 ensemble、chroot、监控失败隔离及超时；#64/#48 有直接回归 |
| [#50](https://github.com/DeemOpen/zkui/pull/50) | ZooKeeper 客户端认证 | 已重做，Fork PR #10 | 独立客户端凭据、正确/错误身份、只读 ZK 权限及创建者 ACL 测试 |
| [#107](https://github.com/DeemOpen/zkui/pull/107) | LDAP 登录调整 | 需安全审计后整合或重写 | 域/OU、空口令、TLS、超时、角色映射、回归 #28/#52/#106 |
| [#41](https://github.com/DeemOpen/zkui/pull/41) | 输入修剪及其他功能 | 拆分后评估 | 涉及 15 文件、数据库迁移/描述能力，不能视为单一 trim 补丁 |
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

最终每项应补齐：`代码差异已审`、`讨论已审`、`测试编号`、`是否重现`、`合入 Fork 的 PR URL`、`处理状态`。除上述 #109/#86/#50/#87 已完成记录外，其余项目上述字段仍 **待完成**。
