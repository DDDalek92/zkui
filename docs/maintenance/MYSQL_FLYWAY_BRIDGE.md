# D01-D：MySQL 现代 Flyway 桥接预演

2026-10-15；从远程 master `5182459ee6e8500268e65ec589124749590a0762`（#37）建立隔离检出。这一步先验收引擎/模块及旧 checksum 转换，**不切换应用 MySQL bootstrap，也不移除运行 Flyway2**。不能把引擎预演称为完整应用或用户历史库升级通过。

## 为什么不能直接替换 API

Flyway2 对原资源的原始字节计算 CRC32，Flyway4/13 的 SQL checksum 按 UTF-8 行规范化。Flyway4 官方 `Flyway.execute` 在 `upgradeIfNecessary` 成功后会调用内部 checksum/description 转换；MySQL 格式脚本会删除 `version_rank` 列及旧索引、将主键改为 `installed_rank`、调整 version 可空性，并将 INIT 类型转为 BASELINE。这是官方旧格式转换，不是保持全部元数据字节不变。

因此必须先用原 Flyway2 验证未桥接副本，再调用桥接引擎。不能先转格式然后把 checksum 变绿当作旧库完整性证明；测试不会调用 repair、baseline、clean 或删除版本记录，也不修改任何 V1 资源。

## 固定安全边界和执行矩阵

`MySqlFlywayBridgeSmoke` 仅在 `ZKUI_OWNED_MYSQL_FIXTURE=true` 下工作，只接受固定模式、loopback 13306/13307、合成凭据及白名单测试数据库。两个容器均为既有 CI 自建并固定摘要的 MySQL5.6.51/8.4；现代连接使用 VERIFY_IDENTITY、临时 CA/信任库、有效 TLS cipher 和 UTC。无用户 URL、生产连接或唯一旧库升级入口。

1. 保持既有 `MySqlMigrationSmoke` 的真实旧 V1 生成与当前应用 8.4/TLS/HTTP/错误拒绝回归。旧 `zkui_fixture` 在桥接期间只读，前后比较全行及全部旧元数据。
2. 从旧夹具逻辑导出创建两个独立旧版本副本 `zkui_bridge`/`zkui_bridge_bad`，不对原库桥接。
3. 损坏副本仅为负向夹具，把 checksum 置123。原 Flyway2 validate 必须失败；保留 `version_rank`，全行/元数据与失败前一致，绝不让 Flyway4 覆盖损坏证据。
4. 正常副本先核对原始字节 CRC32、原引擎 validate，再隔离执行 Flyway4.2 官方转换。历史行和所有保留元数据字段逐字段一致，仅接受预期格式/checksum 转换。重复桥接不再改变行或记录。
5. 保存桥接快照，再将这个副本导出到新的 MySQL8.4 测试数据库。独立进程加入候选 `flyway-mysql:13.10.3` 模块，现代 Flyway 两次 migrate/validate 后快照不变；新增历史 ID 必须超过原大于2^53的最大 ID，再重开核对追加行及全部元数据。另一个空库使用独立 MySQL 方言新建/写入/重开并核对规范化 checksum；在这个独立新库上损坏checksum后现代引擎必须拒绝，失败前后行/记录不变、不repair。

旧进程只使用 test/classes、原资源、Connector8.0.16、Flyway2和桥接Flyway4，不使用现代fat JAR伪装旧引擎；现代进程使用运行包内Flyway13/Connector26.7和单独复制的MySQL模块，不加载桥接Flyway4。候选模块只是dependency-plugin复制文件，不加入应用依赖图；打包回归明确检查它尚未进入运行JAR。

## 证据与未完成项

本地只能执行Java构建/既有隔离回归，环境没有Docker；真实MySQL桥接只由对应准确head的CI mysql job运行。本地编译成功不是MySQL验收成功。最终结果、失败和修订以 CURRENT_VALIDATION.md及对应Fork PR为准；所有六job准确head成功并审查后才允许合并。

通过本预演后，下一项仍需实现应用MySQL的现代API/module路径，启动前拒绝未桥接/未知格式、版本/checksum/结构异常，保留真实Main/HTTP/新审计/重启/TLS失败与原库回退证据，再移除运行Flyway2。现在的应用旧MySQL dev-clean行为没有改变，保留数据必须env=prod。没有将D01、任意历史时间精度/时区/加密/认证/权限/磁盘故障、SBOM/漏洞/许可证或正式发布标为完成。I02原Windows9s:1s根因限制不变。

官方依据（2026-10-15核对）：

- https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql （MySQL需独立开源org.flywaydb:flyway-mysql模块；不降级TLS策略）
- https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/java/org/flywaydb/core/Flyway.java （格式转换后内部checksum/description转换）
- https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/resources/org/flywaydb/core/internal/dbsupport/mysql/upgradeMetaDataTable.sql （MySQL特定格式转换）

没有修改上游状态、发送评论、发布公共镜像或正式Release。
