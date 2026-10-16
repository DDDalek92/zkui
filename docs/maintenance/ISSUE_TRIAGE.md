> 2026-10-16状态校正：#31/#43–#48已合并，以下证据行已同步当前范围；原分类数量保留，不能以当前测试冒充历史环境复现。准确head/运行见CURRENT_VALIDATION.md。

# 上游 Issue 逐项处置（46项）

> 接续更新（2026-10-15）：本次查询仍46个开放Issue/14个开放PR，编号集合无新增。#49的可选hosts创建/版本化更新已由#31受测实现，默认关闭；#6的312763子节点案例已由#27验证有限分页/资源预算，任意宽度/名称长度和#85原环境仍未证明；#63已有#28的MySQL8.4+独立方言及真实5.6逻辑迁移路径，MySQL5.7并未变成受支持运行目标。下表保留原分类（15 fixed、14 documentation、8 not-reproducible、2 duplicate、5 enhancement、2 deferred）；增强类别不等于当前全未实现，受测范围/原环境限制也不能被改称46项全部修好。最新证据见HOSTS_BOOTSTRAP.md、LARGE_TREE_VALIDATION.md、MYSQL_VALIDATION.md及相应Fork PR。

2026-10-15重新查询：上游仍有46个开放Issue、14个开放PR。46项正文及全部分页评论均已读取；空正文使用标题/评论，图片不足时不臆造日志。没有向上游发评论、关闭Issue或改状态。`fixed`仅表示下述受测范围；原部署复验、增强/延期均明确保留，不能把本表称为46个问题全部修好。

本轮分类：fixed 15，documentation 14，not-reproducible 8，duplicate 2，enhancement 5，deferred 2。#59的fixed只覆盖客户端认证，其ACL编辑需求仍是增强。证据入口：[动态验证](SECURITY_VALIDATION.md)、[操作说明](OPERATIONS.md)、[依赖迁移](DEPENDENCIES.md)、[任务队列](TASKS.md)。Fork PR #18在CI运行38698340364完成：三平台各92项Java、Chrome3组、容器构建/非root/登录资源均通过；具体初次失败与修正见CURRENT_VALIDATION.md。

