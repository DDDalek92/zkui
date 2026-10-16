# D01：旧 Flyway 的 MySQL 方言历史

2026-10-16 UTC；隔离基线master `3574b45ec0e2531921cf9729ddb164f43f4af315`（#39）。此项只扩展测试和证据，不修改运行代码、POM、原V1或MySQL方言V1，不把已有库当空库重建。

## 补充的缺口

#38/#39覆盖原始db/migration/V1的旧Flyway2桥接和现代MySQL方言的新建，但还没有让旧Flyway2先应用db/mysql-migration/V1，再桥接到现代应用。本项专门触发第二种已识别checksum/location路径，不能用原V1的绿灯代替。

MySQL5.6.51仅作为隔离旧引擎/旧格式生成器，使用真实Flyway2.2.1、Connector8.0.16及不变的MySQL方言资源，生成NULL/空值/Unicode emoji/空白/换行/500字符/非连续大于2^53 ID的合成库。这不是声称#28应用支持5.6（其支持基线仍8.4+），也不是实际用户部署复现。目标仍为自建MySQL8.4/Connector26.7/VERIFY_IDENTITY TLS；UTC和DATETIME原有秒精度只代表该夹具，不扩大到任意时间精度/时区。

## 独立副本验收

`MySqlDialectHistorySmoke`只接受固定seed/bridge/verify模式，必须有owned-fixture环境标记；端口只为loopback13306/13307，数据库仅固定source/copy/bad三项，凭据为合成CI专用值。

1. 原引擎生成source和全行/全旧元数据快照。逻辑导出到两个独立旧副本，source不桥接；原始新旧应用组合/唯一用户库均不操作。
2. bad副本checksum置123后，匹配方言location的原引擎validate必须先失败；version_rank及全部行/记录不变，禁止Flyway4先自动重算checksum掩盖损坏。
3. 正常副本先核对原始字节CRC32、原引擎validate，再由Flyway4.2官方格式/checksum转换。仅接受预期格式转换和行规范化CRC32；历史行与全部保留元数据字段逐字段一致，重复桥接不变，source全快照不变。
4. 未桥接source逻辑副本和桥接copy分别导入MySQL8.4。未桥接目标在DAO及真实Main中拒绝、无HTTP监听，env=dev也不转换/清库，失败后全快照保留。
5. 现代Dao两次启动（含dev）必须识别MySQL方言checksum/location并保持全行/桥接元数据不变；追加审计ID超过原最大值。实际fat JAR两次独立Main进程登录、带前缀history页面及USER拒绝成功，目标全部行/元数据不变。现代classpath不含旧引擎或外部候选MySQL模块。新增审计通过DAO执行，不声称验证了MySQL上的实际ZK写入共享事务。

复用既有行序列化、固定合成数据和真实Main检查，仅向测试包开放所需helper；原V1路径和所有旧回归继续运行，三平台Java回归的计数不把新增MySQL命令行断言假装成JUnit数量。

## 验证和剩余门槛

本地没有Docker，Java构建/既有H2回归不能证明新增MySQL路径通过；新路径只认对应准确head CI mysql job的三个标记：

- `MYSQL56_DIALECT_FLYWAY2_GENUINE_HISTORY_SEEDED`
- `MYSQL56_DIALECT_BRIDGE_BAD_CHECKSUM_REFUSED_ROWS_FIELDS_REPEAT_SOURCE_PRESERVED_OK`
- `MYSQL84_TLS_OLD_DIALECT_UNBRIDGED_REFUSED_BRIDGED_APP_MAIN_RESTART_IDS_DEV_PRESERVED_OK`

仍须准确head所有六job成功、逐日志和源码审查后合并。完整结果见CURRENT_VALIDATION.md和对应Fork PR，不以#39绿灯代替新head。

后续：更多真实旧服务/认证/加密/时间精度、实际权限/磁盘及故障恢复矩阵；SBOM/漏洞/许可证与其余部署验证，及I02原环境资源边界。此有限方言历史路径不代表D01或全部上游问题完成，不自动升级用户库，不删除版本记录或改写已应用V1。

核对的固定版本官方源码：Flyway2.2.1及4.2.0的`Flyway.setLocations(String... locations)`；Flyway4在`upgradeIfNecessary`后内部转换checksum/description，因此原引擎验证不能省略：

- https://github.com/flyway/flyway/blob/flyway-2.2.1/flyway-core/src/main/java/com/googlecode/flyway/core/Flyway.java
- https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/java/org/flywaydb/core/Flyway.java
