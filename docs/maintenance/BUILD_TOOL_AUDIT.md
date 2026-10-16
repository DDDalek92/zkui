# 构建工具与 npm 依赖审计

2026-10-16。范围独立于54组件应用运行图，不把“运行零匹配”当成构建/开发工具均安全。

## 发现和修复

旧插件解析图有232个唯一包版本，30个坐标匹配OSV公告；其中包含插件内部Jackson、CycloneDX core、Plexus、Commons等，也包含未执行的site/release工具。不是30个已证明可利用的应用漏洞。

Maven Wrapper现固定3.9.16，来自官方Maven Central ZIP；先核对发布SHA512，再将实际ZIP SHA256 `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce` 写入Wrapper。未关闭下载校验。POM中12个插件显式固定当前已核对的稳定3.x/2.x版本（继承的release/antrun另行库存），不使用Maven4或alpha/beta目标：

| 插件 | 版本 |
| --- | --- |
| clean / resources | 3.5.0 /3.5.0 |
| jar / compiler | 3.5.1 /3.16.0 |
| surefire / failsafe | 3.6.0 /3.6.0 |
| assembly / dependency | 3.8.0 /3.11.0 |
| site / install / deploy | 3.22.0 /3.2.0 /3.2.0 |
| CycloneDX | 2.9.3 |

仅升级插件仍留下漏洞依赖，因此在插件私有图中固定补丁：resources的Plexus3.6.2；clean/assembly的Plexus4.0.3；assembly的aircompressor2.0.3；dependency的BeanUtils1.11.0；CycloneDX core11.0.1（XXE修复）和Jackson core/databind2.22.3、annotations2.22。应用运行依赖/V1未改。插件2.9.3仍默认core9.0.5，不能因工具版本较新就当作已修复。

中间一次错误候选aircompressor0.28被Central解析拒绝；随后依据公告固定2.0.3，没有忽略解析错误。升级core11/Jackson后真实XML/JSON BOM生成已成功，仍有54运行组件；不能以编译代替新工具组合完整回归。

## 自动边界

CI保存完整resolve-plugins坐标/实际JAR SHA256、OSV逐包结果和npm audit结果。scripts/audit_build.py固定当前CI的9个clean/package/verify相关插件，缺失根、未知插件根、格式/文件/哈希/网络/查询覆盖错误以及任意实际CI插件匹配均失败。共享依赖只要也由CI插件使用就不能归到可选组；没有忽略公告ID。

site/install/deploy/release/antrun在这些CI目标中没有执行，其匹配仍逐包保留并计数；当前14个此类坐标有匹配。报告状态为unexecuted_plugin_findings，不是全图no_matches，不是可达性/VEX批准，也不保证手动运行这些目标安全。未来CI执行目标变化必须同步调整分类；脚本核对POM执行声明与直接Maven命令，发现可选插件execution/goal、无法安全归类的多行命令或未知插件均拒绝，不能默认为安全。

12项Python回归覆盖OSV分页/错误和库存覆盖、分类、共享依赖、未知插件、聚合POM元数据。聚合POM没有可执行JAR，不冒充二进制依赖。npm检查明确include=dev，当前lock图零匹配；其版本与应用静态资源的归属不同。

## 当前证据和未完成项

本地修订图：实际CI插件涉及的匹配为0；未执行插件仍14个坐标有匹配；npm零匹配，静态首批结果见 BUILD_TOOL_AUDIT_2026-10-16.json，注明脏工作树，准确head证据以CI artifacts为准。Maven插件图和运行图各自保留，不能将可选插件匹配描述为生产运行包漏洞，也不能隐瞒其存在。

新版工具下完整本地和三平台/九项CI仍须验收；许可证/历史二进制归属、可选工具匹配、容器OS、旧隔离引擎、更多迁移/原环境与增强任务仍开放。未发布Release/镜像。

## 官方来源

- [Apache Maven版本元数据](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml)与3.9.16同目录ZIP/SHA512。
- [Apache Maven插件元数据](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/)及[SBOM插件](https://repo.maven.apache.org/maven2/org/cyclonedx/cyclonedx-maven-plugin/)。版本与最低Maven要求由实际POM核对，运行JDK17。
- [CycloneDX core XXE公告](https://github.com/CycloneDX/cyclonedx-core-java/security/advisories/GHSA-6fhj-vr9j-g45r)。
- [Plexus修复来源](https://github.com/codehaus-plexus/plexus-utils/issues/294)；其它精确公告ID保留在JSON/CI报告，可经OSV公开API复核。

## 最终本地候选验证

Maven3.9.16、新插件/内部补丁组合的独立 `-Paudit clean verify` 于2026-10-16 13:28:42 +08:00 BUILD SUCCESS：36单元+128集成=164项零失败/错误/跳过，并实际生成54组件XML/JSON BOM。随后原样执行CI的运行库存、构建插件库存/查询、npm检查均成功：运行54零匹配、CI构建109零匹配、可选未运行工具14坐标匹配保留，12项Python测试通过。新head三平台/浏览器/存储/MySQL/旧ZK全部Checks仍是合并门槛，本地成功不能替代。
