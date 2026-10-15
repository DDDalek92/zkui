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
| P02 | UTF-8 端到端、节点自身值、根路径/搜索、离线排序 | 已完成，Fork PR #12/#13/#14；79项Java回归，排序浏览器检查由CI执行 |
| P03 | ZK Digest 客户端认证与 ACL；LDAP/AD 配置及回归 | Digest 已完成 #10；LDAP 基础已完成 #11，真实 AD/LDAPS 部署验证留在 V01 |
| P04 | 审查 #41 混合功能，分别采纳/拒绝并保留数据迁移要求 | 已拆分处置，Fork PR #15；描述功能 deferred，见 PR_REVIEW.md |
| D01 | 统一依赖审计及 Java 升级，替代过时/互相冲突的依赖 PR | #21的Java17/Jetty12 EE8/JDBC、#23的启动/审计失败策略、#28的MySQL8.4/Connector26.7/真实旧库导出与TLS、#29的ZK/Netty/模板/IO已完成。#32–#36补齐有限副本结构/锁/导入中断/差量恢复及文件格式保护。本增量实现H2 2.5.252/Flyway13.10.3运行时与旧V1桥接的真实Main/HTTP/审计ID/重启，见[MODERN_H2_RUNTIME.md](MODERN_H2_RUNTIME.md)，准入以准确新head全CI为准。MySQL运行Flyway2仍未移除，现代API/module/checksum兼容、实际权限/磁盘失败、更广历史/时区夹具与安全/许可证/SBOM审计未完成；不等于D01或任意旧库迁移全完成。 |
| I01 | 完成下方 46 个 Issue 正文、评论、复现/文档及结论 | 本轮审查分类完成，Fork PR #19；15 fixed（#59部分）、14 documentation、8 not-reproducible、2 duplicate、5 enhancement、2 deferred；后续仍按限制执行 |
| V01 | Docker、Windows/macOS、浏览器、H2/MySQL 迁移和回滚验证 | #18 CI三平台各92项、Chrome3组、容器构建/非root/登录资源通过；真实AD/LDAPS、双栈和历史DB迁移仍待验证 |
| I02 | 超大树工作量边界、报文限制和真实规模负载验证（#6/#85） | 已实现100项分页、8MiB可配置响应缓冲及迭代遍历/删除预检预算；312763子节点真实服务读取、深度/数据预算/跨根删除及重叠选择回归已由#27三平台各112项CI通过，见LARGE_TREE_VALIDATION.md；原#85环境及9s:1s根因仍未复现 |
| E01 | 可选hosts初始化、左栏属性、逐节点ACL编辑、新导入协议及Kerberos需求 | hosts显式初始化/幂等/单独覆盖开关已由#31准确head六job验证并合并，默认关闭，见HOSTS_BOOTSTRAP.md；其他增强尚未实现 |
| V02 | 验收维护版、已知限制和发布说明 | 维护预览说明和验证报告已准备，Fork PR #19；正式发行仍受D01/历史DB与部署验证阻塞，未发布 |

## 上游 PR 逐项清单

14项全部完成本轮差异/讨论审查并给出处置：8项整合，#41部分重写，#105日志/Jetty部分重写；#93/#104由Java17/Jetty12 EE8整体方案替代，#103由#28的Connector26.7/MySQL8.4方案替代，#102仍延期。替代/部分采纳/延期不等于全部功能修复，数据库与其余依赖工作仍在D01；见PR_REVIEW.md。

