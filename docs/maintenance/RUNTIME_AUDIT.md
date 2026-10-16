# 运行依赖库存与 OSV 查询

更新：2026-10-16。此验收限 Maven 解析的 compile/runtime JAR，不是完整供应链或法律批准。

## 重现

```bash
./mvnw -B -ntp -Paudit verify
./mvnw -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.11.0:list -DincludeScope=runtime -DoutputAbsoluteArtifactFilename=true -DoutputFile=target/audit/resolved-runtime.txt
python3 -m unittest discover -s scripts -p 'test_audit_runtime.py'
python3 scripts/audit_runtime.py
```

`audit` profile 固定 CycloneDX Maven 插件 2.9.3 / schema 1.6，并在插件私有依赖中使用修复XXE的core11.0.1及Jackson2.22.3/annotations2.22，生成 JSON/XML。默认构建不额外依赖 Python 或联网漏洞数据库。CI 独立 runtime-audit job 生成同一组证据；Java 三平台及其他功能 CI 仍完整执行，audit job 的 skipTests 不代替它们。

脚本不读取 config.cfg 或数据库凭据，只发送公开 Maven 包坐标与版本到 `https://api.osv.dev/v1/querybatch`。按每个查询独立跟随分页；缺少响应、错误、分页循环、空 BOM、缺失/重复 JAR 或 SHA-256 不匹配均返回失败。有任意匹配也返回失败，不设忽略项。12 项 Python 回归（含构建图分类/缺失数据/分页与失败处理）验证分页保留及失败处理。

## 证据和当前结果

- 本地真实 profile 构建成功，54 个组件全部与实际解析 JAR 的 SHA-256 一致；OSV 查询零条匹配。静态首批结果见 OSV_RUNTIME_2026-10-16.json，准确 head 结果应以 CI 下载件为准。
- `runtime-bom.json/xml`：组件、版本、purl、依赖关系、哈希、POM 许可证元数据。
- `runtime-inventory.json`：每个实际 JAR 的哈希、许可证及法律文件条目。
- `osv-report.json`：查询 UTC 时间、来源提交/是否脏树、BOM 哈希、逐坐标匹配或错误、法律文件缺口。
- `third-party-notices.zip`：从原 JAR 提取的法律文件，以 group/artifact/version/classifier 分层，避免 fat JAR 合并时同名通知互相覆盖；共 61 条嵌入文件。
- 37 个组件没有可提取的 LICENSE/NOTICE/COPYING；具体 purl 保留在报告。所有组件有 POM 许可证元数据，不能由此推断每项版权通知齐全。

GitHub Actions 保存 runtime-audit-evidence 30 天，按提交关联；需要长期发行证据时应随正式发行归档。此任务没有发布 Release 或镜像。

## 许可证库存结论和缺口

Maven 元数据涉及 Apache-2.0、MIT、BSD-3-Clause、Jetty 的 EPL/Apache、H2 的 MPL/EPL、Jakarta 的 EPL/GPL classpath exception，以及 MySQL Connector/J 的 GPLv2 + Universal FOSS Exception。多项许可证不在此自动改写成 OR/AND 结论，原始字段保留。

项目 POM 现声明与根 LICENSE-2.0.txt 一致的 Apache-2.0。当前通知 ZIP 只是原 JAR 文件保全，尚未补齐缺失法律文本、版权归属、例外与再分发方案；许可证任务仍开放，不可将 runtime-audit job 成功称为法律批准。

官方依据（2026-10-16 核对）：

- [CycloneDX Maven 插件](https://cyclonedx.github.io/cyclonedx-maven-plugin/)及[固定版本 2.9.3](https://repo.maven.apache.org/maven2/org/cyclonedx/cyclonedx-maven-plugin/2.9.3/)。
- [OSV batch API 与分页](https://google.github.io/osv.dev/post-v1-querybatch/)。查询匹配只是数据库当时覆盖结果，不证明组件无漏洞。
- [H2 许可证](https://www.h2database.com/html/license.html)。
- [MySQL 客户端授权与 Universal FOSS Exception](https://www.mysql.com/about/legal/licensing/oem/)；[旧 FOSS Exception 已弃用](https://www.mysql.com/about/legal/licensing/foss-exception/)。不将旧列表套到当前 Connector。

## 范围外继续执行

前端 vendored jQuery 1.10.2 / Bootstrap /字体/图片，npm 开发依赖，Maven 构建插件、隔离旧引擎及容器 OS 不在本运行查询范围。旧容器和旧驱动不应进入生产包。SEC-05 默认凭据、SEC-10 代理信任与剩余迁移/部署矩阵另行跟踪。D01、全部 Issue 修复和正式发行均未标成完成。

构建插件及npm独立审计见 BUILD_TOOL_AUDIT.md；原2.9.1/运行库存首轮零匹配不覆盖插件内部依赖。未执行的site/release等可有报告匹配，不得称为全图零匹配。
