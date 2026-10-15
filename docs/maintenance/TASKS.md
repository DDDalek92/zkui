# ZKUI 自动维护执行清单

更新：2026-10-15。目标：在 DDDalek92/zkui 建立可验证的维护基础，逐项处置上游积压，再进入二次开发。此文件是执行入口；完成一项后继续下一项，无需用户逐项提示。

## 执行规则

- 每项按“正文/讨论和源码审查 → 复现或明确需求 → 实现 → 回归 → 独立 PR → CI → 审查合并”推进。
- 上游 PR 的最终结论可为 adopted、rewritten、superseded 或 deferred，并记录原因及 Fork PR；不能把仅阅读标成已处理。
- Issue 逐项给出 fixed、duplicate、documentation、not-reproducible、enhancement 或 deferred 结论与证据；缺少环境时记录具体阻塞，继续其他可执行项。
- 不更改上游仓库状态，不使用生产数据；数据库破坏性迁移及正式发布留待具体方案验收。
- 在同一仓库开始新一轮工作时，读取本文件及相关验证文档，从首个未完成项继续。此清单本身不是后台定时任务。

## 有序工作队列

| ID | 任务及验收要求 | 状态 |
| --- | --- | --- |
| B01 | 初步审计及上游编号台账 | 已完成，Fork PR #1 |
| B02 | JDK 8/Maven Wrapper/隔离真实 JAR 测试/CI | 已完成，Fork PR #2 |
| S01 | ADMIN 导入授权；拒绝 USER/未知角色且无副作用 | 已完成，Fork PR #3 |
| S02 | 默认 REST 认证、按角色遮蔽密码、失败时保持限制 | 已完成，Fork PR #4；当前 24 项测试 |
| S03 | 登录轮换 Session、旧 Session 失效、HttpOnly、退出不创建 ZK 连接 | 已完成，Fork PR #5；27 项测试 |
| S04 | 登录及所有状态修改入口 CSRF；表单和 multipart/脚本兼容；缺失/错误/跨 Session token 拒绝 | 已完成，Fork PR #6；34 项测试 |
| S05 | URL 导入默认关闭、显式目的主机限制、协议/重定向/超时及上传大小限制 | 已完成，Fork PR #7；完整 URL 白名单，42 项测试 |
| S06 | 敏感日志/历史记录与 HTML 输出转义审计 | 已完成本轮修复，Fork PR #8；45 项测试 |
| R01 | ZK 连接就绪等待、超时失败、重连与资源释放、多地址/监控 | 已完成本轮修复，Fork PR #9；51 项测试 |
| P01 | 整合低风险上游 #87、#109 | 已完成，Fork PR #8/#10；LDAP/ACL 继续按 P03 |
| P02 | UTF-8 端到端、节点自身值、根路径/搜索、离线排序 | UTF-8/节点自身值已完成 #12，根路径/子树搜索已完成 #13；离线排序继续处理 |
| P03 | ZK Digest 客户端认证与 ACL；LDAP/AD 配置及回归 | Digest 已完成 #10；LDAP 基础已完成 #11，真实 AD/LDAPS 部署验证留在 V01 |
| P04 | 审查 #41 混合功能，分别采纳/拒绝并保留数据迁移要求 | 待处理 |
| D01 | 统一依赖审计及 Java 升级，替代过时/互相冲突的依赖 PR | 待处理；#93/#102/#103/#104/#105 |
| I01 | 完成下方 46 个 Issue 正文、评论、复现/文档及结论 | 待处理 |
| V01 | Docker、Windows/macOS、浏览器、H2/MySQL 迁移和回滚验证 | 待处理，不能用 Linux/JDK8 结果替代 |
| V02 | 验收维护版、已知限制和发布说明 | 待处理 |

## 上游 PR 逐项清单

14 项中 #109/#86/#50/#87/#107/#88/#54 已整合；其他条目见逐项状态，预审见 PR_REVIEW.md。

