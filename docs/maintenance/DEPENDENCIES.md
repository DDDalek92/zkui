# 当前依赖与审计状态

更新：2026-10-16。旧版本和逐次候选实验见 Git 历史及各迁移专题；本文只列当前状态。

| 组件 | 当前运行版本 | 验证 |
| --- | --- | --- |
| Java / Jetty EE8 | 17 / 12.1.14 | 三平台、真实 JAR、直接 HTTPS、上下文路径；保留 javax Servlet 4 |
| H2 / Flyway | 2.5.252 / core 13.10.3 | 新建、有限旧 V1 副本桥接、Main/重启及拒绝矩阵 |
| MySQL | Connector/J 26.7.0 / Flyway mysql 13.10.3；服务 8.4+ | 原 V1 与独立方言旧格式生成/桥接、VERIFY_IDENTITY TLS、Main/重启 |
| ZooKeeper / Netty | 3.9.6 / 4.1.137.Final | 当前隔离服务及 owned 3.5.7 兼容检查 |
| FreeMarker / Commons IO / FileUpload | 2.3.35 / 2.22.0 / 1.6.0 | 模板、编码、输出、上传边界回归 |
| SLF4J / reload4j | 2.0.20 / 1.2.27 | 单一日志 provider；无运行 Log4j 1 |
| json-simple | 1.1 | 仍为旧库；运行查询无匹配不意味着维护活跃 |

旧 H2 1.3.174、Flyway 2.2.1/4.2.0、Connector 8.0.16 仅在 migration-drivers 下由测试子进程隔离使用，不进入应用运行依赖图。旧 ZooKeeper/MySQL 容器仅用于隔离兼容夹具。

## 可重复的运行审计

见 [RUNTIME_AUDIT.md](RUNTIME_AUDIT.md)。`audit` Maven profile 固定 CycloneDX 2.9.3/补丁core11.0.1，输出 runtime-bom.json/xml；脚本核对实际解析 JAR 集合与 SBOM 的 SHA-256，提取带组件命名空间的 LICENSE/NOTICE/COPYING，查询 OSV 全部分页。网络错误、响应缺失、重复分页或清单/哈希不一致均失败；不添加漏洞忽略项。

本地实测 54 个 runtime 组件，OSV 零条匹配，61 条嵌入法律文件，37 个组件无可提取的法律文件。所有组件均有 POM 许可证元数据；不能将此称为许可证兼容或再分发验收通过。新 profile 本地执行成功；准确 head CI 仍须独立核验。

## 仍需完成

- 补齐缺失法律文件与版权归属、核对 MySQL Universal FOSS Exception / H2 等具体分发要求，并确定发行包如何随附通知。
- 前端当前增量移除jQuery/Bootstrap JS，见LIVE_BROWSER_VALIDATION.md；CSS/字体/图片、构建插件、测试历史引擎、npm 开发依赖、容器基础 OS 另行审计；它们不在本 runtime SBOM 查询范围。
- SEC-05 默认样例凭据和 SEC-10 转发地址信任已在独立增量实现，验收见 PROXY_STARTUP_SECURITY.md；不由运行扫描通过代替。
- 非 UTC/精度、更广历史库、真实权限/磁盘失败和部署矩阵继续按 TASKS.md 执行。

不使用扫描无匹配作为正式 Release 或公共镜像自动发布条件。

构建工具后续：Maven3.9.16及12个插件固定；实际CI插件109个包版本查询零匹配，未运行插件仍14个坐标有匹配，均逐项保留；npm开发lock图零匹配，见BUILD_TOOL_AUDIT.md。新工具实际组合与全部CI须通过对应PR准入，不能将可选工具匹配说成已解决。
