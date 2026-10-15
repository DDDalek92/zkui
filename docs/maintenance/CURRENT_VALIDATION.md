# 当前维护验证快照

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