| 上游 | 标题 | 状态/最终证据 |
| --- | --- | --- |
| [#110](https://github.com/DeemOpen/zkui/pull/110) | table sorting | rewritten，chenws1012 原贡献；Fork PR #14，本地脚本及真实模板浏览器检查 |
| [#109](https://github.com/DeemOpen/zkui/pull/109) | correctly display value with new lines | adopted，gouzhuang 原贡献；Fork PR #8，OutputSafetyIT |
| [#107](https://github.com/DeemOpen/zkui/pull/107) | Add ldap auth | rewritten，zhangdong1015 原贡献；Fork PR #11 |
| [#105](https://github.com/DeemOpen/zkui/pull/105) | Update pom.xml | rewritten（日志/Jetty部分），MySQL迁移仍deferred；#16及JAVA17_VALIDATION.md |
| [#104](https://github.com/DeemOpen/zkui/pull/104) | Bump jetty-server from 9.4.26.v20200117 to 10.0.10 | superseded：Java17/Jetty12.1.14全部Core/EE8模块迁移，不采用已EOL的10.0.10；JAVA17_VALIDATION.md |
| [#103](https://github.com/DeemOpen/zkui/pull/103) | Bump mysql-connector-java from 8.0.16 to 8.0.28 | superseded，#28的Connector26.7/MySQL8.4方案及真实5.6逻辑迁移/TLS验证；不采用旧8.0.28 |
| [#102](https://github.com/DeemOpen/zkui/pull/102) | Bump h2 from 1.3.174 to 2.1.210 | deferred，不直接打开旧库；导出/新建/导入和回滚验证，D01/V01 |
| [#93](https://github.com/DeemOpen/zkui/pull/93) | Bump jetty.version from 9.4.26.v20200117 to 11.0.1 | superseded：Java17/Jetty12 EE8保留javax，整体迁移代替已EOL的Jetty11；JAVA17_VALIDATION.md |
| [#88](https://github.com/DeemOpen/zkui/pull/88) | solve chinese messy code | rewritten，vzardlloo 原贡献；Fork PR #12，US-ASCII JVM 端到端测试 |
| [#87](https://github.com/DeemOpen/zkui/pull/87) | fix the format error in annotation of defaultAcl | rewritten，Fork PR #10，DigestAuthenticationIT/配置校验 |
| [#86](https://github.com/DeemOpen/zkui/pull/86) | Support multiple ZooKeeper server addresses | rewritten，wupeixuan 原贡献；Fork PR #9 |
| [#54](https://github.com/DeemOpen/zkui/pull/54) | add zkRootPath configuration and Search in sub node | rewritten，clevertension 原贡献；Fork PR #13，RootNamespaceIT |
| [#50](https://github.com/DeemOpen/zkui/pull/50) | add auth to the zk client. | rewritten，Fork PR #10，DigestAuthenticationIT/配置校验 |
| [#41](https://github.com/DeemOpen/zkui/pull/41) | trim input or textarea | rewritten（默认值/历史条数/关闭连接）；拒绝值trim和不安全SQL，描述功能deferred，Fork PR #15 |

## 上游 Issue 逐项清单

46项完成正文/评论审核和逐项分类，证据/限制见 ISSUE_TRIAGE.md；固定、文档、无法复现、增强和延期不混称全部修好。

| 上游 | 标题 | 状态/最终证据 |
| --- | --- | --- |
| [#112](https://github.com/DeemOpen/zkui/issues/112) | 连接带有户名和密码的zk | fixed；Digest客户端身份/ACL分离，#10 DigestAuthenticationIT。 |
| [#111](https://github.com/DeemOpen/zkui/issues/111) | Unable to find image 'zkui:latest' locally | documentation；#18提供仓库根本地镜像构建及运行；不假定公共zkui:latest存在。 |
| [#108](https://github.com/DeemOpen/zkui/issues/108) | Does zkui support ipv4/ipv6? | documentation；#9原生IPv4/IPv6/chroot解析回归；真实IPv6网络未验，保留V01。 |
| [#106](https://github.com/DeemOpen/zkui/issues/106) | LDAP 无法正常登录 | not-reproducible；原帖无正文/环境/评论；#11通用LDAP改进不是原故障复现。 |
| [#100](https://github.com/DeemOpen/zkui/issues/100) | How to change base url | fixed；#17完整baseURL、重定向/资源/cookie/表单；ContextPathIT及前缀Chrome。 |
| [#99](https://github.com/DeemOpen/zkui/issues/99) | How to config the defaultAcl field in config.cfg file? | fixed；#10修正ACL示例、Digest凭据与摘要区分；配置/真实JAR验证。 |
| [#98](https://github.com/DeemOpen/zkui/issues/98) | ERROR ServletUtil:76 - Error :KeeperErrorCode = ConnectionLoss for / | not-reproducible；Win10截图/ConnectionLoss缺少配置、服务端及网络证据；#9回归不代替原环境。 |
| [#95](https://github.com/DeemOpen/zkui/issues/95) | 没有看见初始化表结构的sql | documentation；初始化SQL实际位于src/main/resources/db/migration/V1__Core.sql；OPERATIONS.md。 |
| [#94](https://github.com/DeemOpen/zkui/issues/94) | Login not working on Firefox, works on Chrome | not-reproducible；Ubuntu18.04.5、浏览器具体版本/网络/Cookie证据缺失；当前Chrome检查不代表Firefox登录。 |
| [#92](https://github.com/DeemOpen/zkui/issues/92) | Log in successfully and report an error | documentation；/mpush NoAuth需核对实际ACL及后端身份；Web登录不授予ZK权限。原ACL未知，不声称已复现。 |
| [#90](https://github.com/DeemOpen/zkui/issues/90) | 请教密码是多少？为甚么我死活登录不上去？ | documentation；第三方镜像变量与本项目不一致；#18列明USER_SET及sample admin/manager。 |
| [#85](https://github.com/DeemOpen/zkui/issues/85) | The execution time of the zk.getChildren() method in Windos is 9s, but in Linux it is 1s | not-reproducible；Windows约9s/Linux约1s缺少版本/拓扑/原始计时；基础Windows CI不能证明该性能根因。 |
| [#84](https://github.com/DeemOpen/zkui/issues/84) | 在windos环境下启动服务查看节点时的响应速度比linux环境下启动服务查看节点的响应速度慢了好几倍,大概(9s:1s) | duplicate；与#85同一getChildren慢现象的中文描述；继续由#85跟踪，根因未确认。 |
| [#80](https://github.com/DeemOpen/zkui/issues/80) | 如果有的节点是digest,登录成功会报错,提示 /node has no auth | fixed；Digest访问场景由#10正确添加客户端身份；后端NoAuth仍返回403。 |
| [#79](https://github.com/DeemOpen/zkui/issues/79) | 创建节点与目录是怎么区分的？ | documentation；节点数据/子节点并存及foo/bar占位说明见OPERATIONS；#12显示父节点值，未删除旧占位。 |
| [#77](https://github.com/DeemOpen/zkui/issues/77) | docker 安装之后登录KeeperErrorCode = ConnectionLoss for / | not-reproducible；缺原docker-compose网络/版本证据；#18解释容器localhost，未采用评论的盲目降级。 |
| [#76](https://github.com/DeemOpen/zkui/issues/76) | failed to determine Model class name, are you sure models have been instrumented? | documentation；旧插桩已由JDBC/普通History对象替代，见JAVA17_VALIDATION.md；原MySQL/IDE组合未复验。 |
| [#74](https://github.com/DeemOpen/zkui/issues/74) | Disable login function without password | enhancement；不采纳删除AuthFilter；匿名UI需独立角色/CSRF设计，当前仅显式匿名只读REST兼容。 |
| [#72](https://github.com/DeemOpen/zkui/issues/72) | mac环境下不能运行 | not-reproducible；旧macOS/JDK/Jetty组合不完整，评论存在不同JDK经验；#18仅验证当前macOS14/JDK8组合。 |
| [#71](https://github.com/DeemOpen/zkui/issues/71) | docker support | documentation；#18本地构建/非root/卷/运行说明；公共镜像发布未授权且未实施。 |
| [#70](https://github.com/DeemOpen/zkui/issues/70) | bootstrap.sh can't support Unix. | fixed；*.sh LF规则及#18简化exec启动脚本/容器构建验证，消除CRLF解释器问题。 |
| [#69](https://github.com/DeemOpen/zkui/issues/69) | can it support zookeeper password settings? | fixed；#10增加独立zkAuthScheme/zkAuthCredentials；真实Digest UI/REST验证。 |
| [#66](https://github.com/DeemOpen/zkui/issues/66) | Can Java 10 Run? | documentation；当前要求Java17，Java10非支持目标；旧环境404不视为已复现，见JAVA17_VALIDATION.md。 |
| [#64](https://github.com/DeemOpen/zkui/issues/64) | Did not wait for the zk session caused every operation become very slow | fixed；#9 SyncConnected有界等待/会话复用；ConnectionReliabilityIT覆盖延迟及失败释放。 |
| [#63](https://github.com/DeemOpen/zkui/issues/63) | All parts of a PRIMARY KEY must be NOT NULL; if you need NULL in a key, use UNIQUE instead | deferred；MySQL5.7默认NULL主键DDL问题已确认；须独立方言/历史库迁移，不改已应用V1或删除元数据。 |
| [#62](https://github.com/DeemOpen/zkui/issues/62) | 当某节点下有子节点时，其 value 在UI中不显示 | fixed；#12显示有子节点的当前节点自身值，保留转义/角色遮蔽，TextAndNodeValuesIT。 |
| [#61](https://github.com/DeemOpen/zkui/issues/61) | KeeperErrorCode = NoNode for /appconfig/hosts  | documentation；缺/appconfig/hosts需明确初始化；OPERATIONS含示例和chroot/根说明，启动不自动补写。 |
| [#60](https://github.com/DeemOpen/zkui/issues/60) | Can not work in JDK1.9 environment | documentation；当前Java17/Jetty12 EE8整体实现见JAVA17_VALIDATION.md；Java9非支持目标，旧404未复现。 |
| [#59](https://github.com/DeemOpen/zkui/issues/59) | node's children with ACL setting cannot be listed. | fixed；仅客户端认证部分：#10测试Digest访问；逐节点ACL编辑器尚未实现，作为增强留后续。 |
| [#57](https://github.com/DeemOpen/zkui/issues/57) | NumberFormatException while bringing up zkui on linux | not-reproducible；原帖仅图片，缺可复制堆栈/具体配置与版本；记录端口/超时配置排查，不臆断具体值。 |
| [#56](https://github.com/DeemOpen/zkui/issues/56) | Exception in thread "main" java.net.BindException: Address already in use | documentation；BindException端口/进程/实际配置排查见OPERATIONS；不自动停止其他进程，原环境未访问。 |
| [#55](https://github.com/DeemOpen/zkui/issues/55) | Rest API support for import | enhancement；无正文/评论；现有认证+CSRF multipart /import可脚本调用，JSON/纯文本新API协议未实现。 |
| [#53](https://github.com/DeemOpen/zkui/issues/53) | hi sth wrong | duplicate；同#61的/appconfig/hosts缺失；沿用明确初始化说明，不声称自动创建已完成。 |
| [#52](https://github.com/DeemOpen/zkui/issues/52) | LDAP 认证失败 | documentation；#11补充域/UPN、DN模板及角色；原Win2008 AD/证书链未访问。 |
| [#51](https://github.com/DeemOpen/zkui/issues/51) | Unable to locate the Javac Compiler in. | documentation；原JAVA_HOME指向JRE且缺编译器；完整JDK8/javac/Wrapper说明与可执行构建基线。 |
| [#49](https://github.com/DeemOpen/zkui/issues/49) | appconfig/hosts should add/update upon startup | enhancement；启动自动创建/更新业务hosts元数据未实现；当前要求显式初始化，避免覆盖用户配置。 |
| [#48](https://github.com/DeemOpen/zkui/issues/48) | Monitor throws an error when zkServer is not end with port | fixed；#9原生地址解析器处理chroot/IPv6/端口，坏端点隔离及超时HTTP回归。 |
| [#43](https://github.com/DeemOpen/zkui/issues/43) | import special characters  not displayed properly | fixed；#12在US-ASCII应用JVM真实上传原例中文文本，导入/写入/导出UTF-8。 |
| [#42](https://github.com/DeemOpen/zkui/issues/42) | 不支持连接设置密码的zookeeper | fixed；#10内建Digest客户端认证，无需修改源码；未采用讨论中的外部预编译包。 |
| [#36](https://github.com/DeemOpen/zkui/issues/36) | KeeperErrorCode = NoAuth for /123  | fixed；#10配置与真实Digest回归；defaultAcl不再被误当客户端登录凭据。 |
| [#33](https://github.com/DeemOpen/zkui/issues/33) | Error :KeeperErrorCode = ConnectionLoss for /  after Login  | not-reproducible；评论增大超时后仍有人失败；缺同一服务端/网络环境，#9只修已复现的就绪/复用问题。 |
| [#29](https://github.com/DeemOpen/zkui/issues/29) | 左边菜单栏应该增加属性显示，这样比较直观。 | enhancement；左栏属性显示是UI增强，尚未实现；#12父节点值显示不能冒充该需求完成。 |
| [#28](https://github.com/DeemOpen/zkui/issues/28) | ldap authentication - DN is not constructed correctly | fixed；#11可配置转义DN模板及userDNPattern别名，真实隔离LDAP绑定/角色测试。 |
| [#18](https://github.com/DeemOpen/zkui/issues/18) | Kerberos implementation | enhancement；#13根命名空间可隔离HA路径；Kerberos/JAAS/SASL及每用户ZK身份仍未实现/未验。 |
| [#14](https://github.com/DeemOpen/zkui/issues/14) | How do I run this behind an nginx proxy? | fixed；#17应用上下文完整支持；nginx保留前缀示例，未运行真实nginx/TLS。 |
| [#6](https://github.com/DeemOpen/zkui/issues/6) | Failed to view the node that has more than 300k children | deferred；312763子节点的大报文、遍历/渲染成本需独立负载及资源上限方案；普通CRUD不证明已解决。 |

## 本轮接续与优先队列（2026-10-15）

- #36准确head8335b8f6dbc43c3951ef1ef93ab8137d2537aba9的PR38797311625及push38797301172全部六job/三平台各137项成功后已审查合并；master a41ec71的push38797983943也六job成功。重新核查Fork无未完成PR，上游仍14/46且无新增；承接既有干净隔离分支，不复建已合并保护器。
- 本增量实现H2 2.5.252/Flyway13.10.3运行时、新建方言及原V1离线桥接后的真实Main/HTTP/新审计ID/同路径重启；未桥接、未知版本/标记和schema漂移拒绝，现代H2禁止dev自动clean。真正旧驱动/旧Flyway在子进程隔离，不把新版fat JAR当旧引擎。见[MODERN_H2_RUNTIME.md](MODERN_H2_RUNTIME.md)，准确新head全CI仍是合并门槛。
- 后续顺序：MySQL现代Flyway API/module及原checksum兼容/移除运行Flyway2；实际权限/磁盘失败、更广历史夹具和时区验收；SBOM/漏洞/许可证及剩余部署/增强。保留Flyway关于H22.5高于厂商已验2.3.232的警告和有限自验证范围。D01整体、所有历史库/原Windows根因及正式发行均未叫作完成。

- #35已核对准确head81aa63fcc21c39faa56a65d72eb0bcc6e18cca17的PR运行38789692993（attempt2）及push38789687026全部六job成功后审查合并；三平台各127项、Chrome3组及容器/MySQL/旧ZK通过。容器首轮依赖解析失败后仅重跑失败job，同head通过，没有更改代码/依赖或关闭校验。master为2c9c68e，其push38790758128 success，无未完成Fork PR；上游仍14/46且无新增。
- 在该最新基线隔离复现并修复实际应用的H2格式错配静默建空库风险：连接/Flyway/dev清库前拒绝不兼容同base文件，真实JAR拒绝启动并保留两库hash，见[H2_FILE_FORMAT_GUARD.md](H2_FILE_FORMAT_GUARD.md)。本增量不切换运行H2/Flyway，不是完整新版Main/HTTP验收；准确新head全部CI须通过后才合并。完成后继续完整新版新建/历史桥接应用及MySQL旧API/checksum兼容，不跳到发行。

- 当前远程基线4eff9d8：#33准确head69d367f的PR运行38781568897及push运行38781523517全部六job/三平台各121项成功后已审查合并；基线push运行38782275556也success。本地Java17执行可用；没有未完成Fork PR，不复建已合并增量。
- #32固定V1结构/PK/索引/约束漂移拒绝、#33候选锁竞争/空闲持锁进程终止恢复已完成有限夹具验收，见H2_SCHEMA_VALIDATION.md、H2_LOCK_RECOVERY.md；#30合法NULL历史页与#31默认关闭hosts初始化也已组合验收。均不表示完整H2应用升级完成。
- 本次从当前远程基线隔离继续受控原生RUNSCRIPT中断半成品拒绝、H2显式只读目标90097导入拒绝，见[H2_IMPORT_FAILURES.md](H2_IMPORT_FAILURES.md)。定向9项及本地完整123项通过，完整日志/报告一致；准确新head六job仍待验收，本增量最终结果以对应Fork PR准确head Checks/审查记录为准。
- 上游重新查询仍14个开放PR、46个开放Issue，编号集合无新增；分类/延期不等于全部修复。#49默认关闭hosts、#6有限312763子节点边界、#63 MySQL8.4+路径的具体限制继续保留。
- #34已在准确head8a3157d的PR运行38787905991及push运行38787853716全部六job/三平台各123项成功后审查合并。其上待CI为提交时记录。最新隔离基线498fe7f继续仅追加审计差量保全/旧驱动副本恢复，见[H2_AUDIT_DELTA.md](H2_AUDIT_DELTA.md)；重复回放、ID/重开、基线修改删除拒绝、ID内容冲突整批回滚及载荷完整性拒绝纳入本增量。本地最终127项报告零失败/错误/跳过（文本日志截断），准确新head六job仍待验收，最终证据以对应PR准确head Checks/审查记录为准。
- 下一项优先完整新版H2/Flyway打包应用Main/HTTP及其MySQL旧API/checksum兼容；有限离线追加差量不等于在线双写/通用降级，H2只读模式不等于实际文件系统权限/磁盘故障，受控中断不代表任意写页崩溃。实际不可写/磁盘故障、更广夹具、依赖审计和其余部署/增强/发行门槛继续保留。I02现有边界不代表原Windows9s:1s故障根因已复现。
