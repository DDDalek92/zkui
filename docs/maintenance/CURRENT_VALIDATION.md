# 当前维护验证快照

更新：2026-10-16。Fork DDDalek92/zkui，主分支 master。以下是当前事实；中间失败和过时的待CI快照保存在专题文档及Git历史，不能当作新结论。

## 最近已合并

| PR | 内容 | 准确head / PR CI | 结果 |
| --- | --- | --- | --- |
| #40 | 旧MySQL独立方言V1桥接/Main | c837c550 /38836967274 | 6job、三平台149项；旧库损坏/保全/重启标记 |
| #41 | 跨根广度优先删除预检 | 2e5fffaf /38838235899 | 6job、三平台150项；旧实现确定性判红 |
| #42 | SBOM/哈希/OSV/法律文件库存 | 52f61f1 /38838883132 | 7job、54运行组件/零匹配/6项扫描失败处理测试 |
| #43 | 重叠选择并集预算及缺失后代拒绝 | b9e464e /38839636106 | 7job、三平台153项；9999并集/顺序/重复/前缀边界 |
| #44 | 示例账号默认拒绝/可信TCP代理 | 696552bd /38839670737 | 7job、三平台158项；cookie/审计IP/REST实际边界 |
| #45 | POSIX/ENOSPC/同路径恢复 | 2746b294 /38853032997 | 8job、三平台161项；实际权限、空间耗尽、503/ZK状态、重启历史保全 |
| #46 | 去除旧前端JS/真实ChromeFirefox | 0cddf648 /38853654000 | 9job、三平台162项、Chrome静态4组、实际双浏览器HTTPS全流程 |
| #47 | 实际IPv4/IPv6回环与HTTP/REST | 51364d75 /38854274944 | 9job、三平台164项及双栈标记；独立本地164项也通过 |
| #48 | Maven/插件修补和构建/npm审计 | 048177d4 /38856229313 | 9job、三平台164项；运行54/CI构建109坐标零匹配、可选工具14匹配保留；12项Python |

#48已合并为b8b24db（其树与受测head完全相同）；#47已合并为252f794。主分支组合持续核对；CI有效结果不等于所有原用户环境复现。#44最终本地运行因中断缺日志/XML，未计为成功；本地#46非clean产物含旧资源被正确拒绝，clean定向8项通过，均未伪称完整浏览器验收。

## 当前接续

构建审计#48已通过并合并。当前D01法律文档识别修正见LEGAL_INVENTORY_CORRECTION.md：旧规则误将license包中的类当作通知，61条应为44条；54组件及37个缺嵌入法律文件的组件数不变。新增4项回归后16项Python通过。补齐分发通知与其它未完成范围仍独立开放。

## 维护基础已具备的能力

- Java17/Jetty12 EE8/JDBC；H2 2.5.252、Flyway core/mysql13.10.3、Connector/J26.7.0，旧引擎只在测试子进程。
- 授权、REST遮蔽、会话轮换、CSRF、导入URL/上传边界、输出保护、Digest/LDAP基础、UTF-8、上下文、根路径和离线排序。
- H2旧V1副本导出/桥接/现代Main、差量恢复及部分锁/中断/结构/格式拒绝；MySQL原V1和独立方言V1的副本桥接/TLS/Main/重启。
- 有界大树浏览/遍历和删除预检；真实POSIX/tmpfs故障；Chrome/Firefox实际HTTPS登录/写入/角色/退出。
- 14上游PR/46Issue逐项审查分类；延期、增强、无法复现与已修复明确区分，不更改上游状态。

## 仍未完成

1. 许可证再分发：37运行组件缺嵌入法律文件，字体/图片等历史资源来源待核；实际CI构建插件/npm已有独立查询，可选工具14坐标匹配、测试旧引擎和容器OS仍需处置。运行OSV零匹配不覆盖这些范围。
2. 非UTC/时间精度、更广历史方言及原用户库兼容；MySQL故障矩阵、Windows ACL和物理介质故障不由H2/tmpfs代替。唯一旧库不可原地试迁移。
3. 真实AD/受信LDAPS、反向代理TLS、生产IPv6网络和原Windows9s:1s环境；已有合成/回环结果不冒充这些证据。
4. 增强：逐节点ACL编辑、左栏属性、新导入协议、Kerberos/每用户ZK身份与描述功能；hosts显式可选初始化已完成并默认关闭。
5. 正式发行：上述门槛未满足，未发布Release或公共镜像。当前是可验证的维护基线，不称全部问题修复。

## 证据入口

TASKS.md；SECURITY_VALIDATION.md；PROXY_STARTUP_SECURITY.md；STORAGE_FAILURE_MATRIX.md；LIVE_BROWSER_VALIDATION.md；IP_STACK_VALIDATION.md；RUNTIME_AUDIT.md；MODERN_H2_RUNTIME.md；MODERN_MYSQL_RUNTIME.md；MYSQL_DIALECT_HISTORY.md；H2_AUDIT_DELTA.md。
