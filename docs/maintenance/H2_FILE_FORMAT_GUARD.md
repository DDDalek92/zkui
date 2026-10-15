# D01-C：H2 本地文件格式启动前保护

2026-10-15；从远程 master `2c9c68e3088d1b20d078992c161624a816f782bc` 隔离检出。本增量修复实际应用的路径误配风险，不切换运行 H2/Flyway，也不自动迁移任何数据库。

## 复现与修复

用测试独占目录和隔离 H2 2.5.252 进程建立含大 ID 记录的 `history.mv.db`。未修复的 1.3.174 应用对同一数据库 base 调用 `Dao.checkNCreate()` 会成功，忽略新版文件并在旁边建立空的 `history.h2.db`；新增回归先以 `A different on-disk format must fail before creating an empty history database` 失败，1 failure / 0 errors，不是依赖解析失败。

现在 `H2FileFormatGuard` 在构造 Flyway datasource 前、每次 DAO 连接前检查文件格式。1.3 PageStore 驱动发现同 base 的 `.mv.db` 即拒绝；隔离 2.5 候选驱动发现 `.h2.db` 也拒绝。两种文件并存不自动选择、删除、改名或升级。拒绝先于 `env=dev` 的 clean，也先于 Main 构造 Jetty，启动日志不包含驱动原因链或 JDBC 凭据。

支持普通本地 base、`file:`、相对/绝对路径、`~/` 及 URL 设置后的 base 提取；同时检查 OS 路径与 canonical-file 路径，不先词法抹掉 symlink/`..`。其他格式名称被目录或符号链接占据也拒绝；属性读取的权限/IO 错误不作为“文件不存在”。不接受在 URL 中带 `.h2.db`/`.mv.db` 后缀、含糊的转义分隔符、未支持的虚拟文件系统、H2 class/URL 不匹配、未验收的 H2 1.4 或显式 MV_STORE 格式切换；旧驱动的 `h2.mvStore=true` 同样拒绝。这些路径须先在隔离副本上另行设计验收，不能绕过保护自动升级。

`mem:`、`tcp:`、`ssl:`、`zip:` 无可按普通本地 base 检查的文件，保护器不连接也不声称核查它们；服务器端/归档兼容仍不在本增量范围。MySQL 路径不变。检查不是对恶意并发改文件的互斥锁或 JDBC URL 沙箱；切换仍要求停写、封存及独立目标路径。

## 回归与证据边界

- 真实旧运行包拒绝由新驱动创建的文件；原 `.mv.db` SHA256 不变，没有新建 `.h2.db`；直接 DAO 查询也不能绕过。
- 两种实际格式并存、相对 URL、`env=dev` 的真实 JAR 非零退出且未监听 HTTP；两份文件 SHA256 不变。测试只启动自己拥有的子进程，使用合成记录和临时库。
- 只加载候选 H2 2.5.252 与生产保护器的独立 JVM，不打开连接，拒绝旧文件且不创建新版文件；这只是反向保护器验收，不是新版 Main/Flyway/HTTP 应用验收。
- 单元测试覆盖新路径/既有旧文件不打开、普通设置、相对路径、home 展开、其他后端不变、拒绝未支持形式及格式切换。

首次定向验证（中间修订）为22单元+10集成通过；最终完整本地与准确新 head 的 CI 结果见本 PR 和 CURRENT_VALIDATION.md，不能用中间结果替代最终 head。原始 V1 blob 仍为 `fadfd7e9462b348131965f855949529ac6ceaa39`，POM 未改。下一项继续完整新版 H2/Flyway 的新建与历史桥接 Main/HTTP，以及 MySQL 旧 API/checksum 兼容；磁盘/权限失败矩阵、漏洞/许可证/SBOM 等门槛仍保留。

中间完整回归23单元+114集成=137项通过；最终修订定向23单元+10集成=33项BUILD SUCCESS。准确head的完整跨平台/六job通过与审查合并结果以对应PR最终证据为准。没有把候选保护器通过写成新版运行包已验证。
