# D01-D：MySQL 现代运行路径

旧MySQL独立方言历史的补充验收见[MYSQL_DIALECT_HISTORY.md](MYSQL_DIALECT_HISTORY.md)：旧Flyway2实际生成、匹配location校验/副本桥接、未桥接拒绝和现代Main重启；新head全CI仍须独立核验，不把本有限夹具扩大成任意旧部署/精度/时区支持。

2026-10-16 UTC（洛杉矶10月15日）；隔离基线master `3e5873d5091429788cbde5bc28b9b12586addd24`（#38）。应用MySQL切换到org.flywaydb core/mysql 13.10.3，移除运行com.googlecode Flyway2；旧2/4仍仅是隔离历史夹具jar，不进入运行依赖图。不是用户库自动升级工具，准确新head全部CI成功并审查后才准入。

## 启动策略

- 仅H2和MySQL8.4+为支持的历史存储后端；其他产品/缺默认数据库拒绝。H2策略不变。MySQL先读取元数据，**在现代Flyway构造/运行前拒绝仍有version_rank的旧格式**，即使env=dev也不自动转换、清库或repair。
- 空库使用独立db/mysql-migration；无版本表但已有TABLE/VIEW的库拒绝，不能旁路新建空历史。已有库只接受唯一成功SQL/V1__Core.sql、正installed_rank、已识别的行规范化checksum。原V1和MySQL新建方言分别选择原资源；拒绝未知版本/多余记录/坏checksum。
- 已有histories逐列核对顺序、JDBC类型、100/500/500宽度、可空性、自增ID和单列主键。异常在Main绑定HTTP前终止，错误不回显凭据或数据库内容。MySQL和H2都cleanDisabled=true、baselineOnMigrate=false、validateOnMigrate=true；env=dev不再清历史。
- 原始db/migration/V1__Core.sql不改；离线转换必须先旧引擎校验副本，再用官方Flyway4转换格式/checksum，保留原库/原程序组合。不能让新版直接打开唯一旧库，也不能删版本记录或用repair隐藏损坏。

## 打包不是外部模块预演

#38仅证明外部候选模块可用；本增量把MySQL模块真正加入运行图，使用显式assembly描述符的metaInf-services合并服务注册文件，避免覆盖H2或MySQL的Flyway插件。打包测试同时检查两种provider、单一日志provider、固定13.10.3版本及旧com.googlecode Flyway类不存在。所有既有H2新建、桥接Main/HTTP/重启/拒绝回归继续运行。

## 真实MySQL CI与有限范围

仍只使用CI自建loopback MySQL5.6.51/8.4、合成凭据、临时CA/信任库、VERIFY_IDENTITY和UTC。旧源生成/原引擎校验/桥接进程与现代运行包隔离，Main不加载旧Flyway。

1. 原始旧格式逻辑恢复副本在DAO及真实Main中拒绝，Main非零退出且无HTTP；所有旧行/版本记录不变，dev也不清库。
2. 官方桥接副本分别导入独立引擎预演库和实际应用库，互不干扰。现代应用两次初始化保留完整历史/桥接元数据（含dev），读取Unicode/NULL/大ID，新增审计ID超过历史最大值。
3. 实际打包Main登录、带前缀history页面、USER拒绝；另起第二个Main连接同一个MySQL目标，验证全部已追加行/元数据未变。这里新增审计通过DAO执行，不冒充MySQL上的实际ZK写入/审计跨系统事务验收。
4. 独立MySQL方言空库新建/写入/重开；坏checksum、错误密码、不可信TLS、未知非空库、额外未来版本及结构漂移均拒绝，不repair/清库。负向记录保留；不删除Flyway行恢复测试状态。

本机没有Docker，只能执行Java/H2/组装回归；真实MySQL结果必须取对应准确head的CI mysql日志，不能从本地编译/H2通过推断。更广旧MySQL方言历史、时区/精度、加密/认证/权限/实际磁盘故障、回退增量保全、安全扫描/许可证/SBOM与部署矩阵仍未全部完成，不将D01或所有issue标为完成。MySQL8.4为本Fork实际回归目标，不宣称任意版本的厂商认证。

依据：

- https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql （独立开源MySQL模块）
- https://maven.apache.org/plugins/maven-assembly-plugin/examples/single/using-container-descriptor-handlers.html （metaInf-services聚合）
- [MYSQL_FLYWAY_BRIDGE.md](MYSQL_FLYWAY_BRIDGE.md)（原引擎先校验/官方转换及不可省略的边界）

最终本地、准确head和合并master证据以CURRENT_VALIDATION.md及对应Fork PR为准；没有用户数据库操作、上游状态/评论变动、公共镜像或正式Release。