| 上游 | 评论数 | 结论 | 证据、范围和剩余工作 |
| --- | ---: | --- | --- |
| [#112](https://github.com/DeemOpen/zkui/issues/112) | 0 | fixed | Digest客户端身份/ACL分离，#10 DigestAuthenticationIT。 |
| [#111](https://github.com/DeemOpen/zkui/issues/111) | 0 | documentation | #18提供仓库根本地镜像构建及运行；不假定公共zkui:latest存在。 |
| [#108](https://github.com/DeemOpen/zkui/issues/108) | 0 | documentation | #9有地址解析回归，#47另有真实IPv4/IPv6回环ZK/HTTP/REST验证；生产IPv6网络仍未验。 |
| [#106](https://github.com/DeemOpen/zkui/issues/106) | 0 | not-reproducible | 原帖无正文/环境/评论；#11通用LDAP改进不是原故障复现。 |
| [#100](https://github.com/DeemOpen/zkui/issues/100) | 0 | fixed | #17完整baseURL、重定向/资源/cookie/表单；ContextPathIT及前缀Chrome。 |
| [#99](https://github.com/DeemOpen/zkui/issues/99) | 1 | fixed | #10修正ACL示例、Digest凭据与摘要区分；配置/真实JAR验证。 |
| [#98](https://github.com/DeemOpen/zkui/issues/98) | 0 | not-reproducible | Win10截图/ConnectionLoss缺少配置、服务端及网络证据；#9回归不代替原环境。 |
| [#95](https://github.com/DeemOpen/zkui/issues/95) | 0 | documentation | 初始化SQL实际位于src/main/resources/db/migration/V1__Core.sql；OPERATIONS.md。 |
| [#94](https://github.com/DeemOpen/zkui/issues/94) | 0 | not-reproducible | Ubuntu18.04.5、浏览器具体版本/网络/Cookie证据缺失；#46已通过真实Chrome/Firefox HTTPS登录/写入/退出，但不代表该旧环境根因已复现。 |
| [#92](https://github.com/DeemOpen/zkui/issues/92) | 0 | documentation | /mpush NoAuth需核对实际ACL及后端身份；Web登录不授予ZK权限。原ACL未知，不声称已复现。 |
| [#90](https://github.com/DeemOpen/zkui/issues/90) | 1 | documentation | 第三方镜像变量与本项目不一致；当前本地账号来自USER_SET；#44默认拒绝示例账号，隔离演示须显式allowSampleUsers=true。 |
| [#85](https://github.com/DeemOpen/zkui/issues/85) | 0 | not-reproducible | Windows约9s/Linux约1s缺少版本/拓扑/原始计时；基础Windows CI不能证明该性能根因。 |
| [#84](https://github.com/DeemOpen/zkui/issues/84) | 0 | duplicate | 与#85同一getChildren慢现象的中文描述；继续由#85跟踪，根因未确认。 |
| [#80](https://github.com/DeemOpen/zkui/issues/80) | 1 | fixed | Digest访问场景由#10正确添加客户端身份；后端NoAuth仍返回403。 |
| [#79](https://github.com/DeemOpen/zkui/issues/79) | 0 | documentation | 节点数据/子节点并存及foo/bar占位说明见OPERATIONS；#12显示父节点值，未删除旧占位。 |
| [#77](https://github.com/DeemOpen/zkui/issues/77) | 1 | not-reproducible | 缺原docker-compose网络/版本证据；#18解释容器localhost，未采用评论的盲目降级。 |
| [#76](https://github.com/DeemOpen/zkui/issues/76) | 0 | documentation | 旧版需ActiveJDBC插桩；当前改用参数化JDBC/普通History对象，无需IDE插桩，见JAVA17_VALIDATION.md；原MySQL/IDE组合未复验。 |
| [#74](https://github.com/DeemOpen/zkui/issues/74) | 0 | enhancement | 不采纳删除AuthFilter；匿名UI需独立角色/CSRF设计，当前仅显式匿名只读REST兼容。 |
| [#72](https://github.com/DeemOpen/zkui/issues/72) | 6 | not-reproducible | 旧macOS/JDK/Jetty组合不完整，评论存在不同JDK经验；当前macOS14/JDK17已通过CI，不能代替原旧组合复现。 |
| [#71](https://github.com/DeemOpen/zkui/issues/71) | 1 | documentation | #18本地构建/非root/卷/运行说明；公共镜像发布未授权且未实施。 |
| [#70](https://github.com/DeemOpen/zkui/issues/70) | 0 | fixed | *.sh LF规则及#18简化exec启动脚本/容器构建验证，消除CRLF解释器问题。 |
| [#69](https://github.com/DeemOpen/zkui/issues/69) | 2 | fixed | #10增加独立zkAuthScheme/zkAuthCredentials；真实Digest UI/REST验证。 |
| [#66](https://github.com/DeemOpen/zkui/issues/66) | 0 | documentation | 当前要求Java17，Java10不是支持目标；旧环境404未复现，不能以现代基线代替原根因。 |
| [#64](https://github.com/DeemOpen/zkui/issues/64) | 0 | fixed | #9 SyncConnected有界等待/会话复用；ConnectionReliabilityIT覆盖延迟及失败释放。 |
| [#63](https://github.com/DeemOpen/zkui/issues/63) | 0 | deferred | 原MySQL5.7运行环境未验；#28/#40提供MySQL8.4+独立方言和有限5.6副本迁移，不改已应用V1/删除元数据，不能普遍放行旧库。 |
| [#62](https://github.com/DeemOpen/zkui/issues/62) | 2 | fixed | #12显示有子节点的当前节点自身值，保留转义/角色遮蔽，TextAndNodeValuesIT。 |
| [#61](https://github.com/DeemOpen/zkui/issues/61) | 2 | documentation | 默认不补写；#31支持显式可选启动初始化，幂等且覆盖须单独开关，见HOSTS_BOOTSTRAP.md。 |
| [#60](https://github.com/DeemOpen/zkui/issues/60) | 3 | documentation | 当前Java17/Jetty12 EE8整体实现见JAVA17_VALIDATION.md；Java9非支持目标，旧404未复现。 |
| [#59](https://github.com/DeemOpen/zkui/issues/59) | 0 | fixed | 仅客户端认证部分：#10测试Digest访问；逐节点ACL编辑器尚未实现，作为增强留后续。 |
| [#57](https://github.com/DeemOpen/zkui/issues/57) | 0 | not-reproducible | 原帖仅图片，缺可复制堆栈/具体配置与版本；记录端口/超时配置排查，不臆断具体值。 |
| [#56](https://github.com/DeemOpen/zkui/issues/56) | 1 | documentation | BindException端口/进程/实际配置排查见OPERATIONS；不自动停止其他进程，原环境未访问。 |
| [#55](https://github.com/DeemOpen/zkui/issues/55) | 0 | enhancement | 无正文/评论；现有认证+CSRF multipart /import可脚本调用，JSON/纯文本新API协议未实现。 |
| [#53](https://github.com/DeemOpen/zkui/issues/53) | 1 | duplicate | 同#61；#31已受测实现显式可选初始化，默认不写入，不能隐含开启。 |
| [#52](https://github.com/DeemOpen/zkui/issues/52) | 0 | documentation | #11补充域/UPN、DN模板及角色；原Win2008 AD/证书链未访问。 |
| [#51](https://github.com/DeemOpen/zkui/issues/51) | 1 | documentation | 原JAVA_HOME指向JRE且缺编译器；当前完整JDK17/javac/Maven3.9.16 Wrapper已验证，原帖JRE误配另列排查。 |
| [#49](https://github.com/DeemOpen/zkui/issues/49) | 0 | enhancement | #31已实现显式可选创建/更新，默认关闭、幂等、覆盖单独授权开关，见HOSTS_BOOTSTRAP.md。 |
| [#48](https://github.com/DeemOpen/zkui/issues/48) | 0 | fixed | #9原生地址解析器处理chroot/IPv6/端口，坏端点隔离及超时HTTP回归。 |
| [#43](https://github.com/DeemOpen/zkui/issues/43) | 0 | fixed | #12在US-ASCII应用JVM真实上传原例中文文本，导入/写入/导出UTF-8。 |
| [#42](https://github.com/DeemOpen/zkui/issues/42) | 7 | fixed | #10内建Digest客户端认证，无需修改源码；未采用讨论中的外部预编译包。 |
| [#36](https://github.com/DeemOpen/zkui/issues/36) | 5 | fixed | #10配置与真实Digest回归；defaultAcl不再被误当客户端登录凭据。 |
| [#33](https://github.com/DeemOpen/zkui/issues/33) | 3 | not-reproducible | 评论增大超时后仍有人失败；缺同一服务端/网络环境，#9只修已复现的就绪/复用问题。 |
| [#29](https://github.com/DeemOpen/zkui/issues/29) | 1 | enhancement | 左栏属性显示是UI增强，尚未实现；#12父节点值显示不能冒充该需求完成。 |
| [#28](https://github.com/DeemOpen/zkui/issues/28) | 0 | fixed | #11可配置转义DN模板及userDNPattern别名，真实隔离LDAP绑定/角色测试。 |
| [#18](https://github.com/DeemOpen/zkui/issues/18) | 0 | enhancement | #13根命名空间可隔离HA路径；Kerberos/JAAS/SASL及每用户ZK身份仍未实现/未验。 |
| [#14](https://github.com/DeemOpen/zkui/issues/14) | 7 | fixed | #17应用上下文完整支持；nginx保留前缀示例，未运行真实nginx/TLS。 |
| [#6](https://github.com/DeemOpen/zkui/issues/6) | 1 | deferred | #27已验证312763短名子节点读取/分页及资源预算，#41/#43修复删除预检；任意名称长度/资源规模与原用户环境仍未证明。 |

当前接续：Java17/Jetty12、有限历史迁移、超大树边界及显式hosts初始化已有受测实现；继续D01许可证/范围外审计、迁移剩余矩阵与部署门槛，然后左栏属性、逐节点ACL编辑、新导入协议及Kerberos。维护版验收前逐一保留状态；没有真实环境的历史网络/浏览器问题保持not-reproducible，不用当前CI替代原根因。
