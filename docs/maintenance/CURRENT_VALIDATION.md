# 当前维护验证快照

## 2026-10-16 UTC 当前入口：旧MySQL方言历史

#39准确head `c0971059b4315bbe54a1bba8a70b946d588ff5f6` 的PR38831718747/push38831710330全六job成功并逐日志审查后已合并。master `3574b45ec0e2531921cf9729ddb164f43f4af315` 的[38832294886](https://github.com/DDDalek92/zkui/actions/runs/38832294886)也六job成功；三平台各149项零失败/错误/跳过、Chrome3组、容器/旧ZK及现代MySQL真实Main/拒绝/重启均通过。master树e6c79e2与已验head一致。下方#39待CI是提交时快照。

从该最新远程基线隔离接续旧Flyway2先应用独立MySQL方言V1、匹配location原引擎validate/官方Flyway4桥接、现代应用保留历史与Main重启，见[MYSQL_DIALECT_HISTORY.md](MYSQL_DIALECT_HISTORY.md)。仅测试/文档变化，运行实现、两个V1及POM不改。5.6仅旧格式生成器，不声称#28应用支持5.6；未桥接/损坏副本必须拒绝，source不桥接。新head真实MySQL及全部CI仍须独立核验，本地没有Docker不能把Java/H2成功叫作MySQL验收。

首轮本地完整clean verify为32单元通过、117集成中1个SocketTimeoutException（LargeTreeIT.combinedDeleteSelectionIsPreflightedBeforeAnyRootIsRemoved，HTTP读取超时），整体BUILD FAILURE，不能报告149项通过。该实现/断言/超时均未在本增量修改，当前不臆断根因为MySQL夹具或原Windows9s:1s问题。继续原样定向复跑和完整回归，不删除断言/增大限时/跳过该测试；后续成功不抹掉此失败记录。

原样定向复跑于2026-10-16 01:39:01 UTC完成BUILD SUCCESS：7个格式保护单元+5个LargeTreeIT，零失败/错误/跳过。只说明这次没有重现，未证明超时根因已修复，不能冒充完整149项。随后重新执行完整clean verify以清除旧报告并验收全套。

第二轮完整clean verify于2026-10-16 01:43:05 UTC仍BUILD FAILURE：32单元通过，117集成同一LargeTreeIT用例1个HTTP读取超时，其余无失败/错误/跳过。不继续重复求绿，不放宽读取限时或预算；本地完整回归仍未通过。新增MySQL路径仅由准确head CI真实服务验证，CI通过也不等于此本地I02观察已修复。当前确认删除预检在遍历完首个6000子节点根后才发现跨根总预算超限，存在大量顺序exists/getChildren往返；这是源码工作量观察，不声称已证明具体超时原因，更不等于原Windows9s:1s根因。后续I02考虑不改变权限/重叠选择/零删除语义的跨根早期下界预检，须独立复现和回归，不能混入本测试专用增量。

已比较本增量与#39本地通过版本的ZooKeeperUtil.class、LargeTreeIT.class、TestApplication.class，三者逐一SHA256相同；相应实现、测试断言/读取超时及运行POM/V1均未改。这能证明该路径没有代码差异，不能据此推断具体性能根因。新helper的owned开关及旧classpath未知模式拒绝检查通过，未建立数据库连接；本机仍无Docker，真实MySQL路径须看新head CI。

接续检查时Fork无其他未完成PR，上游仍14个开放PR/46个开放Issue，编号集合无新增。分类不是全部修复，D01更广历史/故障/安全依赖与部署及I02原环境限制继续保留。

## 2026-10-16 UTC 当前入口：MySQL现代运行路径

最终源码完整clean verify于2026-10-16 00:48:32 UTC完成BUILD SUCCESS：32单元+117集成=149项零失败/错误/跳过，Failsafe timeout=false。包括最后的同名VIEW拒绝预检及真实Main负向helper修订；本机未运行MySQL/Docker/Chrome。组装测试证明无旧Flyway类、H2/MySQL provider并存；实际运行图已导出MODERN_RUNTIME_DEPENDENCY_TREE.txt。原V1 blob仍fadfd7e9462b348131965f855949529ac6ceaa39。此前中间149项不代替最终源码结果；新head真实MySQL及所有CI仍须独立核对。

