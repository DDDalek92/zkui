# D01-C：新版 H2/Flyway 真实应用运行时

2026-10-15；隔离检出接续远程 master `a41ec71817026e32a780699ceebe72844fbffb9a`（#36）。本增量实现运行 H2 2.5.252 / 开源 Flyway 13.10.3，不是自动迁移工具、任意历史库放行或完整 D01/正式发行完成。准确新 head 全部六项 CI 必须成功后才可审查合并。

## 运行策略与兼容边界

- H2 路径使用 `org.flywaydb` 现代 API；新库只应用独立 `db/h2-migration/V1__Core.sql`，采用标准 identity / TIMESTAMP(3) / 原字段宽度。原始 `db/migration/V1__Core.sql` blob 仍为 `fadfd7e9462b348131965f855949529ac6ceaa39`，不改写已应用 V1。
- 已有新格式目标必须经过离线 Flyway4 桥接，保留桥接后的原 V1 checksum 和迁移记录；应用按受识别 checksum 选择原资源，只 migrate/validate，不 repair/baseline/删除版本记录。不接受仍有 `version_rank` 的原 Flyway2 元数据、坏 checksum、失败/未知版本、额外未来版本或不匹配的表结构/主键/可空性/宽度。
- Flyway13 新建 H2 元数据表会生成其内部 `installed_rank=-1` / TABLE 标记。实测“只允许一行”会错误拒绝第二次启动，现仅接受官方格式的可选 TABLE 标记加唯一成功 V1；不把任意 TABLE/未来版本笼统忽略。桥接库不添加或重写该标记。
- `MODE=LEGACY` 在每次 DAO 和 Flyway 连接上明确指定。普通模式插入显式 ID9007199254740993 后下一自增为1；LEGACY 下为9007199254740994。`SET MODE LEGACY` 之后关闭重开不保证模式保留，因此不能仅在迁移 SQL 中设置一次。冲突/重复 MODE 在连接前拒绝，旧样例隐式相对 base `jdbc:h2:zkui` 规范为 `jdbc:h2:./zkui`。
- 固定 PUBLIC/默认大写标识符语义；INIT、改变 schema/search path 或大小写规则的未验收设置在连接前拒绝，不执行其副作用后再猜测元数据。文件格式保护继续先于所有连接：`.h2.db` 拒绝，两个格式并存拒绝，不自动选择/改名/覆盖。
- 现代 H2 路径始终 `cleanDisabled=true`，**即使 env=dev 也不自动清历史**。MySQL 为保持已验证旧 API/checksum 路径，暂继续 `com.googlecode.flyway:2.2.1`，其旧 dev-clean 行为未在本增量改变；保留数据必须使用 prod。这不是“所有过时迁移依赖已移除”。

## 真正隔离旧引擎与真实应用验收

旧夹具使用单独的 H2 1.3.174 / Flyway2 jar 和当前资源，Flyway4 仅桥接子进程可见；不再用已升级的 fat JAR 伪装旧驱动。候选子进程也只看见一个 H2 驱动，实际版本仍逐次核验。运行包检查确认 H2 2.5.252 / Flyway13.10.3，ActiveJDBC 仍不存在。

合成旧库包含三条原历史：NULL/空值、中文 emoji、毫秒日期、空白/换行/引号/反斜杠、500字符和大于2^53的非连续 ID。原引擎先验证，再在工作副本上运行官方 Flyway4 元数据转换、旧驱动 SCRIPT、独立新路径 H2 RUNSCRIPT FROM_1X。封存库和原始导出不动。

真实 fat JAR 的 Main 首次启动读取桥接目标，不先用替代 bootstrap 代跑。HTTP 检查 `/migrated/history` 的原行/精确 ID/中文和按节点筛选，USER 的 GET/POST 均403；ADMIN 通过 CSRF 保护的真实节点写入产生新审计 ID 大于历史最大 ID。实际重启同一应用/数据库路径，完整历史行及所有桥接迁移字段不变。旧封存库/原导出 hash 不变，旧驱动另一个恢复副本仍可用。

负向：未桥接新格式目标在 DAO 及真实 Main 启动时拒绝，原版本记录/历史逐字段不变且 `version_rank` 未被自动移除；旧格式文件/并存文件拒绝、两文件 hash 不变；未知未来版本、坏 TABLE 标记、宽度漂移在 dev 下也拒绝且不清库；其他schema中有数据时不误判空库、不另建空历史库。已有缺表/坏 checksum/失败写入503、连接释放及 NULL/转义回归继续运行。新库的直接 JDBC 大 ID 夹具显式采用同样 LEGACY 连接契约，不删除原 ID 连续性断言；原 V1 的保留由真正旧引擎夹具和桥接应用另行证明。历史桥接夹具的数据库凭据为测试专用SA/空密码；不代表任意真实凭据、加密库或跨版本认证策略已验收。

## 时区及厂商警告不能隐去

历史 TIMESTAMP 不包含时区。一次实际回归在纽约默认时区下读 UTC 导出的墙钟时间，Instant 发生5小时差异；仅临时改 JVM 默认时区仍会受 H2 已缓存默认时区影响。应用验收明确固定旧/新子进程 UTC、应用 JVM UTC，并为验收 JDBC URL 指定 `TIME ZONE=UTC`，避免测试依赖执行顺序。另有 UTC 会话的毫秒 Instant 单元回归。不把这个有限夹具叫做任意时区/任意历史时间精度通过：操作前必须明确源库/应用的原时区，在副本上逐字段核对，再固定一致的目标设置；不能擅自假设用户旧数据为 UTC。

Flyway13.10.3 运行时仍报告其厂商已验证 H2 上限为2.3.232，而所选 H2 为2.5.252。保留原警告，不修改/屏蔽厂商版本检查。Fork 的实际新建、桥接、负向、HTTP 和跨平台回归是有限自验证，不等于厂商认证或任意数据库兼容承诺。

依据：
- https://documentation.red-gate.com/flyway/reference/usage/api-java （2026-10-15，Java17、开源13.10.3及fluent API）
- https://www.h2database.com/html/migration-to-v2.html （原驱动导出、新驱动新目标导入，identity/SQL差异）
- https://github.com/flyway/flyway/blob/flyway-13.10.0/flyway-core/src/main/java/org/flywaydb/core/internal/database/h2/H2Database.java （TABLE标记/版本警告；也核对了实际13.10.3运行 jar 的生成片段）

本地与准确新 head 的最终结果见 CURRENT_VALIDATION.md 及对应 Fork PR，不用中间失败/旧绿灯冒充验收。下一项是 MySQL 现代 Flyway API/module/原 checksum 兼容与旧引擎移除，再继续实际权限/磁盘失败、更广历史夹具和 SBOM/漏洞/许可证。现有 I02 有限边界继续有效，但原 Windows9s:1s 根因仍未复现。没有访问生产库、改写原 V1、发布公共镜像或正式 Release。
