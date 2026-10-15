# 上游 Issue 逐项处置（46项）

2026-10-15重新查询：上游仍有46个开放Issue、14个开放PR。46项正文及全部分页评论均已读取；空正文使用标题/评论，图片不足时不臆造日志。没有向上游发评论、关闭Issue或改状态。`fixed`仅表示下述受测范围；原部署复验、增强/延期均明确保留，不能把本表称为46个问题全部修好。

本轮分类：fixed 15，documentation 14，not-reproducible 8，duplicate 2，enhancement 5，deferred 2。#59的fixed只覆盖客户端认证，其ACL编辑需求仍是增强。证据入口：[动态验证](SECURITY_VALIDATION.md)、[操作说明](OPERATIONS.md)、[依赖迁移](DEPENDENCIES.md)、[任务队列](TASKS.md)。Fork PR #18在CI运行38698340364完成：三平台各92项Java、Chrome3组、容器构建/非root/登录资源均通过；具体初次失败与修正见CURRENT_VALIDATION.md。

| 上游 | 评论数 | 结论 | 证据、范围和剩余工作 |
| --- | ---: | --- | --- |
| [#112](https://github.com/DeemOpen/zkui/issues/112) | 0 | fixed | Digest客户端身份/ACL分离，#10 DigestAuthenticationIT。 |
| [#111](https://github.com/DeemOpen/zkui/issues/111) | 0 | documentation | #18提供仓库根本地镜像构建及运行；不假定公共zkui:latest存在。 |
| [#108](https://github.com/DeemOpen/zkui/issues/108) | 0 | documentation | #9原生IPv4/IPv6/chroot解析回归；真实IPv6网络未验，保留V01。 |
| [#106](https://github.com/DeemOpen/zkui/issues/106) | 0 | not-reproducible | 原帖无正文/环境/评论；#11通用LDAP改进不是原故障复现。 |
| [#100](https://github.com/DeemOpen/zkui/issues/100) | 0 | fixed | #17完整baseURL、重定向/资源/cookie/表单；ContextPathIT及前缀Chrome。 |
| [#99](https://github.com/DeemOpen/zkui/issues/99) | 1 | fixed | #10修正ACL示例、Digest凭据与摘要区分；配置/真实JAR验证。 |
| [#98](https://github.com/DeemOpen/zkui/issues/98) | 0 | not-reproducible | Win10截图/ConnectionLoss缺少配置、服务端及网络证据；#9回归不代替原环境。 |
| [#95](https://github.com/DeemOpen/zkui/issues/95) | 0 | documentation | 初始化SQL实际位于src/main/resources/db/migration/V1__Core.sql；OPERATIONS.md。 |
| [#94](https://github.com/DeemOpen/zkui/issues/94) | 0 | not-reproducible | Ubuntu18.04.5、浏览器具体版本/网络/Cookie证据缺失；当前Chrome检查不代表Firefox登录。 |
| [#92](https://github.com/DeemOpen/zkui/issues/92) | 0 | documentation | /mpush NoAuth需核对实际ACL及后端身份；Web登录不授予ZK权限。原ACL未知，不声称已复现。 |
| [#90](https://github.com/DeemOpen/zkui/issues/90) | 1 | documentation | 第三方镜像变量与本项目不一致；#18列明USER_SET及sample admin/manager。 |
| [#85](https://github.com/DeemOpen/zkui/issues/85) | 0 | not-reproducible | Windows约9s/Linux约1s缺少版本/拓扑/原始计时；基础Windows CI不能证明该性能根因。 |
| [#84](https://github.com/DeemOpen/zkui/issues/84) | 0 | duplicate | 与#85同一getChildren慢现象的中文描述；继续由#85跟踪，根因未确认。 |
| [#80](https://github.com/DeemOpen/zkui/issues/80) | 1 | fixed | Digest访问场景由#10正确添加客户端身份；后端NoAuth仍返回403。 |
| [#79](https://github.com/DeemOpen/zkui/issues/79) | 0 | documentation | 节点数据/子节点并存及foo/bar占位说明见OPERATIONS；#12显示父节点值，未删除旧占位。 |
| [#77](https://github.com/DeemOpen/zkui/issues/77) | 1 | not-reproducible | 缺原docker-compose网络/版本证据；#18解释容器localhost，未采用评论的盲目降级。 |
| [#76](https://github.com/DeemOpen/zkui/issues/76) | 0 | documentation | 旧版需ActiveJDBC插桩；当前改用参数化JDBC/普通History对象，无需IDE插桩，见JAVA17_VALIDATION.md；原MySQL/IDE组合未复验。 |
| [#74](https://github.com/DeemOpen/zkui/issues/74) | 0 | enhancement | 不采纳删除AuthFilter；匿名UI需独立角色/CSRF设计，当前仅显式匿名只读REST兼容。 |
| [#72](https://github.com/DeemOpen/zkui/issues/72) | 6 | not-reproducible | 旧macOS/JDK/Jetty组合不完整，评论存在不同JDK经验；#18仅验证当前macOS14/JDK8组合。 |
| [#71](https://github.com/DeemOpen/zkui/issues/71) | 1 | documentation | #18本地构建/非root/卷/运行说明；公共镜像发布未授权且未实施。 |
| [#70](https://github.com/DeemOpen/zkui/issues/70) | 0 | fixed | *.sh LF规则及#18简化exec启动脚本/容器构建验证，消除CRLF解释器问题。 |
| [#69](https://github.com/DeemOpen/zkui/issues/69) | 2 | fixed | #10增加独立zkAuthScheme/zkAuthCredentials；真实Digest UI/REST验证。 |
| [#66](https://github.com/DeemOpen/zkui/issues/66) | 0 | documentation | 当前要求Java17，Java10不是支持目标；旧环境404未复现，不能以现代基线代替原根因。 |
| [#64](https://github.com/DeemOpen/zkui/issues/64) | 0 | fixed | #9 SyncConnected有界等待/会话复用；ConnectionReliabilityIT覆盖延迟及失败释放。 |
| [#63](https://github.com/DeemOpen/zkui/issues/63) | 0 | deferred | MySQL5.7默认NULL主键DDL问题已确认；须独立方言/历史库迁移，不改已应用V1或删除元数据。 |
| [#62](https://github.com/DeemOpen/zkui/issues/62) | 2 | fixed | #12显示有子节点的当前节点自身值，保留转义/角色遮蔽，TextAndNodeValuesIT。 |
| [#61](https://github.com/DeemOpen/zkui/issues/61) | 2 | documentation | 缺/appconfig/hosts需明确初始化；OPERATIONS含示例和chroot/根说明，启动不自动补写。 |
| [#60](https://github.com/DeemOpen/zkui/issues/60) | 3 | documentation | 当前Java17/Jetty12 EE8整体实现见JAVA17_VALIDATION.md；Java9非支持目标，旧404未复现。 |
| [#59](https://github.com/DeemOpen/zkui/issues/59) | 0 | fixed | 仅客户端认证部分：#10测试Digest访问；逐节点ACL编辑器尚未实现，作为增强留后续。 |
| [#57](https://github.com/DeemOpen/zkui/issues/57) | 0 | not-reproducible | 原帖仅图片，缺可复制堆栈/具体配置与版本；记录端口/超时配置排查，不臆断具体值。 |
| [#56](https://github.com/DeemOpen/zkui/issues/56) | 1 | documentation | BindException端口/进程/实际配置排查见OPERATIONS；不自动停止其他进程，原环境未访问。 |
| [#55](https://github.com/DeemOpen/zkui/issues/55) | 0 | enhancement | 无正文/评论；现有认证+CSRF multipart /import可脚本调用，JSON/纯文本新API协议未实现。 |
| [#53](https://github.com/DeemOpen/zkui/issues/53) | 1 | duplicate | 同#61的/appconfig/hosts缺失；沿用明确初始化说明，不声称自动创建已完成。 |
| [#52](https://github.com/DeemOpen/zkui/issues/52) | 0 | documentation | #11补充域/UPN、DN模板及角色；原Win2008 AD/证书链未访问。 |
| [#51](https://github.com/DeemOpen/zkui/issues/51) | 1 | documentation | 原JAVA_HOME指向JRE且缺编译器；完整JDK8/javac/Wrapper说明与可执行构建基线。 |
| [#49](https://github.com/DeemOpen/zkui/issues/49) | 0 | enhancement | 启动自动创建/更新业务hosts元数据未实现；当前要求显式初始化，避免覆盖用户配置。 |
| [#48](https://github.com/DeemOpen/zkui/issues/48) | 0 | fixed | #9原生地址解析器处理chroot/IPv6/端口，坏端点隔离及超时HTTP回归。 |
| [#43](https://github.com/DeemOpen/zkui/issues/43) | 0 | fixed | #12在US-ASCII应用JVM真实上传原例中文文本，导入/写入/导出UTF-8。 |
| [#42](https://github.com/DeemOpen/zkui/issues/42) | 7 | fixed | #10内建Digest客户端认证，无需修改源码；未采用讨论中的外部预编译包。 |
| [#36](https://github.com/DeemOpen/zkui/issues/36) | 5 | fixed | #10配置与真实Digest回归；defaultAcl不再被误当客户端登录凭据。 |
| [#33](https://github.com/DeemOpen/zkui/issues/33) | 3 | not-reproducible | 评论增大超时后仍有人失败；缺同一服务端/网络环境，#9只修已复现的就绪/复用问题。 |
| [#29](https://github.com/DeemOpen/zkui/issues/29) | 1 | enhancement | 左栏属性显示是UI增强，尚未实现；#12父节点值显示不能冒充该需求完成。 |
| [#28](https://github.com/DeemOpen/zkui/issues/28) | 0 | fixed | #11可配置转义DN模板及userDNPattern别名，真实隔离LDAP绑定/角色测试。 |
| [#18](https://github.com/DeemOpen/zkui/issues/18) | 0 | enhancement | #13根命名空间可隔离HA路径；Kerberos/JAAS/SASL及每用户ZK身份仍未实现/未验。 |
| [#14](https://github.com/DeemOpen/zkui/issues/14) | 7 | fixed | #17应用上下文完整支持；nginx保留前缀示例，未运行真实nginx/TLS。 |
| [#6](https://github.com/DeemOpen/zkui/issues/6) | 1 | deferred | 312763子节点的大报文、遍历/渲染成本需独立负载及资源上限方案；普通CRUD不证明已解决。 |

具体后续：D01现代Java/Jetty与历史数据库迁移、I02超大树工作量边界/性能、E01可选hosts初始化、E02左栏属性、E03逐节点ACL编辑、E04新导入协议及E05Kerberos。维护版验收前逐一保留状态；没有真实环境的历史网络/浏览器问题保持not-reproducible，不用当前CI替代原根因。
