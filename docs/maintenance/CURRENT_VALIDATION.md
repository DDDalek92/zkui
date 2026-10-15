# 当前维护验证快照

## 当前增量：合法NULL历史的真实页面兼容（2026-10-15）

复现、修复及准确新head的CI准入见[NULL_HISTORY_VALIDATION.md](NULL_HISTORY_VALIDATION.md)。修改前ADMIN历史页对NULL日期触发InvalidReferenceException，HTTP虽200但内容不完整；不是数据库丢失或迁移成功。此增量不改库、V1、H2/Flyway或访问控制，不能替代完整历史库迁移。

已承接核对并合并的#29 head `aa6d39a08a9dc529a9e313badc3d43a246bb6837`，[运行38743599356](https://github.com/DDDalek92/zkui/actions/runs/38743599356)全部六job成功；Linux/Windows/macOS各16单元+96集成=112项，0失败/错误/跳过，Chrome3组、非root容器、实际MySQL/TLS及3.9.6客户端对3.5.7真实旧ZK服务的Digest/ACL/UTF8/重连检查成功。#27的大树受测范围和#28的MySQL逻辑迁移路径分别见LARGE_TREE_VALIDATION.md、MYSQL_VALIDATION.md；当前运行依赖已更新，旧清单里的待办状态须按这些证据区分。它们不是任意旧环境/全漏洞清零/完整H2升级/正式发布声明。

下文#23/#25待验证和旧依赖描述保留为先前快照：#25最终head `6750ff95f0f7ec8f819b376a00c124073b432b38` 已由[运行38738283086](https://github.com/DDDalek92/zkui/actions/runs/38738283086)全部四job验证后合并，三平台各107项Java、Chrome3组和容器通过。完整应用迁移与其余矩阵仍待办，不应再重复创建相同桥接PR。

## 先前增量：Fork #23（2026-10-15）

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
