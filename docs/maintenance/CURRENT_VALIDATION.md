# 当前维护验证快照

## 2026-10-16 最新接续

#43初始heada8203c22的本地clean verify于02:15:19 UTC BUILD SUCCESS：32单元+121集成=153项零失败/错误/跳过。最终再补祖先扫描逐段deadline检查并合入最新master7be066b（#42运行库存改动全部保留）；组合head须重新定向/全部CI验收，不能沿用初始head结果。#43只纠正重叠预算计数，不代表D01或原Windows问题完成。

重叠计数纠正后的定向回归于02:10:25 UTC完成BUILD SUCCESS：7单元+9集成=16项零失败/错误/跳过，包括原六项大树及三项新边界。完整clean verify与准确新head六项CI仍分别待验收；不以定向或旧head绿灯替代。

#41已合并为15a281d9571255de01bb1dd7d4fd59837590328a，其准确head2e5fffaf的PR38838235899/push38838222625均六项成功；但独立审查新边界于02:06:21 UTC确定性判红：9998子节点根+其中两个显式孩子，实际9999节点被重复计为10001而误拒绝。现从该最新master独立纠正，保留共享广度优先预检，加入分段祖先合并、原始缺失选择拒绝及顺序/重复/相似前缀回归，见[DELETE_OVERLAP_ACCOUNTING.md](DELETE_OVERLAP_ACCOUNTING.md)。新head完整回归/全部CI须重新验收；旧绿灯不覆盖此问题。下方#41待验为提交时快照。

PR #40 准确 head `c837c55012f8162e6855e0c1adc81d1659fa460e` 的 PR [38836967274](https://github.com/DDDalek92/zkui/actions/runs/38836967274) 与 push [38836958256](https://github.com/DDDalek92/zkui/actions/runs/38836958256) 均六项 CI 成功；三平台各 32 单元 +117 集成，MySQL 日志含旧方言 seed/损坏 checksum 拒绝/桥接保全/现代 Main 重启全部新标记。源码审查后合并为 `d3a45f0a83b31cd95cfbe85cefb60729038e44ab`。

现在独立修复跨根删除的深度优先预检低效路径，见 [LARGE_TREE_VALIDATION.md](LARGE_TREE_VALIDATION.md)。新增确定性回归在旧实现判红；修复后独立本地 clean verify 32 单元+118 集成=150 项零失败/错误/跳过；准确 head `2e5fffaf` 的 PR38838235899 六项 CI 全成功，三平台各150项、Chrome3组、Docker/MySQL/旧ZK标记已逐日志核实。PR #41 已合并为 `15a281d`。历史超时、真实环境缺口及其余 D01/E01/V01/V02 继续保留。


## 当前已验收基础

- Java 17 / Jetty 12.1.14 EE8；JDBC 审计 DAO，H2 2.5.252、Flyway core/mysql 13.10.3、MySQL Connector/J 26.7.0。
- ADMIN 导入授权、REST 角色遮蔽、会话轮换、CSRF、导入大小与 URL 白名单、输出转义、Digest/ACL、LDAP 基础、上下文路径和离线排序已有回归。
- H2 原 1.3/Flyway 2 的独立副本导出/桥接/现代 Main、差量恢复及部分锁/中断/格式漂移拒绝已验收；MySQL 原 V1 和独立 MySQL 方言 V1 的独立副本桥接、TLS、现代 Main/重启已验收。
- 已验收的是合成夹具和明确版本组合；不得原地打开唯一旧库、删除迁移记录或宣称任意历史库普遍兼容。
- 上游 14 PR/46 Issue 已逐项审查分类，见 PR_REVIEW.md / ISSUE_TRIAGE.md。deferred/enhancement/not-reproducible 不等于已修复；不更改上游状态。

## 未完成验收

1. 跨根删除预检修复后的独立完整本地回归及准确 head 全 CI。
2. 运行 SBOM、CVE 查询、第三方许可证与打包通知；构建/测试依赖、前端 vendored 资源、容器底层另列范围。
3. 真正文件系统权限/磁盘空间故障、非 UTC 与时间精度、更广历史方言与备份恢复矩阵。
4. 真实受信 LDAPS/AD、IPv6 双栈、Firefox 和反向代理 HTTPS；已通过的 Chrome、直接 HTTPS、三平台与 Docker 不覆盖这些环境。
5. 剩余增强：逐节点 ACL 编辑、左栏属性、新导入协议、Kerberos；hosts 可选初始化已合并 #31，默认关闭。
6. 原 Windows 9s:1s 性能环境无法获得；正式 Release/公共镜像尚未发布。

## 证据入口

- Java/HTTP/跨平台：JAVA17_VALIDATION.md、SECURITY_VALIDATION.md、OPERATIONS.md。
- 历史迁移：MODERN_H2_RUNTIME.md、MODERN_MYSQL_RUNTIME.md、MYSQL_DIALECT_HISTORY.md、H2_AUDIT_DELTA.md。
- 大树：LARGE_TREE_VALIDATION.md。
- 早期中间状态与失败原始记录保存在本文件提交历史（至 PR #40）；此快照替代重复的“当前入口”，避免将历史待办误读为现状。
