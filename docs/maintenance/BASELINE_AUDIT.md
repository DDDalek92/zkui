# ZKUI 接管审计：代码基线（初稿）

> 审计日期：2026-10-15。目标仓库：[DDDalek92/zkui](https://github.com/DDDalek92/zkui)；上游：[DeemOpen/zkui](https://github.com/DeemOpen/zkui)。
>
> 基线提交：`1b3e7c133ab76a26c9c5b3653679d77d0cd6358d`（双方 `master` 相同）。本文件是**静态审计初稿**，尚未运行 Maven、Docker、ZooKeeper、浏览器或真实 LDAP 测试；任何运行兼容性与漏洞可利用性结论均待验证。

## 目标和边界

- 接管旧代码，不改变当前业务行为；先建立可验证的测试基线，再按风险整合社区 PR 和 Issue。
- 本阶段仅增加维护文档，不改源码、配置、依赖或主分支。
- 上游截至本轮盘点有 **14 个开放 PR、46 个开放 Issue**；并非 60 个独立缺陷。
- [许可证文件](../../LICENSE-2.0.txt)为 Apache License 2.0，保留源版权与署名。

## 当前架构

| 模块 | 入口/文件 | 现状 |
| --- | --- | --- |
| 应用启动与 Jetty | [`Main.java`](../../src/main/java/com/deem/zkui/Main.java) | 内嵌 Jetty；从工作目录加载 `config.cfg`；启动前执行数据库迁移 |
| Web 控制器 | [`controller/`](../../src/main/java/com/deem/zkui/controller) | Login、Home、Import、Export、RestAccess、Monitor、ChangeLog 等 Servlet |
| 会话/认证 | [`AuthFilter.java`](../../src/main/java/com/deem/zkui/filter/AuthFilter.java)、[`Login.java`](../../src/main/java/com/deem/zkui/controller/Login.java) | 基于 Session 的身份状态、本地用户、LDAP |
| ZooKeeper 操作 | [`ZooKeeperUtil.java`](../../src/main/java/com/deem/zkui/utils/ZooKeeperUtil.java)、[`ServletUtil.java`](../../src/main/java/com/deem/zkui/utils/ServletUtil.java) | 查询、CRUD、搜索、导入导出、ACL、连接创建 |
| 历史记录数据库 | [`Dao.java`](../../src/main/java/com/deem/zkui/dao/Dao.java)、[`V1__Core.sql`](../../src/main/resources/db/migration/V1__Core.sql) | H2 默认数据源，可配置 MySQL；Flyway 迁移，ActiveJDBC |
| UI | [`webapp/`](../../src/main/resources/webapp) | Freemarker、Bootstrap、jQuery 静态资源与模板 |
| 容器部署 | [`docker/`](../../docker) | 基于 `java:8` 的 Dockerfile，启动脚本 `bootstrap.sh` |

## 构建工具和兼容性风险

- [`pom.xml`](../../pom.xml)：Maven 产物 `com.deem:zkui:2.0-SNAPSHOT`，compiler plugin 2.3.2，`source`/`target` 为 Java 8。
- README 声称需要 Java 7，与当前 POM 的 Java 8 编译配置不一致，应先纠正文档，最终以可复现构建验证为准。
- 依赖版本包括 Jetty `9.4.26.v20200117`、ZooKeeper `3.5.7`、H2 `1.3.174`、SLF4J `1.7.6`、Freemarker `2.3.8`、Flyway `2.2.1` 等；老旧不代表每个组件均已证实存在可利用漏洞，需要依赖图与安全数据库扫描。
- `src/test/java/com/deem/zkui/test/ZKUITest.java` 只有打印 `Testing!` 的 `main()`，没有功能断言、JUnit 测试或可用的回归测试。
- 仓库树不存在 `.github/workflows`，没有随仓库交付的 GitHub Actions CI 配置。
- `docker/Dockerfile` 依赖历史 `java:8` 镜像和 `MAINTAINER` 指令；Docker 可构建性未验证。

## 优先核查的静态代码问题

1. **权限边界**：`AuthFilter` 只验证已登录；`Home.doPost` 有显式 ADMIN 检查，但 `Import.doPost` 未发现同等检查。只读账户调用导入接口的可能性必须加入首批安全回归用例。详见 [SECURITY_AUDIT.md](SECURITY_AUDIT.md)。
2. **连接生命周期**：`ZooKeeperUtil.createZKConnection` 实际包含循环等待连接的逻辑，不能简单视为“完全没有等待”；但它仅轮询状态，失败后仍可能返回未连接的对象。`ServletUtil.getZookeeper` 可能返回 null；部分调用方直接解引用，需验证超时和重连。
3. **连接复用**：`ServletUtil` 将连接缓存进 Session；`RestAccess` 最后调用 `closeZookeeper` 关闭可能来自 Session 的连接，需要验证复用状态和资源释放。
4. **数据库迁移**：`Dao.checkNCreate` 捕获迁移异常后仍继续启动，可能在未完成迁移时运行；`env=dev` 会执行 `flyway.clean()`，必须在测试环境隔离。
5. **文本编码**：`ServletUtil.externalizeNodeValue` 与若干 ZooKeeper 写入代码使用默认字符集，而其他路径使用 UTF-8；应以中文和二进制数据测试验证。
6. **性能**：`ZooKeeperUtil.searchTree` 对整棵树递归导出后搜索，需针对大数据量控制内存和访问延迟，关联上游 [#6](https://github.com/DeemOpen/zkui/issues/6)。
7. **安全数据**：导入日志、写入日志、变更历史可能记录节点值与配置数据；后续实施应先定义脱敏策略。

## 已验证与未验证

| 项目 | 状态 |
| --- | --- |
| GitHub Fork 可访问且具备写权限 | 已通过连接验证 |
| Fork `master` 与上游 `master` 基线一致 | 已核对 SHA |
| Maven 依赖解析、`mvn clean verify` | **未运行** |
| JDK 8/11/17/21 实际兼容性 | **未运行** |
| ZooKeeper CRUD / ACL / LDAP | **未运行** |
| Docker 构建、发布镜像 | **未运行** |
| 安全扫描、漏洞可利用性验证 | **未运行** |

下一步：按 [TEST_STRATEGY.md](TEST_STRATEGY.md) 先获得绿灯或明确失败的基线测试，在隔离的 ZooKeeper 环境复现缺陷。参考 [PR_REVIEW.md](PR_REVIEW.md)、[ISSUE_TRIAGE.md](ISSUE_TRIAGE.md) 和 [ROADMAP.md](ROADMAP.md) 选择后续工作。
