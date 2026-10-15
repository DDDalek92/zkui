# 上游开放 PR 审核台账（初稿）

> 2026-10-15；基线 `1b3e7c1`。上游：[DeemOpen/zkui/pulls](https://github.com/DeemOpen/zkui/pulls)。共 14 个开放 PR。分类是**静态预审建议**，不等于通过测试、批准合并或全部逐行审核。每项整合前须检查完整差异、讨论、许可证、构建与回归测试。任何上游 PR 均不能直接合并到本 Fork 的 `master`。

| PR | 标题 / 目标 | 初步决策 | 关键检查和依赖 |
| --- | --- | --- | --- |
| [#87](https://github.com/DeemOpen/zkui/pull/87) | 修正 defaultAcl 示例注释 | 适合早期独立整合 | 仅文档性质；检查 JSON 示例有效性，关联 Issue #99 |
| [#109](https://github.com/DeemOpen/zkui/pull/109) | 保留多行节点文本空格和换行 | 优先测试后整合 | HTML 转义、长文本换行、搜索/导出行为、#43/#62 |
| [#54](https://github.com/DeemOpen/zkui/pull/54) | 配置根路径、子树搜索 | 修改后整合候选 | 根路径安全边界、搜索性能、空路径与路径遍历 |
| [#110](https://github.com/DeemOpen/zkui/pull/110) | 表格排序 | 修改后整合候选 | 外部 CDN 供应链和离线部署、前端版本适配 |
| [#88](https://github.com/DeemOpen/zkui/pull/88) | 修复中文乱码 | 修改后整合候选 | 确保 UTF-8 端到端一致；新增日志可能泄露原始节点值，需删除/脱敏 |
| [#86](https://github.com/DeemOpen/zkui/pull/86) | 多 ZooKeeper 地址与监控 | 需重测/可能重写 | 客户端 ensemble 连接语义、故障转移、配置无效与 #64/#98 |
| [#50](https://github.com/DeemOpen/zkui/pull/50) | ZooKeeper 客户端认证 | 需安全审计后整合或重写 | `addAuthInfo`、Digest 凭据格式、ACL/用户隔离；关联 #42/#59/#80/#99/#112 |
| [#107](https://github.com/DeemOpen/zkui/pull/107) | LDAP 登录调整 | 需安全审计后整合或重写 | 域/OU、空口令、TLS、超时、角色映射、回归 #28/#52/#106 |
| [#41](https://github.com/DeemOpen/zkui/pull/41) | 输入修剪及其他功能 | 拆分后评估 | 涉及 15 文件、数据库迁移/描述能力，不能视为单一 trim 补丁 |
| [#105](https://github.com/DeemOpen/zkui/pull/105) | Jetty/日志/MySQL 依赖升级 | 吸收升级意图，重新制定版本 | 2022 年版本已过时；需完整依赖树与兼容性测试 |
| [#103](https://github.com/DeemOpen/zkui/pull/103) | MySQL Connector/J 版本更新 | 归入统一依赖迁移 | 驱动坐标和 JDBC URL、ActiveJDBC 兼容 |
| [#102](https://github.com/DeemOpen/zkui/pull/102) | H2 1.3 到 2.1 | 独立数据库迁移任务 | 备份/恢复、Flyway、SQL、历史数据不可直接无验证升级 |
| [#104](https://github.com/DeemOpen/zkui/pull/104) | Jetty Server 9 到 10 | 归入 Jetty 迁移设计 | 对齐所有 Jetty 模块、Servlet API 和运行环境 |
| [#93](https://github.com/DeemOpen/zkui/pull/93) | Jetty 9 到 11 | 不能按原样直接合并 | 现有 `javax.servlet` 与 Jetty 11 `jakarta.servlet` 不兼容；需整体迁移 |

## 已关闭历史 PR 的复查

上游搜索另外显示 27 个已关闭 PR（**包括合并与未合并的 PR**），不能将它们全部当成待合并。复查优先对象：[#97](https://github.com/DeemOpen/zkui/pull/97) JSON 验证、[#83](https://github.com/DeemOpen/zkui/pull/83) 字符串值、[#73](https://github.com/DeemOpen/zkui/pull/73) JS 安全、[#78](https://github.com/DeemOpen/zkui/pull/78) Docker、[#27](https://github.com/DeemOpen/zkui/pull/27) 与 [#16](https://github.com/DeemOpen/zkui/pull/16) 涉及相互撤销的认证修改。须查询 `merged` 状态、上游当前代码是否已经包含效果，禁止重复应用已合并内容。

## 整合流程

1. 给每个 PR 写清原链接、作者、提交/差异、业务意图、关联 Issue、风险和方案。
2. 建立测试用例（失败的复现用例先行），在独立分支 cherry-pick 部分提交或重做修复。
3. 运行 CI + 集成验证，尤其关注认证、数据库和导入导出。
4. 为本 Fork 新建独立 PR，标明 `Upstream PR: URL` 并保留原作者贡献和来源。
5. 本人审核后合并；外部上游 PR 不会因此自动关闭。对“采纳意图、重写实现”的情况保留说明。

## 审核清单字段

最终每项应补齐：`代码差异已审`、`讨论已审`、`测试编号`、`是否重现`、`合入 Fork 的 PR URL`、`处理状态`。当前上述字段统一 **待完成**。
