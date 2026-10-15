# 依赖维护状态与执行顺序

2026-10-15。依赖清单由固定工具 `org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree` 实取，见 DEPENDENCY_TREE.txt；不等于 CVE 扫描。

已实现阶段（Fork PR #16）：SLF4J 1.7.36、显式 reload4j 1.2.27，排除 ZK 传递的 Log4j 1/旧绑定；Commons FileUpload 1.6.0、512字节part头上限，原有32parts/1MiB/2MiB等限制仍有效。组装包检查及86项Java回归通过。日志格式兼容现有 log4j.properties，不使用网络Appender。

尚未完成的执行队列：

1. Java17及Jetty12：当前ActiveJDBC1.4.1构建插桩在JDK17反射访问失败；先在隔离分支升级/替换插桩和ORM，保证历史读写及真实JAR回归，再迁移全部Jetty模块、注解/静态资源、Session/CSRF/HTTPS/代理。Jetty12的EE8模块可保留javax，不必强制将应用改为Jakarta。不得只改jetty-server属性。
2. H2/Flyway历史迁移：生成含中文/旧历史/元数据的1.3库副本，用原驱动SCRIPT导出；新版本只打开新路径数据库，RUNSCRIPT并核对行数/内容/ID/版本记录；重复启动、失败回滚、完整恢复测试。保留原库与原应用可回退组合，禁止新驱动直接打开唯一旧库。V1__Core.sql不改写，不删schema_version。
3. MySQL：独立核对当前驱动坐标/JDBC URL/TLS/时区，使用一次性实际服务验证新建及已有历史迁移。#63的MySQL5.7主键DDL差异不能由H2通过代替，更不能按原帖建议删除Flyway记录。没有真实数据库副本前不自动迁移用户数据。
4. ZK3.5.7及Netty4.1.45、FreeMarker2.3.8、JSON库、Flyway2.2.1/ActiveJDBC1.4.8、旧MySQL及Protobuf都仍需逐项替换与回归。构建插件依赖也在后续审计范围。
5. 完成 SBOM/漏洞扫描、许可证核对及可部署包检查后再评估维护版发布；当前不创建正式Release或发布公共镜像。

版本依据（已核对官方资料）：

- [Jetty下载及Java/Servlet支持表](https://jetty.org/download.html)：当前支持12系，9/10/11已EOL。
- [Jetty支持策略](https://jetty.org/support.html)：2026-01-01起旧系列修复二进制不再发布到Maven Central。
- [SLF4J手册](https://www.slf4j.org/manual.html)：1.7.36的旧log4j12坐标迁移到reload4j绑定。
- [reload4j官方](https://reload4j.qos.ch/)及Maven Central发布元数据：当前选用1.2.27。
- [FileUpload发布说明](https://commons.apache.org/proper/commons-fileupload/changes.html)、[安全说明](https://commons.apache.org/proper/commons-fileupload/security.html)：1.6.0支持Java8并提供part头上限。
- [H2官方迁移流程](https://www.h2database.com/html/migration-to-v2.html)：原版本导出SQL，新版本创建新库后导入。

没有把上游历史Dependabot目标版本当作当前安全版本；延期项目仍是执行任务，不是已修复。