#38准确head `8af4efcb55b8baaceda70389cd151386e9cf36dc` 的PR38825255619/push38825243961六job均成功并逐日志审查后已合并；master `3e5873d5091429788cbde5bc28b9b12586addd24` 的[38825828726](https://github.com/DDDalek92/zkui/actions/runs/38825828726)六job也成功。三平台各149项零失败/错误/跳过、Chrome3组、容器/旧ZK/MySQL均通过；MySQL日志包含原引擎损坏checksum转换前拒绝、官方转换/保留字段/重复运行、现代模块重开/高ID/新建/坏checksum拒绝全部标记。master树286006b与已验head一致。下方#38待CI为历史快照。

从该远程最新基线隔离继续：MySQL改为现代core/mysql13.10.3、移除运行Flyway2，使用assembly服务合并保留H2/MySQL插件；启动前拒绝旧格式、未知版本/非空库及schema/checksum异常，所有后端禁止dev自动清库。真实MySQL应用及负向矩阵见[MODERN_MYSQL_RUNTIME.md](MODERN_MYSQL_RUNTIME.md)，准确新head全CI仍须独立核验，不能把本地H2通过称为MySQL成功。首次离线构建因新环境缺Failsafe缓存而未执行，使用正常配置代理恢复下载，不关闭校验。

Fork无其他未完成PR，上游仍14开放PR/46开放Issue且编号集合无新增。原V1未改，未接触用户库；D01整体、安全/许可证/SBOM、更广历史/故障与部署矩阵仍未完成。

## 2026-10-15 当前入口：MySQL Flyway 独立副本桥接

#37准确head `1c4f50a00910190c42aed15267cf6c7fc2a5f16b` 的PR38819046224/push38819038419全部六job成功并逐日志审查后已合并；master `5182459ee6e8500268e65ec589124749590a0762` 的[运行38819831969](https://github.com/DDDalek92/zkui/actions/runs/38819831969)也六job success，Ubuntu/macOS/Windows各32单元+117集成=149项零失败/错误/跳过、Chrome3组、容器/MySQL/旧ZK均通过。master树 `4d334515b41594bb86e1e68611984fe690b5ecc9` 与已验证head一致；下方#37待CI是提交时快照。

本轮从该最新远程基线隔离继续MySQL原引擎先validate、官方Flyway4元数据/checksum转换、现代Flyway13/MySQL模块重开及损坏副本拒绝，见[MYSQL_FLYWAY_BRIDGE.md](MYSQL_FLYWAY_BRIDGE.md)。只增加独立引擎预演，应用MySQL仍使用Flyway2；候选模块仅复制到测试目录，不进入运行包。原V1不改。准确新head本地/真实MySQL及全CI尚须分别记录，禁止把编译或H2通过称为MySQL桥接已验收。

首轮本地clean verify单元32项通过，但assembly产物缺ZIP尾目录，集成测试报Invalid or corrupt jarfile/zip END header not found；磁盘空间充足，不能把该轮当通过。原样重建后JAR压缩完整性检查通过，继续完整回归；没有修改构建校验、运行实现或放松测试来绕过。MySQL候选模块的实际pom.properties为13.10.3，服务注册文件存在；本机无Docker，真实桥接仍待准确head CI。

重建完整clean verify于2026-10-15 23:41:54 UTC完成BUILD SUCCESS：32单元+117集成=149项、零失败/错误/跳过，Failsafe timeout=false。仅新增MySQL helper的追加重开/坏checksum拒绝最后修订随后于23:42:14 UTC独立test-compile成功（48个测试源文件）；未把这项编译叫作MySQL运行验证。原引擎隔离classpath的owned开关/未知模式拒绝冒烟通过，不建立数据库连接。新MySQL预演仍必须以准确head CI mysql job日志为准。

当前Fork无其他未完成PR；上游仍14开放PR/46开放Issue，编号集合无新增；分类不是全部修复。D01整体和MySQL应用现代API/旧运行引擎移除、实际权限磁盘/更广历史夹具及安全依赖审计仍未完成。

## 2026-10-15 当前入口：H2 现代运行时与真实桥接应用

最终本地 `clean verify` 于2026-10-15 22:41:24 UTC完成，完整文本BUILD SUCCESS；Surefire 11套32项、Failsafe 29套117项，总149项零失败/错误/跳过，Failsafe汇总timeout=false。包含最后的跨schema非空库拒绝修订。原V1 blob仍为 `fadfd7e9462b348131965f855949529ac6ceaa39`。本机未执行Chrome/Docker/MySQL容器；新PR准确head六job仍须独立核验，不以本地成功替代。

