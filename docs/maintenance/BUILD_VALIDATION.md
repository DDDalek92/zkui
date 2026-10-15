# 构建和首批测试验证记录

> 后续进度：文档 PR #1、测试基线 PR #2、导入权限 PR #3 已审查合并；SEC-01/02 的复现和修复详见 [SECURITY_VALIDATION.md](SECURITY_VALIDATION.md)。下文 8 项测试及未验证列表记录初始构建基线，不代表当前全部进度。

日期：2026-10-15（Asia/Singapore）。上游应用基线 `1b3e7c133ab76a26c9c5b3653679d77d0cd6358d`；承接 Fork 文档分支 `maintenance/baseline` 的 `c4d0292dcc198ee776ab129bcb2254e55cad4c42`。本轮分支：`maintenance/test-baseline`。

## 结论

原应用在 JDK 8 上能够构建和打包。加入可执行测试后，3 个单元测试和 5 个集成测试通过，无失败、错误或跳过。打包后的 JAR 完成真实 HTTP 和 ZooKeeper/H2 操作。应用源码 `src/main` 和运行时依赖版本均未修改。

这是一条可继续维护的构建基线，不代表旧代码全部问题已经修复。原开放 PR 和 Issue 尚未合并或关闭。

## 工具链与构建

| 项目 | 实际值 / 结果 |
| --- | --- |
| 系统 | Linux x86_64 |
| 成功工具链 | Eclipse Temurin JDK `1.8.0_504`，Maven `3.9.9` |
| 初始 JDK 17 尝试 | OpenJDK `17.0.20`；失败于 ActiveJDBC instrumentation `1.4.1` 的模块反射访问 |
| JDK 17 失败摘要 | `java.base` 未向 unnamed module 开放 `java.net`，无法反射调用 `URLClassLoader.addURL` |
| 基线 JDK 8 | 原始 POM `clean verify` 返回成功并生成两个 JAR；原有测试只有打印语句，不能作为测试通过证据 |
| 新测试 | `clean verify` 必须执行 Surefire 和 Failsafe，不允许零测试成功 |
| 构建产物 | `target/zkui-2.0-SNAPSHOT.jar` 与 `target/zkui-2.0-SNAPSHOT-jar-with-dependencies.jar` |

本地网络环境最初出现代理失效和 JDK 8 信任库缺少环境 CA 的下载错误，均属环境问题。验证时使用当前代理和系统 Java 信任库；未关闭 HTTPS 校验，未把代理/信任库路径提交进仓库。用户通常只需正确设置 JDK 8 和正常 Maven 网络访问。

Maven Wrapper 使用官方生成的 `only-script` 模式，固定 Maven 3.9.9。发行 ZIP 先核对官方 SHA-512，再将计算所得 SHA-256 固定在 `.mvn/wrapper/maven-wrapper.properties` 中；没有引入额外二进制 Wrapper JAR。

## 实际测试覆盖

| 类 | 用例数 | 验证行为 |
| --- | ---: | --- |
| `NodeValueTest` | 3 | UTF-8 节点解码、首尾空格与多行、导出转义、null 导出 |
| `ZooKeeperOperationsIT` | 4 | 文本 CRUD、USER 搜索/导出密码字段遮蔽、ADMIN 搜索、导入不覆盖/覆盖、只读 ZK ACL 拒绝修改 |
| `PackagedApplicationIT` | 1 | 真实 JAR 启动、匿名访问跳转、登录会话、首页树、HTTP 创建/更新/删除、导出、H2 历史记录、退出 |

测试服务端与客户端使用现有 ZooKeeper 3.5.7 依赖。每个服务独立绑定 loopback 随机端口，使用临时目录；HTTP 用例独立启动应用子进程，使用随机模拟口令和临时数据库，退出时终止进程。没有连接现有数据库或 ZooKeeper，没有运行 `env=dev` 清库。

## 观察到但未在本轮修复的问题

- JDK 17 构建兼容性失败需要单独升级/替换旧工具链，不能宣称应用已支持全部新版 Java。
- 运行日志包含 Jetty ASM 版本警告及 Freemarker JavaBeans introspection 类型不匹配警告；当前受测页面和历史记录检查仍通过，应后续定位并纳入依赖迁移。
- `Home.doPost` 在所有 action 前直接对 `searchStr` 调用 `trim()`，省略表单该字段会触发 500。冒烟测试提交与现有页面一致的完整表单；缺失参数处理作为后续输入校验任务，不在测试基线中改变业务行为。
- 尚未验证 USER 对 `/import` 的写入越权、匿名 REST 敏感配置、CSRF、会话轮换、URL 导入边界。上述安全项仍优先排队。
- Docker、Windows 实机、macOS、真实浏览器、MySQL、LDAP/Digest、超大节点以及历史数据升级/回滚仍未验证。

## CI 与复现

Linux/macOS：`./mvnw -B -ntp clean verify`。

Windows PowerShell：`.\mvnw.cmd -B -ntp clean verify`。

`mvnw test` 只跑 3 个单元测试；完整 8 项测试需使用 `verify`。XML/文本报告位于 `target/surefire-reports`、`target/failsafe-reports`。工作流在成功或失败时均上传报告及构建日志，保留 14 天；远程 Actions 结果需另行查看，不以文档中的本地通过代替远程通过。

下一步优先单独验证并修复 SEC-01/SEC-02，为每项补上失败复现和修复后的安全回归，再整合社区低风险补丁。
