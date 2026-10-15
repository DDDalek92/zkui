# 自动化测试与 CI 规划

> 当前命令要求 JDK17，具体已执行范围见 [CURRENT_VALIDATION.md](CURRENT_VALIDATION.md)。下文保留最初 JDK8 测试策略及构建记录作为历史，不代表最新验收状态。测试只使用自建 loopback 服务、合成身份和临时数据库。

## 已实现的第一批测试

- `NodeValueTest`：3 项断言用例，覆盖 UTF-8 读取、空格/换行保留、导出换行以及 null 值。
- `ZooKeeperOperationsIT`：4 项集成用例，覆盖 CRUD、导出/搜索脱敏、导入覆盖策略和 ZooKeeper 只读 ACL 拒绝写入。
- `PackagedApplicationIT`：1 项完整 HTTP 冒烟，启动真正的胖 JAR，验证匿名跳转、登录、首页树、节点创建/修改/删除、导出、历史落库及退出后的会话访问。
- `mvnw` / `mvnw.cmd` 固定 Maven 3.9.9，并校验发行包 SHA-256；JUnit 4.13.2 与 Surefire/Failsafe 3.2.5 只负责测试。
- CI 使用 JDK17，执行 `./mvnw -B -ntp clean verify`，保留报告；测试数量为零将失败。CI 的远程运行状态应查看 PR 检查，不能以本地通过代替远程通过。

运行：Linux/macOS `./mvnw clean verify`；Windows PowerShell `.\mvnw.cmd clean verify`。JDK17 须自行安装，Wrapper 只负责 Maven；不再需要 ActiveJDBC 插桩。

隔离：测试不接受外部 ZooKeeper 地址；嵌入式服务只绑定 `127.0.0.1`、随机端口，数据库和配置位于临时目录。测试进程在 finally 中退出。HTTP 用例使用模拟随机密码。已新增 `ImportAuthorizationIT` 的 6 项 HTTP 权限回归，覆盖 USER/未知角色拒绝、匿名跳转、解析及远程取文件前拒绝、ADMIN 正常操作。另有 `RestAccessIT` 的 10 项回归，覆盖默认/缺失/非法/旧配置、角色、匿名兼容、混合大小写密码字段、参数错误及精确路由。当前共 3 个单元测试、21 个集成测试。仍没有 MySQL、Digest/LDAP 或 CSRF 测试，不能据此宣称权限安全已验收。

## 0. 基线采集

1. 记录 Fork `master` SHA `1b3e7c1`、系统、JDK、Maven、Docker 版本和测试数据隔离环境。
2. 使用 JDK 8 + Maven 执行 `mvn -B -ntp clean verify`；完整留存日志、错误、构建产物和耗时。若失败先分析依赖下载/插件/JDK，不偷偷改业务代码。
3. 启动**测试用** ZooKeeper 与 zkui，执行独立冒烟：启动、登录、节点增删改查、搜索、导入导出、退出。禁止连接生产 ZooKeeper。
4. README 声称 Java 7，但 POM 编译目标为 Java 8；从 Java 8 基线开始，其他版本的兼容性由实测确定。

## 1. 回归层次

| 层次 | 测试目标 | 参考路径/工具（待集成） |
| --- | --- | --- |
| 单元测试 | ACL 解析、权限判断、路径/输入校验、UTF-8 转换、数据脱敏 | JUnit（版本与现有 JDK 相容） |
| ZK 集成测试 | 会话建立、重连、CRUD、ACL/Digest、断链/超时、批量节点、搜索 | 独立测试 ZooKeeper 或 Testcontainers |
| 数据库测试 | 初始化、Flyway 迁移、历史保留、备份/还原、H2 与可选 MySQL | 临时隔离库、迁移前后快照 |
| Servlet/API 测试 | 匿名 REST 访问、USER/ADMIN 权限隔离、CSRF、登录/退出、输入边界 | HTTP 集成测试 |
| UI 冒烟 | Chrome/Firefox 登录、节点展示、多行文本、中文、导入导出 | Playwright 或适配的浏览器自动化 |
| 资源/性能 | 深层路径和大量子节点、内存、连接数、检索性能 | 分级负载，#6 样例逐步扩容 |

## 2. 最先补齐的失败用例

- 非管理员账户尝试 `POST /import` 必须被拒绝；ZooKeeper 的任何节点均不得被更改（SEC-01）。
- 匿名调用 `GET /acd/appconfig` 不能获得敏感值（SEC-02）；未授权身份访问受限节点不得泄漏数据。
- 普通登录成功后会话标识应正确轮换；退出后旧会话失效；跨站状态修改请求受保护。
- ZooKeeper 不可达时在可控时间内失败，不能无限等待、连接泄漏或 NPE；正常重连后 CRUD 可用。
- UTF-8 中文、换行、空格和特殊字符实现端到端往返一致；二进制节点不能被静默损坏。
- 导入的来源 URL 不得访问不允许的内部/本地资源；上传大小与数量受限。
- 数据库迁移失败不得被静默误判为成功；真实历史文件先备份、再在复制环境验证迁移。
- 默认 ADMIN/USER 对 CRUD、Export、Import、RestAccess 等接口的授权矩阵清晰且一致。

## 3. 测试用数据与隔离

- 测试专用随机凭据、两级角色、开放与 Digest ACL 节点、中文/多行/大体积节点、含模拟“secret”的假值。
- Docker/Testcontainers 固定版本与端口策略；测试完销毁容器和临时数据，不触及线上实例。
- 对 `env=dev` 的 `flyway.clean()` 做明确隔离：任何有历史数据的 H2/MySQL 数据库禁止作为测试目标。
- 将测试配置与生产配置拆离，不提交真实密钥或数据库文件。

## 4. GitHub Actions CI

已创建 `.github/workflows/ci.yml`，支持 `push`、`pull_request` 和手动触发：JDK 8 → 固定 Maven → 编译 → 单元测试 → 打包 → 集成测试 → 保存报告。工作流使用只读权限，Actions 固定提交 SHA，不自动合并或部署。后续仍需静态检查/依赖安全扫描、其他 JDK 兼容矩阵、容器构建和真实浏览器冒烟。

CI 要求：所有变更通过测试、日志不得泄漏模拟密钥、安全测试失败阻止合并；如果基础依赖无法解析，标记为基线失败，不把“跳过测试”视为成功。

## 5. 验收门槛

首个基线阶段：可稳定重现一次完整构建与冒烟过程，失败原因明确，测试目录真正有可执行断言。历史 PR 集成阶段：每个修复有对应回归用例、安全与数据迁移相关工作另行审批。稳定发布阶段：P0 风险消除、关键业务链路通过、部署与回滚文档完善。