| 上游 | 标题 | 状态/最终证据 |
| --- | --- | --- |
| [#110](https://github.com/DeemOpen/zkui/pull/110) | table sorting | 待处理 |
| [#109](https://github.com/DeemOpen/zkui/pull/109) | correctly display value with new lines | adopted，gouzhuang 原贡献；Fork PR #8，OutputSafetyIT |
| [#107](https://github.com/DeemOpen/zkui/pull/107) | Add ldap auth | rewritten，zhangdong1015 原贡献；Fork PR #11 |
| [#105](https://github.com/DeemOpen/zkui/pull/105) | Update pom.xml | 待处理 |
| [#104](https://github.com/DeemOpen/zkui/pull/104) | Bump jetty-server from 9.4.26.v20200117 to 10.0.10 | 待处理 |
| [#103](https://github.com/DeemOpen/zkui/pull/103) | Bump mysql-connector-java from 8.0.16 to 8.0.28 | 待处理 |
| [#102](https://github.com/DeemOpen/zkui/pull/102) | Bump h2 from 1.3.174 to 2.1.210 | 待处理 |
| [#93](https://github.com/DeemOpen/zkui/pull/93) | Bump jetty.version from 9.4.26.v20200117 to 11.0.1 | 待处理 |
| [#88](https://github.com/DeemOpen/zkui/pull/88) | solve chinese messy code | rewritten，vzardlloo 原贡献；Fork PR #12，US-ASCII JVM 端到端测试 |
| [#87](https://github.com/DeemOpen/zkui/pull/87) | fix the format error in annotation of defaultAcl | rewritten，Fork PR #10，DigestAuthenticationIT/配置校验 |
| [#86](https://github.com/DeemOpen/zkui/pull/86) | Support multiple ZooKeeper server addresses | rewritten，wupeixuan 原贡献；Fork PR #9 |
| [#54](https://github.com/DeemOpen/zkui/pull/54) | add zkRootPath configuration and Search in sub node | rewritten，clevertension 原贡献；Fork PR #13，RootNamespaceIT |
| [#50](https://github.com/DeemOpen/zkui/pull/50) | add auth to the zk client. | rewritten，Fork PR #10，DigestAuthenticationIT/配置校验 |
| [#41](https://github.com/DeemOpen/zkui/pull/41) | trim input or textarea | 待处理 |

## 上游 Issue 逐项清单

当前 46 项均需最终处置；分组见 ISSUE_TRIAGE.md。

| 上游 | 标题 | 状态/最终证据 |
| --- | --- | --- |
| [#112](https://github.com/DeemOpen/zkui/issues/112) | 连接带有户名和密码的zk | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#111](https://github.com/DeemOpen/zkui/issues/111) | Unable to find image 'zkui:latest' locally | 待处理 |
| [#108](https://github.com/DeemOpen/zkui/issues/108) | Does zkui support ipv4/ipv6? | 待处理 |
| [#106](https://github.com/DeemOpen/zkui/issues/106) | LDAP 无法正常登录 | not-reproducible，原 Issue 无正文和环境，不能声称已复现；排查说明见 Fork PR #11 |
| [#100](https://github.com/DeemOpen/zkui/issues/100) | How to change base url | 待处理 |
| [#99](https://github.com/DeemOpen/zkui/issues/99) | How to config the defaultAcl field in config.cfg file? | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#98](https://github.com/DeemOpen/zkui/issues/98) | ERROR ServletUtil:76 - Error :KeeperErrorCode = ConnectionLoss for / | 待处理 |
| [#95](https://github.com/DeemOpen/zkui/issues/95) | 没有看见初始化表结构的sql | 待处理 |
| [#94](https://github.com/DeemOpen/zkui/issues/94) | Login not working on Firefox, works on Chrome | 待处理 |
| [#92](https://github.com/DeemOpen/zkui/issues/92) | Log in successfully and report an error | 待处理 |
| [#90](https://github.com/DeemOpen/zkui/issues/90) | 请教密码是多少？为甚么我死活登录不上去？ | 待处理 |
| [#85](https://github.com/DeemOpen/zkui/issues/85) | The execution time of the zk.getChildren() method in Windos is 9s, but in Linux it is 1s | 待处理 |
| [#84](https://github.com/DeemOpen/zkui/issues/84) | 在windos环境下启动服务查看节点时的响应速度比linux环境下启动服务查看节点的响应速度慢了好几倍,大概(9s:1s) | 待处理 |
| [#80](https://github.com/DeemOpen/zkui/issues/80) | 如果有的节点是digest,登录成功会报错,提示 /node has no auth | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#79](https://github.com/DeemOpen/zkui/issues/79) | 创建节点与目录是怎么区分的？ | 待处理 |
| [#77](https://github.com/DeemOpen/zkui/issues/77) | docker 安装之后登录KeeperErrorCode = ConnectionLoss for / | 待处理 |
| [#76](https://github.com/DeemOpen/zkui/issues/76) | failed to determine Model class name, are you sure models have been instrumented? | 待处理 |
| [#74](https://github.com/DeemOpen/zkui/issues/74) | Disable login function without password | 待处理 |
| [#72](https://github.com/DeemOpen/zkui/issues/72) | mac环境下不能运行 | 待处理 |
| [#71](https://github.com/DeemOpen/zkui/issues/71) | docker support | 待处理 |
| [#70](https://github.com/DeemOpen/zkui/issues/70) | bootstrap.sh can't support Unix. | 待处理 |
| [#69](https://github.com/DeemOpen/zkui/issues/69) | can it support zookeeper password settings? | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#66](https://github.com/DeemOpen/zkui/issues/66) | Can Java 10 Run? | 待处理 |
| [#64](https://github.com/DeemOpen/zkui/issues/64) | Did not wait for the zk session caused every operation become very slow | fixed，Fork PR #9，ConnectionReliabilityIT |
| [#63](https://github.com/DeemOpen/zkui/issues/63) | All parts of a PRIMARY KEY must be NOT NULL; if you need NULL in a key, use UNIQUE instead | 待处理 |
| [#62](https://github.com/DeemOpen/zkui/issues/62) | 当某节点下有子节点时，其 value 在UI中不显示 | fixed，Fork PR #12，TextAndNodeValuesIT |
| [#61](https://github.com/DeemOpen/zkui/issues/61) | KeeperErrorCode = NoNode for /appconfig/hosts  | 待处理 |
| [#60](https://github.com/DeemOpen/zkui/issues/60) | Can not work in JDK1.9 environment | 待处理 |
| [#59](https://github.com/DeemOpen/zkui/issues/59) | node's children with ACL setting cannot be listed. | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#57](https://github.com/DeemOpen/zkui/issues/57) | NumberFormatException while bringing up zkui on linux | 待处理 |
| [#56](https://github.com/DeemOpen/zkui/issues/56) | Exception in thread "main" java.net.BindException: Address already in use | 待处理 |
| [#55](https://github.com/DeemOpen/zkui/issues/55) | Rest API support for import | 待处理 |
| [#53](https://github.com/DeemOpen/zkui/issues/53) | hi sth wrong | 待处理 |
| [#52](https://github.com/DeemOpen/zkui/issues/52) | LDAP 认证失败 | documentation，域/UPN及角色配置说明；原 AD 环境未验证，Fork PR #11 |
| [#51](https://github.com/DeemOpen/zkui/issues/51) | Unable to locate the Javac Compiler in. | 待处理 |
| [#49](https://github.com/DeemOpen/zkui/issues/49) | appconfig/hosts should add/update upon startup | 待处理 |
| [#48](https://github.com/DeemOpen/zkui/issues/48) | Monitor throws an error when zkServer is not end with port | fixed，Fork PR #9，ConnectionReliabilityIT |
| [#43](https://github.com/DeemOpen/zkui/issues/43) | import special characters  not displayed properly | fixed，Fork PR #12，US-ASCII JVM 下真实上传回归 |
| [#42](https://github.com/DeemOpen/zkui/issues/42) | 不支持连接设置密码的zookeeper | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#36](https://github.com/DeemOpen/zkui/issues/36) | KeeperErrorCode = NoAuth for /123  | fixed（Digest 客户端身份/ACL 配置），Fork PR #10 |
| [#33](https://github.com/DeemOpen/zkui/issues/33) | Error :KeeperErrorCode = ConnectionLoss for /  after Login  | 待处理 |
| [#29](https://github.com/DeemOpen/zkui/issues/29) | 左边菜单栏应该增加属性显示，这样比较直观。 | 待处理 |
| [#28](https://github.com/DeemOpen/zkui/issues/28) | ldap authentication - DN is not constructed correctly | fixed，可配置并转义 DN 模板；Fork PR #11，真实 LDAP 绑定测试 |
| [#18](https://github.com/DeemOpen/zkui/issues/18) | Kerberos implementation | 待处理 |
| [#14](https://github.com/DeemOpen/zkui/issues/14) | How do I run this behind an nginx proxy? | 待处理 |
| [#6](https://github.com/DeemOpen/zkui/issues/6) | Failed to view the node that has more than 300k children | 待处理 |