#36已在准确head `8335b8f6dbc43c3951ef1ef93ab8137d2537aba9` 的[PR运行38797311625](https://github.com/DDDalek92/zkui/actions/runs/38797311625)及push38797301172六job成功后审查合并，三平台各137项、Chrome3组、容器/MySQL/旧ZK通过。合并基线 `a41ec71817026e32a780699ceebe72844fbffb9a` 的push38797983943也六job success；其树与已验PR完全一致。下方#36待CI为历史快照。

本增量从该最新远程基线的既有干净隔离分支接续，切换H2 2.5.252/Flyway13.10.3运行包，旧驱动/旧Flyway仅在迁移子进程隔离使用（MySQL运行仍保留Flyway2，不混称移除所有旧引擎）。新增真正旧V1桥接目标的Main/HTTP/审计新ID/同路径重启及未桥接拒绝，见[MODERN_H2_RUNTIME.md](MODERN_H2_RUNTIME.md)。原V1未改；上游仍14个开放PR/46个开放Issue，无新增，不是全部修好。

已实际发现并修正鲜库TABLE内部标记的重复启动拒绝、隔离导入子进程缺原资源、旧夹具凭据与app默认凭据不一致，以及UTC/非UTC及H2缓存时区导致的对比偏移。此前中间失败不是通过报告。时区现在在验收连接/进程上明确固定；厂商H2验证上限2.3.232警告仍保留，不能声称官方认证2.5.252。准确新head完整本地及全部CI结果须分别记录后审查合并，最终证据以对应PR为准。

## 2026-10-15 当前入口：H2 文件格式启动前保护

#35准确head `81aa63fcc21c39faa56a65d72eb0bcc6e18cca17` 的[PR运行38789692993](https://github.com/DDDalek92/zkui/actions/runs/38789692993)（attempt2）及push38789687026全部六job成功后已审查合并；三平台各16单元+111集成=127项，Chrome3组及容器/MySQL/旧ZK通过。容器首次为Maven依赖描述符解析失败；同head的push容器已通过，再仅重跑失败job后也成功，未改代码、POM、TLS或校验。master `2c9c68e3088d1b20d078992c161624a816f782bc` 的push38790758128已查询success。下方#35待CI为旧快照，不再是当前阻塞。

本次从该最新基线隔离复现旧运行H2忽略 `.mv.db` 而旁路创建 `.h2.db`，先取得新增测试的预期失败，再加入连接/迁移/dev清库之前的格式保护。真实JAR在两个格式并存时非零退出且不监听HTTP，两库SHA256不变；候选驱动仅验收保护器，不声称完整新版Main/HTTP已通过，见[H2_FILE_FORMAT_GUARD.md](H2_FILE_FORMAT_GUARD.md)。最终完整本地与准确新head CI须分别记录；只有准确head全部所需CI通过才审查合并。POM和原V1均未改，上游仍14个开放PR/46个开放Issue，无新增；分类不是全部修复。

本地中间完整clean verify为23单元+114集成=137项，零失败/错误/跳过；首轮完整文本BUILD SUCCESS，第二轮返回0、XML/Failsafe汇总一致但文本止于dependency阶段，不冒充完整日志。最终大小写后缀/Windows夹具修订后，定向23单元+10集成=33项BUILD SUCCESS。没有在本机执行Chrome、Docker或真实MySQL/旧ZK容器；准确新head完整跨平台和六job最终结果须以对应Fork PR Checks/审查证据为准，不以中间137项替代新head。

## 2026-10-15 当前入口：仅追加审计差量恢复

#34准确head `8a3157d6fa8e84a22ee3baaceaceef4a4caa8c21` 的[PR运行38787905991](https://github.com/DDDalek92/zkui/actions/runs/38787905991)及push运行38787853716全部六job成功后审查合并，三平台各123项、Chrome3组与容器/MySQL/旧ZK通过，基线为498fe7f。下方#34待CI为提交时快照，不是当前阻塞。

本次从该最新远程基线隔离继续仅追加审计差量预演，见[H2_AUDIT_DELTA.md](H2_AUDIT_DELTA.md)：候选新增行精确恢复到旧驱动副本、相同快照重复回放、ID继续/重开、基线修改删除拒绝、冲突整批回滚及载荷完整性拒绝。本地最终clean verify返回0，16单元+111集成=127项XML/汇总报告零失败/错误/跳过，含13项迁移预演；文本日志止于assembly，不冒充完整文本日志。不是通用降级工具，不改变运行H2/Flyway；完整新版应用Main/HTTP及实际磁盘/权限等仍未完成。准确head六job及完整日志仍须独立核验；基线498fe7f的push运行38788556538已查询success。

