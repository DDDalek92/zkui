# 依赖维护状态与执行顺序

实际运行图见[MODERN_RUNTIME_DEPENDENCY_TREE.txt](MODERN_RUNTIME_DEPENDENCY_TREE.txt)，固定dependency-plugin3.8.1/runtime tree导出，含core/mysql13.10.3且无运行com.googlecode Flyway；旧图保留为历史。图不是SBOM/CVE/许可证通过报告。

> 当前现代MySQL增量：org.flywaydb core/mysql13.10.3进入运行包，com.googlecode Flyway2从运行依赖移除；旧2/4仅隔离历史夹具。assembly聚合服务注册，同时保留H2/MySQL插件。范围/待验收见[MODERN_MYSQL_RUNTIME.md](MODERN_MYSQL_RUNTIME.md)，准确新head全部CI成功后才准入；下方“MySQL仍运行旧引擎”为历史快照。SBOM/漏洞/许可证审计仍未完成。

> MySQL接续（2026-10-15）：#37及合并master六job已验证，三平台各149项。现增加独立副本上的旧Flyway2先校验/官方Flyway4转换/现代Flyway13+MySQL模块预演，见[MYSQL_FLYWAY_BRIDGE.md](MYSQL_FLYWAY_BRIDGE.md)。`flyway-mysql:13.10.3` 仅复制测试jar，未加入运行依赖图；应用MySQL仍保留Flyway2，实际新head MySQL CI未完成前不称桥接通过，更不能称运行旧引擎已移除。

> 当前H2运行时增量：H2 2.5.252 / 开源Flyway13.10.3，独立新建方言及原V1桥接Main/HTTP/重启/拒绝验证，见[MODERN_H2_RUNTIME.md](MODERN_H2_RUNTIME.md)。MySQL仍使用旧Flyway2及既有checksum路径，现代API/module迁移和旧引擎移除是下一项；Flyway的H2厂商验证上限警告未屏蔽。下方“候选仅测试/运行H2未升级”为历史快照，不代表本增量源码。最终准入仍依准确head全部CI。

本增量实际运行依赖图见[MODERN_H2_DEPENDENCY_TREE.txt](MODERN_H2_DEPENDENCY_TREE.txt)，由固定dependency-plugin3.8.1的runtime tree生成：H22.5.252与org.flywaydb13.10.3进入运行包，H21.3/Flyway4不在运行图，com.googlecode Flyway2.2.1因MySQL仍保留。此图不是SBOM/CVE/许可证审核通过报告；原DEPENDENCY_TREE.txt留作旧阶段快照。

> D01-C新增实际应用本地文件格式保护，见[H2_FILE_FORMAT_GUARD.md](H2_FILE_FORMAT_GUARD.md)：复现旧驱动忽略新版文件而建立旁路空库，现于连接/迁移/dev清库前拒绝；不改变运行H2/Flyway或已应用V1，完整新版Main/HTTP仍未完成。

> D01-C有限追加差量预演见[H2_AUDIT_DELTA.md](H2_AUDIT_DELTA.md)：旧驱动只开封存副本，记录逐字段恢复/冲突拒绝，不改迁移元数据；不是在线/通用降级工具。完整新版H2/Flyway应用及MySQL旧API/checksum兼容仍需独立实现验收。

> D01-C故障预演接续：受控原生RUNSCRIPT中断后的半成品拒绝、显式只读目标导入拒绝见[H2_IMPORT_FAILURES.md](H2_IMPORT_FAILURES.md)。运行H2/Flyway未升级，实际文件系统不可写/磁盘故障、完整应用及差量恢复仍未完成。

> D01-C接续：隔离预演新增固定V1的语义表结构/PK/索引/可空性/自增和实际约束探测，见[H2_SCHEMA_VALIDATION.md](H2_SCHEMA_VALIDATION.md)。候选H2/Flyway仍仅测试隔离使用；不改变运行包，不代表完整应用迁移或任意历史库验收完成。

> 当前更新：#28已完成Connector/J26.7.0、MySQL8.4+方言和真实旧库逻辑迁移/TLS/启动验证；#29已完成ZK3.9.6（Netty4.1.137.Final）、FreeMarker2.3.35及CommonsIO2.22，六个CI job通过。见MYSQL_VALIDATION.md和CLIENT_TEMPLATE_UPGRADE.md。下文旧版本/待办描述保留历史；H2/Flyway运行时、JSON库、SBOM/漏洞/许可证及其余部署验证仍未完成。


