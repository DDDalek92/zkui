# H2 旧库隔离迁移预演（不是应用驱动升级）

> 后续增量：Flyway桥接见[FLYWAY_BRIDGE_REHEARSAL.md](FLYWAY_BRIDGE_REHEARSAL.md)；固定V1的语义结构/PK/索引/可空性/自增与约束执行、DDL漂移拒绝见[H2_SCHEMA_VALIDATION.md](H2_SCHEMA_VALIDATION.md)。下文100项及待办是首批预演历史，完整应用/驱动切换和故障/差量恢复仍未完成。

2026-10-15；基线Fork #21，`f1dee98e642f2d1a8dbd61585886b5e1fcdaaf0b`。
这是D01-C的第一组可重复实验，不是生产迁移工具，也不是全部迁移矩阵通过。
运行时仍H2 1.3.174/Flyway2.2.1，V1未改。上游#102仍deferred。

## 已复现的具体兼容边界

[H2官方当前版本](https://www.h2database.com/html/download.html)为2.5.252（2026-09-23）。
按[官方迁移路线](https://www.h2database.com/html/migration-to-v2.html)，
用1.3.174导出SQL，2.5.252仅创建新路径库后RUNSCRIPT FROM_1X：

- **普通模式失败**：导出DDL中的 `CHANGE_DATE DATETIME` 被拒绝，错误50004。
  FROM_1X本身并不让这个具体旧库在普通模式兼容。
- 同一份未改写导出，在新路径的 **MODE=LEGACY** 下，有限合成夹具导入/逻辑对比/主键序列/重开通过。
  这只是候选兼容路径，不表示应用新建库、旧Flyway或所有旧库都兼容；不自动将应用URL改成LEGACY。

测试中普通模式的预期失败有明确标记；CI绿只表示“正确识别阻塞且未伤及封存库”，
绝不能写成普通模式迁移成功。不会改V1、修改导出DDL或删除/repair Flyway校验记录。

## 已实现隔离回归

`./mvnw clean verify`会在pre-integration-test用dependency-plugin3.8.1单独复制候选H2 JAR。
它不在dependencies/runtime图中，不进入组装JAR，也不参与Docker的package阶段。
打包回归明确断言运行包H2仍是1.3.174。

| 阶段 | 实际保护和断言 |
| --- | --- |
| 生成 | 每个用例自行创建临时目录/随机所有权标记；旧应用JAR中的1.3.174及Flyway2.2.1按原V1建库；只含合成数据/空测试密码 |
| 封存 | 旧连接关闭后复制到sealed-source；拒绝锁文件/符号链接/非普通文件；保存完整文件名与SHA256清单 |
| 导出 | 只旧驱动打开old-work；UTF-8 SCRIPT；保留原始导出，不在新驱动进程打开旧路径 |
| 驱动隔离 | 子进程classpath分别为test-classes+旧应用JAR或test-classes+候选H2；断言仅一个org/h2/Driver.class，并核对实际驱动版本 |
| 普通失败 | 精确识别DATETIME/50004；没有切换配置，失败目标不作为可用库；封存文件清单/SHA256完全不变 |
| LEGACY候选 | histories每字段与原Flyway表所有字段快照精确比较；NULL/空串、日期、中文/emoji/换行/空白/引号/反斜杠、500字符及非连续大于2^53的ID；新ID继续、关闭重开后保留 |
| 重复执行 | 已存在目标目录直接拒绝；目标库文件SHA256前后不变，未覆盖或重复插入 |
| 截断导入 | 另存截断SQL并在独立新目标的LEGACY模式下识别语法失败；原始导出字节/封存SHA256不变，旧副本仍可恢复；半成品未作为可用库 |
| 原版本恢复 | 从sealed-source另复制rollback-work，仅旧驱动打开；先比较原快照，再确认旧版本新增记录/生成ID仍可用；封存清单/SHA256不变 |

最后的“恢复”只证明原快照可恢复，**不包含把候选库新增记录无损带回旧库**。
测试刻意使用不同候选/回退新增标记；切换后有新业务写入时需另行保全差量，不能把改回URL称作无损降级。
测试辅助类只在src/test中，不提供接受任意用户JDBC URL的生产迁移入口。

## 验证证据与剩余任务

本地OpenJDK17.0.20/Linux完成定向预演及完整clean verify：16单元+84集成=100项，0失败/错误/跳过。
对应Fork PR准确head的三平台CI仍须单独核对，未全绿不得合并。
远程完整结果以PR的Checks/说明为准，不能用Linux通过代替Windows/macOS，也不能把预期失败算成应用支持新驱动。

仍待D01-C后续：新版Flyway选择及受支持元数据升级路径、新应用在新库的完整初始化/读写/就绪行为、
约束/索引对照、更广历史夹具、进程中断/不可写/锁竞争等失败注入、切换后差量保全与完整恢复。
实际MySQL新建/旧库与#63方言仍独立待做。没有接触用户数据库、生产服务或部署卷，没有发布镜像/Release。

当前Java17基线证据：Fork #21 head `37369d1127c9729ced003169a4ba27f894823adf`，
[运行38716523453](https://github.com/DDDalek92/zkui/actions/runs/38716523453)四job成功；
Linux/Windows/macOS各97项Java，Chrome3组与Java17非root容器冒烟通过。
本次增加的3项预演在对应新PR中重新完整执行；旧97项结果不代替新head。
