# 嵌入法律文件库存校正

2026-10-16。在#48合并后继续D01时，直接检查通知ZIP发现原扫描规则匹配整个路径，把Flyway的`org/flywaydb/core/internal/license/*.class`和MySQL的`Notice.class`当成法律文本。原61条中17条为误计代码，不是61份许可证或通知。历史JSON保留原样，本文件及LEGAL_INVENTORY_2026-10-16.json明确纠正，不能用旧数字支持分发结论。

`audit_runtime.py`改按文件basename识别LICENSE/NOTICE/COPYING及常用后缀，排除代码/二进制文件扩展名；仍保留嵌套`META-INF/license/LICENSE.boringssl.txt`。同一JAR的重复法律路径直接失败，避免归档出现歧义；原有不安全相对路径拒绝保留。各组件仍使用独立命名空间，原文件字节、换行和编码不重写。

新增4项回归：法律文本与license包代码区分；只有代码的JAR仍显示缺法律文本；越界/重复路径拒绝；两个组件同名文档各自保留原字节。前三项在旧实现产生3个失败，修正后全部16项审计Python测试通过。

使用#48真实54组件BOM和解析JAR重新执行完整运行库存/OSV查询成功，得到44条法律文档、37组件缺嵌入文档；组件/JAR哈希边界及OSV零匹配不变。已检查新ZIP没有`.class`且路径无重复。静态校正快照记录实际本地基础提交b8b24db和dirty=true；最终准确head证据由PR的runtime-audit CI生成并归档，不把该快照冒充干净提交的CI报告。

这里只修正证据的准确性。37组件缺失文本、版权归属、字体/图片来源及分发通知仍需逐项补齐；POM元数据或OSV零匹配不等于许可证再分发批准。原Java应用、依赖版本、V1及数据库操作均未修改。

任务入口TASKS.md与CURRENT_VALIDATION.md同步#48已经九项通过/合并及#31/#43–#47已完成范围；ISSUE_TRIAGE.md保留历史分类数量，同时纠正“Firefox未验”“hosts未实现”等过期证据，不改变上游状态。
