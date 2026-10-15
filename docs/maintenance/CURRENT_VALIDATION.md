# 当前维护验证快照

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

- 当前仍是Java8/旧Jetty和数据库栈，不是已完成现代Java安全发行版。DEPENDENCIES.md列出Java17/Jetty12、H2/Flyway/MySQL/ZK/模板/传递依赖的迁移和审计队列；依赖树不是漏洞扫描通过报告。
- 未升级/迁移用户历史数据库，没有删除或改写V1/Flyway记录；H2旧库到新库、实际MySQL、新旧版本回滚组合仍需独立验证。
- 真实AD、LDAPS可信证书链、Kerberos/JAAS、双栈ZK网络、实际nginx/TLS与Firefox真实登录未验证。
- 30万子节点、原Windows9秒延迟的原配置/网络未复现；普通CI不能说明那些性能问题已解决。
- Docker冒烟没有验证生产ZK网络、升级既有卷或完成镜像漏洞扫描。未发布公共镜像或正式Release。
- 导入/批量删除不是事务；文本导出仍不是任意二进制或反斜杠序列的无损备份。

上游14个PR和46个Issue均完成本轮审查/处置说明，但延期/增强和无法复现项目没有被叫做全部修好。继续执行入口为TASKS.md。

## D01 后续审查（2026-10-15，PR #20历史记录）

新增 [D01_MIGRATION_PLAN.md](D01_MIGRATION_PLAN.md) 作为待执行验收方案，不是成功报告。本轮本地 OpenJDK17 构建停于依赖解析/DNS，代理重试后执行通道断开；未取得编译/插桩或回归结果，未更改代码和依赖。已重新核对 #19 的准确 head 2d58c446b42e72aaa29634afff5b3a183709d130，运行38699753280四个job成功；其范围仍是Java8维护基线。