> 后续更新：Fork #23已合并。启动迁移失败/历史列不可读时停止HTTP启动，显式启用旧Flyway校验；历史写入失败明确返回503并提示先核对已执行的ZK变更。见DATABASE_READINESS.md及PR #23的105项/三平台CI。下文关于原先“吞错继续”的描述为历史基线，不再代表当前策略。驱动/Flyway跨版本迁移仍未放行。

2026-10-15。依赖清单由固定工具 `org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree` 实取，见 DEPENDENCY_TREE.txt；不等于 CVE 扫描。

已实现阶段（Fork PR #16）：SLF4J 1.7.36、显式 reload4j 1.2.27，排除 ZK 传递的 Log4j 1/旧绑定；Commons FileUpload 1.6.0、512字节part头上限，原有32parts/1MiB/2MiB等限制仍有效。组装包检查及86项Java回归通过。日志格式兼容现有 log4j.properties，不使用网络Appender。

尚未完成的执行队列：

1. Java17及Jetty12：本分支整体迁移到Jetty12.1.14 EE8（保留javax Servlet4.0），编译/CI/容器统一Java17；历史ORM改为参数化JDBC，移除ActiveJDBC及插桩。旧插件反射故障、旧Jetty扫描Java17字节码失败、新ActiveJDBC调用旧H2未实现getSchema均已实际观察；没有靠--add-opens或直接升级H2绕过。回归范围/CI准入见[JAVA17_VALIDATION.md](JAVA17_VALIDATION.md)。
2. H2/Flyway历史迁移：生成含中文/旧历史/元数据的1.3库副本，用原驱动SCRIPT导出；新版本只打开新路径数据库，RUNSCRIPT并核对行数/内容/ID/版本记录；重复启动、失败回滚、完整恢复测试。保留原库与原应用可回退组合，禁止新驱动直接打开唯一旧库。V1__Core.sql不改写，不删schema_version。
3. MySQL：独立核对当前驱动坐标/JDBC URL/TLS/时区，使用一次性实际服务验证新建及已有历史迁移。#63的MySQL5.7主键DDL差异不能由H2通过代替，更不能按原帖建议删除Flyway记录。没有真实数据库副本前不自动迁移用户数据。
4. ZK3.5.7及Netty4.1.45、FreeMarker2.3.8、JSON库、Flyway2.2.1、旧MySQL及Protobuf都仍需逐项替换与回归；ActiveJDBC及javalite-common已移除。构建插件依赖也在后续审计范围。
5. 完成 SBOM/漏洞扫描、许可证核对及可部署包检查后再评估维护版发布；当前不创建正式Release或发布公共镜像。

版本依据（已核对官方资料）：

- [Jetty下载及Java/Servlet支持表](https://jetty.org/download.html)：当前支持12系，9/10/11已EOL。
- [Jetty支持策略](https://jetty.org/support.html)：2026-01-01起旧系列修复二进制不再发布到Maven Central。
- [SLF4J手册](https://www.slf4j.org/manual.html)：1.7.36的旧log4j12坐标迁移到reload4j绑定。
- [SLF4J发布说明](https://www.slf4j.org/news.html)：本分支为Jetty12改用API/provider同版本2.0.20，reload4j仍为1.2.27，打包检查排除旧StaticLoggerBinder和多provider。
- [reload4j官方](https://reload4j.qos.ch/)及Maven Central发布元数据：当前选用1.2.27。
- [FileUpload发布说明](https://commons.apache.org/proper/commons-fileupload/changes.html)、[安全说明](https://commons.apache.org/proper/commons-fileupload/security.html)：1.6.0支持Java8并提供part头上限。
- [H2官方迁移流程](https://www.h2database.com/html/migration-to-v2.html)：原版本导出SQL，新版本创建新库后导入。

没有把上游历史Dependabot目标版本当作当前安全版本；延期项目仍是执行任务，不是已修复。

## D01 实现前验收细化

后续D01-C首批隔离预演见[H2_MIGRATION_REHEARSAL.md](H2_MIGRATION_REHEARSAL.md)：测试专用2.5.252驱动不在运行包中；原1.3.174导出在普通模式因DATETIME/50004失败，LEGACY有限夹具通过。应用默认H2/Flyway/V1仍未升级，不能把预演绿灯当作完整应用迁移放行。

见 [D01_MIGRATION_PLAN.md](D01_MIGRATION_PLAN.md)：保留原方案及Java17实现后的差异；H2跨版本合成夹具、独立驱动/目录、逐字段/生成ID/Flyway元数据校验及失败回滚矩阵仍未完整执行。Dao仍保留原迁移/插入错误的日志后继续策略，不能把进程存活或登录成功作为数据库迁移通过证据。当前只验证同一旧H2驱动/V1上的JDBC兼容，不代表历史库跨版本迁移完成。
