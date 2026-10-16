# 真实文件系统故障验收

2026-10-16。此增量只增加隔离 CI 夹具，不改运行数据库策略、驱动或已应用迁移。准确新 head CI 未通过前，下面是验收要求，不能称为已验收结果。

## 范围和隔离

固定 owned CI Docker 镜像 `zkui:storage-ci`、非 root UID/GID 10001、独立网络/旧 ZK 合成服务。必须同时有 ZKUI_OWNED_STORAGE_FIXTURE=true 与 RUNNER_TEMP；不接受用户数据库、磁盘设备或镜像参数。新建 RUNNER_TEMP 私有目录，仅在其子目录挂载 8/16 MiB tmpfs。测试结束先停止自建容器，再卸载自建挂载点，最后清理这一个新目录。没有格式化设备或填满宿主根分区。

本地没有 Docker，实际挂载/服务故障只能由准确 head 的 storage-faults CI 执行。源码可编译，未授权调用在任何 Docker/挂载操作前拒绝，历史 HTML 快照解析检查通过；这些不代替动态故障验证。

## 必须通过的矩阵

1. **真实权限**：10001 用户面对 0555 的新库目录，数据库初始化失败、进程非零退出；封存 sentinel 哈希和目录内容保持不变。不是 JDBC ACCESS_MODE_DATA=r。
2. **启动时 ENOSPC**：独立 8 MiB tmpfs 由受测非 root 用户写满，必须观察实际 `No space left on device`；新库初始化拒绝并非零退出。写入最多 32 MiB，挂载容量有界。
3. **运行中 ENOSPC**：先在独立 16 MiB tmpfs 成功创建一条审计和 ZK 节点，保存全部历史表格字段；同步写入模式 WRITE_DELAY=0，随后写满文件系统。后续真实 HTTP 节点修改必须返回 503/inspect state before retrying，实际 ZK 变更存在但不出现成功 flash；节点值不得泄露日志。
4. **同一路径恢复**：清理填充文件、重启同一应用容器，宿主临时文件系统持续挂载；历史逐字段快照必须等于故障前基线。不能因 tmpfs 丢失而重建空库冒充恢复。

CI 日志必须包含三个 STORAGE_*_OK 标记，报告保存 storage-failure-evidence（30天）。故障及恢复日志、前后历史快照均为合成数据。任何断言不满足都要保留为真实失败并继续定位，不能放宽503/持久化断言或假称通过。

## 边界

此项是 Linux/POSIX/tmpfs 的真实权限和空间耗尽，不代表 Windows ACL、物理硬盘断电、块设备坏页或任意用户历史库普遍恢复。现有旧库导出/桥接/差量演练分别保留范围；MySQL权限、只读服务端和网络中断也不由 H2/tmpfs 结果代替。原Windows9s:1s、真实AD/LDAPS、许可证缺口和未实现增强仍在 TASKS.md。

## 最终准确 head 结果

PR #45 head2746b294916c25ce322da09f95f80c2d02b1120f 的PR38853032997八项CI全成功，三平台36单元+125集成=161项、Chrome3组及MySQL/旧ZK/运行审计通过。storage-faults三个必需标记全部在日志中出现，包含真实权限、实际ENOSPC和同一宿主挂载上清理/重启后的历史保全。逐日志审查后已合并为0d51bb1。没有本地Docker验证的冒称；上文待验收是提交时记录。物理介质/Windows/MySQL故障及更广历史范围仍开放。
