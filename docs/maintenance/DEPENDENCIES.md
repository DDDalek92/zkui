# 依赖维护状态与执行顺序

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

见 [D01_MIGRATION_PLAN.md](D01_MIGRATION_PLAN.md)：保留原方案及Java17实现后的差异；H2跨版本合成夹具、独立驱动/目录、逐字段/生成ID/Flyway元数据校验及失败回滚矩阵仍未完整执行。#23已将迁移异常/必需表读取失败改为HTTP启动前终止，审计写失败显式503且提示ZK变更可能已生效，见[DATABASE_READINESS.md](DATABASE_READINESS.md)。不是跨系统事务或持续写禁用策略，不能把方法返回/登录成功当持久化或完整迁移证明。运行H2/Flyway仍未升级；后续已有#25桥接实验，应核对准确head CI后再给结论。