## 2026-10-15 当前入口：原生导入中断与只读目标

#33已在准确head `69d367fc832ad4679314a492df01d98c9ca48dbe` 的[PR运行38781568897](https://github.com/DDDalek92/zkui/actions/runs/38781568897)及[push运行38781523517](https://github.com/DDDalek92/zkui/actions/runs/38781523517)全部六job成功后审查合并：三平台各121项，Chrome3组、容器/MySQL/旧ZK通过。master现为4eff9d8，其push运行38782275556也已查询success。下方#33“新head待验收”为提交时快照，已由对应PR最终准确head审查完成，不是当前阻塞。

本次从该最新远程基线隔离检出，新增原生RUNSCRIPT在受控检查点终止后的半成品拒绝、只读目标90097导入拒绝，见[H2_IMPORT_FAILURES.md](H2_IMPORT_FAILURES.md)。本地OpenJDK17.0.20完整clean verify为BUILD SUCCESS，16单元+107集成=123项零失败/错误/跳过，含定向9项预演；完整文本日志与报告一致。本增量准确head六job仍须独立核验。只读模式不等于文件系统权限/磁盘故障，受控中断不等于任意写页崩溃。完整应用驱动/Flyway切换及切换后差量恢复仍未完成。

## 2026-10-15 当前接续：候选库锁竞争/空闲持锁进程终止

#32准确head `4aa07b1886a50723b6f4922f1b6b4bf9f43b4c7c` 的[运行38780428584](https://github.com/DDDalek92/zkui/actions/runs/38780428584)六job/完整日志已核验：三平台各120项零失败/错误/跳过，Linux Chrome3组及容器/MySQL/旧ZK通过后合并，master为d20eece。完整证据已写入H2_SCHEMA_VALIDATION.md；下方“本增量待CI”属于#32提交时快照。

从该最新master隔离继续：测试自建候选库的第二进程必须90020拒绝，随后终止仅本测试的空闲持锁进程，重开核对全行/元数据/结构/约束及新增ID/重开；原封存hash和导出不变且旧副本恢复成功。见[H2_LOCK_RECOVERY.md](H2_LOCK_RECOVERY.md)。本地最终clean verify返回0，16单元+105集成=121项XML/汇总报告零失败/错误/跳过（文本日志截断），含七项迁移预演；准确新head六job/完整日志仍须独立核验。不是导入中断或磁盘故障恢复，也不是完整应用驱动迁移。

## 2026-10-15 当前接续：结构与约束预演

#30已核对最终head `9a07fe869e9b7b5a979b87ed5284641847968e8c` 的[运行38771117277](https://github.com/DDDalek92/zkui/actions/runs/38771117277)六job成功后合并，三平台各119项、Chrome3组、容器/MySQL/旧ZK检查通过；master现为 `1f8058befd2e4602c592bf92d27d99635336f436`。下方“尚待#30组合验证/本地环境不可用”为当时快照，不再是当前状态。

本地执行环境已恢复，基于该最新master的隔离检出补齐H2历史表语义结构、PK/索引、可空性、自增及实际约束/边界探测，新增行不变而DDL变宽的拒绝用例，见[H2_SCHEMA_VALIDATION.md](H2_SCHEMA_VALIDATION.md)。本地clean verify返回0，完整JUnit XML/Failsafe汇总为16单元+104集成=120项，0失败/错误/跳过；文本日志尾部截断，不据此声称完整日志已保存。准确head六job仍须独立核验。当前master的push运行38771830646也已查询为success（不是将PR专用查询的空列表当作无CI）。未切换运行时H2/Flyway，完整应用及锁/中断/不可写/差量恢复仍未完成。

## 2026-10-15 接续说明

Fork #31已由准确head `c93dcfffba4de65539fe8f634190ba3f81d1d4ac`、[运行38745592386](https://github.com/DDDalek92/zkui/actions/runs/38745592386)六job成功后合并，三平台各117项。hosts初始化默认关闭；显式覆盖采用观察到的ZK版本号，不具备ZK/JDBC共享事务。

现承接已有#30合法NULL历史页面修复；原head `187dc3dc458ca38e4674faa53c3943485d3601c2`的[运行38746059947](https://github.com/DDDalek92/zkui/actions/runs/38746059947)六job成功、三平台各114项，但不能代表与最新master同步后的组合结果。只解决文档冲突并保持#30既有源码不变；准确新head全部六job/完整日志需重新核对后才可合并，见[NULL_HISTORY_VALIDATION.md](NULL_HISTORY_VALIDATION.md)及对应PR。当前本地执行环境不可用，未取得组合版本本地构建结果；不采用中断且未推送的执行结果。完整H2迁移/失败恢复矩阵和安全/部署/其余增强验收仍未完成。

