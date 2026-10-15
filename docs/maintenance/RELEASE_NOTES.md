# 维护候选说明（2026-10-15）

这是可审查的维护预览记录，应用仍为2.0-SNAPSHOT；未创建正式Release、发布公共镜像或自动迁移用户数据。依赖迁移和安全发行验收尚未完成，不能当作已清空第三方漏洞的新版发行。

## 已完成并有回归的行为

- 默认REST要求登录、普通用户密码字段遮蔽；显式匿名兼容只按只读角色，只有已登录ADMIN和显式设置才允许REST明文密码字段。
- 登录轮换Session/CSRF，Cookie HttpOnly、仅Cookie追踪；退出及所有写入口需要POST/CSRF，导入需要ADMIN。
- 上传/远程导入具备大小、parts、UTF-8、路径及完整URL白名单/协议/重定向/超时限制；上传头512字节。导入仍不是事务。
- 输出HTML转义；新增历史和DEBUG日志不保存配置值，历史仅ADMIN；旧历史没有被清理。
- ZooKeeper完整ensemble/chroot、连接就绪有界等待、连接复用/释放和监控失败隔离；Digest客户端身份与新节点ACL分离。
- LDAP可配置/转义DN，兼容域/UPN、空密码拒绝、超时、角色优先级、失败重登录清身份。
- UTF-8表单/节点/导入/导出，保留值空白；有子节点的节点自身值可见且按角色遮蔽。
- zkRootPath限制全部HTTP数据入口，搜索当前子树，批次路径预检；baseURL保留代理子路径中的跳转、表单、资源和Cookie。
- 本地稳定文本排序保留选择和编辑事件，键盘/aria-sort支持，不增加CDN。
- 有界历史条数、缺失配置默认值、数据库异常连接关闭；日志替换为reload4j、上传组件升级，依赖清单与后续迁移队列明确。
- 本地多阶段容器构建、固定Temurin摘要、非root运行、历史卷及直接环境覆盖；make默认只构建。
- Java17编译/运行/CI/容器与Jetty12 EE8整体迁移；历史记录改用参数化JDBC，移除旧ORM和插桩。具体新基线验证见JAVA17_VALIDATION.md；H2/Flyway/V1没有升级或改写。

## 升级现有调用者时的变化

1. 匿名REST调用需按README登录并保存轮换后的Cookie；写入脚本必须取得当前Session CSRF token。GET logout不再有效。
2. 新增/导入的路径必须符合配置根范围；REST hosts映射和继承不再越过根。根需由运维事先创建，启动不自动补写业务配置。
3. 自定义baseURL时，代理必须保留同一前缀；旧的根路径绝对链接不再适用。
4. 上传限制可能拒绝旧的大文件/长文件名头；远程导入需明确开启并列出完整可信URL。
5. 环境变量以docker/Docker.md为准，旧第三方镜像变量不兼容。持久化历史卷和只读配置的权限需匹配容器UID10001。
6. 历史默认50条，historyLimit为1–1000；非法配置启动失败。只有显式env=dev会使用旧清库模式，不要对保留数据使用它。
7. 新构建要求JDK17，不再支持Java8或IDE ActiveJDBC插桩流程；仅css/js/images/fonts资产直接公开，模板/WEB-INF/META-INF不由静态处理器公开。

## 验证和放行状态

[运行38698340364](https://github.com/DDDalek92/zkui/actions/runs/38698340364)：Linux/Windows/macOS各92项Java通过，Chrome3组通过，Docker构建/非root/登录/资源通过。具体版本、失败修正和限制见CURRENT_VALIDATION.md。

Java17新基线与上述Java8历史结果分开，验收见JAVA17_VALIDATION.md。正式发行仍阻塞于DEPENDENCIES.md中的历史H2/Flyway/MySQL迁移/恢复及其余老组件审计；还缺真实AD/LDAPS、双栈、nginx/部署TLS、Firefox真实登录和超大树验证。旧文本导出不是无损二进制备份，父节点数据导出/超大树工作量边界仍有缺口。46个Issue的文档/增强/延期/无法复现分类并不等于全部故障修复。

继续执行TASKS.md中的未完成项；新功能需求按增强队列处理，不以关闭上游Issue充当完成证据。
