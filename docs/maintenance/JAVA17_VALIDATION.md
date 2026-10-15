# Java17 / Jetty12 EE8 / JDBC 迁移

2026-10-15；隔离工作区基线 `39d0f93e5f79a8e567724fe2edc99db80d2e55fa`（Fork #20）。
本增量对应 D01-A/B；不是 H2/Flyway 跨版本迁移、全部漏洞清零或正式发行。
确切远程 head、运行链接及最终四个 job 结果记录在对应 Fork PR 的说明/Checks 中；
仅在全部所需 CI 成功并审查后合并，不以本地结果代替 Windows/macOS/Chrome/Docker。

## 实际复现与方案选择

1. 原基线在 OpenJDK17.0.20 构建，旧 instrumentation 1.4.1 在 process-classes 失败：
   `Unable to make ... URLClassLoader.addURL ... accessible: module java.base does not opens java.net`。
   这次已进入插桩并取得错误，不再只是前一轮依赖解析/执行通道阻塞。
2. ActiveJDBC 与插件同升 3.5-j11 后能够编译/插桩，但旧 Jetty9 扫描 release17 的应用类失败：
   `Unsupported class file major version 61`。不能只修构建插件就宣称应用运行正常。
3. 整体 Jetty12 EE8 迁移后，候选新版 ORM 在旧 H2 1.3.174 上调用
   `Connection.getSchema()` 引发 `AbstractMethodError`；历史查询失败、写入被吞后记录缺失。
   没有为绕过它直接升级 H2，也没有混用新插件和未经支持的旧 ORM。
4. 最终删除仅服务 histories 的 ActiveJDBC/插件及 javalite-common 传递依赖。
   History 改为独立行对象，Dao 用固定列名、参数化 LIKE/LIMIT/INSERT、try-with-resources
   取得并关闭 Connection/Statement/ResultSet，返回已完整读取的对象列表。
   不再有模型发现/插桩或线程本地连接依赖。空过滤条件、查询限额、ID降序、摘要500上限保留。

旧迁移异常和写入异常仍采用日志后继续的既有策略（不记录凭据/历史值）；
没有借本次兼容迁移悄悄改变审计失败策略。严格迁移就绪/失败停止仍是 D01-C 前置设计任务。
SQL 的 H2/MySQL LIMIT 兼容只在本次旧 H2 上执行，实际 MySQL 仍待验证。

## 一致的工具链与服务器

- Java 编译 release17；Compiler Plugin 固定3.13.0、Maven Wrapper3.9.9。
  不使用 --add-opens，不再支持 Java8。升级构建环境与运行环境必须同步。
- [Jetty官方版本表](https://jetty.org/download.html)及[迁移说明](https://jetty.org/docs/jetty/12.1/programming-guide/migration/11-to-12.html)
  核对后选12.1.14；全部Core/EE8模块同版本，EE8保留javax Servlet4.0。
  Servlet API只由Jetty toolchain4.0.9提供，不再同时打包另一份javax.servlet-api。
- Handler.Sequence接入EE8核心适配器；显式WebAppConfiguration及注解配置，确保组装JAR内默认监听器/
  servlet/filter可加载。迁移中曾观察缺WebAppConfiguration导致IntrospectorCleaner不可加载，已补齐，
  并使WebApp启动失败直接抛出，不保留“监听端口存在、应用不可用”的假启动。
- 资源从组装JAR加载、生命周期由server管理；只有css/js/images/fonts可以绕过登录。
  不列目录，保护template/WEB-INF/META-INF；上下文前缀和原有认证/CSRF保持回归。
- SLF4J API/provider统一2.0.20，reload4j1.2.27不变；[官方2.x发布/ServiceLoader说明](https://www.slf4j.org/news.html)。
  无旧StaticLoggerBinder，无额外provider，保留旧ZK传递Log4j/绑定排除。
- 三平台CI改为Temurin17，Chrome检查和Docker非root冒烟保留。容器builder/runtime均改17，
  [官方Hub tag API](https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/17-jdk-jammy)
  在2026-10-15解析并固定摘要：
  builder `sha256:43f4431cc895d37ceb115e2e1f160545ec45caee9c1cb4246fc6bb879d58aeda`；
  runtime `sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7`。
  没有发布镜像或Release。

## 本地执行结果

OpenJDK17.0.20 / Linux，完整 `./mvnw -B -ntp clean verify`：
16单元 + 81集成 = **97项，0失败/错误/跳过**。
环境使用已配置网络代理下载依赖，未关闭TLS或Wrapper校验。
运行时依赖树由dependency-plugin3.8.1重新生成，见DEPENDENCY_TREE.txt；它不是漏洞扫描报告。
本机无Chrome/Chromium及Docker命令，不能用静态审查声称它们本地通过；以远程准确head的CI为准。

新增5项回归：

| 测试 | 验证范围 |
| --- | --- |
| HistoryStorageIT新增场景 | 未改V1生成的临时旧H2库，JDBC独立播种；中文/emoji/换行/空白/引号/反斜杠/NULL/空串/日期/非连续64位大ID；关闭连接后读取对象；新ID继续；500摘要上限；两次初始化后Flyway元数据逐字段快照不变 |
| DependencyPackagingIT新增场景 | Java17类版本61，全部Jetty模块12.1.14，唯一EE8 API，无旧ORM/Jetty9/Jakarta Servlet类；原日志测试也增加2.x provider一致性 |
| ContextPathIT新增场景 | 登录后/匿名不公开模板；受保护目录与静态目录不列表；路径遍历不取得模板；字体资产仍可用 |
| HttpsStartupIT | 临时自签证书且仅信任该证书，默认主机名验证开启；真实HTTPS登录、Secure/HttpOnly/前缀cookie、CSRF拒绝/成功写入及历史页 |
| ForwardedHeadersIT | 两个自建loopback应用，伪造X-Forwarded-Proto仅在显式开启信任时改变请求安全属性；不是实际nginx部署验证 |

原有连接、权限、导入边界、Digest/LDAP、UTF-8、根路径、Session/CSRF和真实JAR回归全部继续执行。
HistoryStorageIT改用追踪真实JDBC连接关闭，不是因为去掉ORM而删除资源释放断言。
Flyway元数据比较与大ID夹具验证限于**同一1.3.174驱动和原V1**；不是用户旧库或H2跨版本迁移成功证据。

## 仍未完成

- H2仍1.3.174，Flyway仍2.2.1；V1 Git blob仍 `fadfd7e9462b348131965f855949529ac6ceaa39`。
  没有打开用户库、重写V1、删版本记录、升级唯一数据库或自动变更卷。
- D01-C/D/E的跨版本导出/新建/导入、元数据转换、完整回滚/失败注入、实际MySQL与SBOM/漏洞/许可证审计未完成。
- 真实AD/LDAPS、双栈ZK、nginx/部署TLS、Firefox登录、超大树报文/遍历/删除工作量边界仍未验证。
- 上游 #93/#104 的旧Jetty版本方案由整体迁移superseded；#105日志/Jetty意图重写实现，MySQL部分未完成。
  #103/#102继续deferred；原14个PR/46个issue不是全部功能修好。

继续顺序：承接本PR准确head的CI及审查；D01-C先实现合成副本/隔离驱动迁移测试，
不直接改默认H2版本。实际MySQL/完整迁移尚受环境约束时，继续I02有界工作量的独立回归与实现。