下方“当前增量”及待验证表述保留先前快照；当前状态以上述接续及准确head的Checks/PR审查记录为准，不以旧绿灯替代新head。

## 最新已合并增量

- #25隔离Flyway桥接：head6750ff95f0f7ec8f819b376a00c124073b432b38，运行38738283086，三平台各107项及全部job通过。
- #27大树分页/共享删除预算：head47ae47027bbeb5ca3e8c7fc6432a0c403bf50111，运行38739863015，三平台各112项、Chrome和容器通过；含312763个真实服务内存树节点读取，未测WAL/写吞吐。
- #28真实MySQL逻辑迁移/TLS/方言/驱动：head088e4afda3ef8dd1cd7dc267150c55f7e22ddbfc，运行38741719034，五job通过；三平台各112项，新增实际5.6->8.4逻辑转移与证书拒绝/打包应用验证。应用MySQL基线8.4+，不等于用户服务器原地升级。
- #29 ZK/Netty/模板/IO：headaa6d39a08a9dc529a9e313badc3d43a246bb6837，运行38743599356，六job通过；三平台各112项、真实旧ZK3.5.7服务与新3.9.6客户端Digest/ACL/重连、Chrome、容器和MySQL通过。

上面均逐job核对日志，0失败/错误/跳过。H2/Flyway应用驱动升级、完整故障/差量恢复、安全审计和其余增强仍未完成。hosts初始化为后续候选，不能用这些旧head证据替代它的准确head CI。


## 当前增量：Fork #23（2026-10-15）

已审查合并[数据库就绪/审计失败策略 #23](https://github.com/DDDalek92/zkui/pull/23)，head `748ad0c86bf3b2721ffdd32414afe90400aea1ab`，[CI 38736989054](https://github.com/DDDalek92/zkui/actions/runs/38736989054)四个job全成功。逐一核对日志：Ubuntu116428803611、macOS116428802960、Windows116428803570各16单元+89集成=105项，0失败/错误/跳过；Linux Chrome3项，0失败/跳过；container116428803377构建、UID/GID10001及登录/环境覆盖/前缀资产冒烟成功。独立detached checkout同一准确head，本地OpenJDK17完整clean verify也是105项通过；本机没有执行Chrome/Docker。

迁移异常/必需表读取失败现在启动前停止；审计写失败显式503且不冒充ZK回滚，见[DATABASE_READINESS.md](DATABASE_READINESS.md)。V1 blob仍为 `fadfd7e9462b348131965f855949529ac6ceaa39`，运行H2/Flyway未升级。#22隔离H2预演在[运行38728588004](https://github.com/DDDalek92/zkui/actions/runs/38728588004)四job成功、三平台各100项，是先前增量证据。

并行出现的同目标 #24 已关闭未合并，分支保留，没有覆盖 #23；#24本地104项不能冒充 #23准确head的105项。上游2026-10-15重查仍14个开放PR/46个开放Issue，编号集合无新增，不等于全部修复。

#25是后续Flyway桥接实验。其初始head `dac03a897f81d171cc3f919d50dbb9f6abe752dd` 在[运行38737460221](https://github.com/DDDalek92/zkui/actions/runs/38737460221)三平台均失败于反射签名 `NoSuchMethodException: Flyway.setDataSource(String,String,String)`，不是完整桥接已执行后的迁移结论。已核对Flyway4.2官方源码的第四个varargs String[]参数；该工作分支已更新为 `87c04805dd3e5b2dfec476140841f3dc2e65463f` 并重新运行CI，未覆盖它或用旧绿灯替代新head。桥接还会转换checksum/格式，不能将之误称所有元数据不变；需原引擎先校验，并检查负向夹具。

继续入口：先检查#25后续准确head的全部CI/源码及校验拒绝证据，不能把实验失败/未验标为完成。完整H2/Flyway迁移、实际MySQL、约束/索引、锁/中断/不可写和差量恢复、I02、依赖/部署验收仍未完成。#25同时更新TASKS/DEPENDENCIES/D01计划；本PR只记录CURRENT_VALIDATION和DATABASE_READINESS，避免并行编辑同一组文件。

## 先前证据与历史记录

已核对并合并Fork #21：head37369d1127c9729ced003169a4ba27f894823adf，[运行38716523453](https://github.com/DDDalek92/zkui/actions/runs/38716523453)四job全成功；Linux/Windows/macOS的Temurin17各16单元+81集成=97项，0失败/错误/跳过，Chrome3组与Java17非root容器冒烟成功。新增H2隔离预演及其新head验证见[H2_MIGRATION_REHEARSAL.md](H2_MIGRATION_REHEARSAL.md)和对应Fork PR。

当前Java17/Jetty12 EE8/JDBC基线见[JAVA17_VALIDATION.md](JAVA17_VALIDATION.md)及对应Fork PR准确head的CI。以下保留PR #18/#20的Java8结果和当时阻塞作为历史，不代表Java17的执行结果。

2026-10-15，Fork PR #18提交456d019aa17c2c4eb92e2659f6ddde66526e6766。
[CI运行38698340364](https://github.com/DDDalek92/zkui/actions/runs/38698340364)的全部4个job成功；日志和报告由该运行保存。

| 环境/任务 | 实际结果 | 范围 |
| --- | --- | --- |
| Ubuntu24.04 / Temurin JDK8 | 16单元+76集成，0失败/错误/跳过 | 完整clean verify，隔离ZK/H2/LDAP、真实JAR HTTP |
| Windows2022 / Temurin JDK8 | 同样92项全部通过 | 同一套完整回归，不用Linux结果替代 |
| macOS14 ARM / Azul Zulu JDK8 | 同样92项全部通过 | 同一套完整回归；不是旧JDK9/10或所有macOS版本声明 |
| Linux / Chrome / Playwright | 3组通过，0失败/跳过 | 真实JAR渲染页面、/zkui前缀下所有资产、键盘/稳定排序、选择/编辑事件、pre-wrap、HTML不执行和USER遮蔽 |
| Docker构建 | 成功 | 仓库根多阶段构建，Maven ZIP校验开启，固定官方Temurin镜像摘要 |
| Docker运行冒烟 | 成功 | UID/GID10001；合成USER_SET及baseURL/横幅环境优先级；登录/静态资源；无生产ZK写入 |

第一次跨平台运行38697820333并非全部通过：Linux/Windows成功，macOS的Temurin8 ARM版本不可用，Docker因缺unzip让Wrapper下载tar.gz但核对ZIP摘要而拒绝。修正为macOS使用可用Zulu8、Docker安装unzip，未去除哈希校验。checkout/setup-java更新为已核对官方v5提交，使用Node24运行时。

本地Linux同样92项Java回归通过；本机浏览器下载为空/损坏压缩包、没有Docker命令，所以浏览器/容器成功依据明确来自远程CI。没有用静态HTML检查替代这些执行结果。

保留的验收限制：

- 上表Java8/旧Jetty是历史结果；当前Java17/Jetty12已在#21完成，但H2/Flyway/MySQL/ZK/模板/传递依赖的迁移和审计仍未完成。依赖树不是漏洞扫描通过报告，不能宣称已完成现代Java安全发行版。
- 未升级/迁移用户历史数据库，没有删除或改写V1/Flyway记录；H2旧库到新库、实际MySQL、新旧版本回滚组合仍需独立验证。
- 真实AD、LDAPS可信证书链、Kerberos/JAAS、双栈ZK网络、实际nginx/TLS与Firefox真实登录未验证。
- 30万子节点、原Windows9秒延迟的原配置/网络未复现；普通CI不能说明那些性能问题已解决。
- Docker冒烟没有验证生产ZK网络、升级既有卷或完成镜像漏洞扫描。未发布公共镜像或正式Release。
- 导入/批量删除不是事务；文本导出仍不是任意二进制或反斜杠序列的无损备份。

上游14个PR和46个Issue均完成本轮审查/处置说明，但延期/增强和无法复现项目没有被叫做全部修好。继续执行入口为TASKS.md。

## D01 后续审查（2026-10-15，PR #20历史记录）

新增 [D01_MIGRATION_PLAN.md](D01_MIGRATION_PLAN.md) 作为待执行验收方案，不是成功报告。本轮本地 OpenJDK17 构建停于依赖解析/DNS，代理重试后执行通道断开；未取得编译/插桩或回归结果，未更改代码和依赖。已重新核对 #19 的准确 head 2d58c446b42e72aaa29634afff5b3a183709d130，运行38699753280四个job成功；其范围仍是Java8维护基线。
