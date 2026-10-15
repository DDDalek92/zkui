# 已审问题的构建、连接和部署排查

当前入口为TASKS.md；下列说明是处置依据，不表示复现了每个历史环境。

## 构建、JDK与数据库

- #51：使用完整JDK8，JAVA_HOME指向JDK根，PATH能找到javac；用./mvnw --version核对实际工具链。只装JRE不能运行编译器。
- #76：History继承ActiveJDBC Model，构建需要process-classes插桩。用./mvnw clean verify生成并运行带依赖JAR，避免从未插桩IDE目录直接启动。真实JAR历史持久化已测；原MySQL/IDE环境未复验，JDK17兼容仍见DEPENDENCIES.md。
- #60/#66/#72：进程启动不等于Servlet注册正常。记录JDK、Jetty、打包方式、/login状态和启动日志；历史Jetty升级经验不代表所有组合已验。当前JDK8基线三平台已通过，JDK9/10不是新维护目标。
- #95：初始化SQL位于src/main/resources/db/migration/V1__Core.sql，由Flyway启动迁移。env=dev会清库，只用于可丢弃数据；缺env默认生产行为。
- #63：MySQL5.7不接受原id bigint default null auto_increment primary key，而MariaDB行为不同。不要删除schema_version或改写已应用V1；须单独提供空库/已有库的方言迁移、备份和恢复验证。H2通过不能替代MySQL。
- H2跨大版本遵循原驱动导出、新库导入、核对和回滚路线，不把新驱动指向唯一旧库，不自动迁移用户数据。

## ZooKeeper连接和性能

- #108：原生ensemble为逗号分隔地址，IPv6用[::1]:2181，可带chroot；无需leader放首位。解析及chroot已测，真实IPv6网络仍待验证。
- #33/#77/#98/#85：ConnectionLoss或Windows延迟不能独立确定根因。核对实际应用网络中的DNS/全部地址/端口、容器localhost、chroot、服务端健康和会话日志、ACL身份、超时及jute报文限制。#9已修复有界等待与复用，不采用无界sleep或盲目降级ZooKeeper。
- #92：Web登录与ZK授权是两层；/mpush NoAuth需核对实际ACL及客户端身份，ADMIN不能绕过后端权限。Digest设置见README，不能将Kerberos等其他方案当成Digest套用。
- #56：核对实际config.cfg/serverPort及JVM；Linux用ss -ltnp、macOS用lsof -nP -iTCP:9090 -sTCP:LISTEN、Windows用Get-NetTCPConnection -LocalPort 9090检查占用。确认自己的服务后选端口，不自动杀其他进程。
- #57：需要可复制堆栈、配置项名和JDK/版本；端口应为有效整数，超时单位看配置注释。仅原帖图片不足以认定具体非法配置。
- #6：312763子节点需同规模/名称长度/服务端配置复现；getChildren仍受报文上限约束，分页HTML不能消除服务端大报文。遍历/渲染尚非流式，缺完整工作量上限，后续须独立性能和资源边界实现。

## hosts、节点模型与导入

#61/#53：默认根/的Hosts及REST映射位于/appconfig/hosts；自定义根默认<root>/hosts，或显式zkHostsPath。应用不自动创建/覆盖这些业务元数据（#49）。确认权限后，仅对全新示例命名空间可在ZooKeeper CLI显式创建：

```text
create /appconfig ""
create /appconfig/hosts ""
create /appconfig/demo ""
create /appconfig/hosts/test-host "/appconfig/demo"
create /appconfig/demo/example "value"
```

已有节点不必重建。按实际chroot、根和ACL调整，不盲目覆盖生产路径。REST缺映射返回404；UI缺节点仍显示后端错误，文档初始化不是透明自动修复。

#79：节点可同时有数据和子节点；UI按有子节点判“目录”，不按value空/null判类型。旧Add Node创建foo=bar占位子节点。#12已展示父节点值，没有删除旧占位；#29左栏属性仍是增强。

#55：现有POST /import支持脚本multipart调用，仍需先按README登录、更新Cookie及CSRF token：

```sh
curl -b cookies.txt -H "X-CSRF-Token: $CSRF_TOKEN" \
  -F 'scmOverwrite=false' -F 'file=@config.txt' "$BASE/import"
```

ADMIN/CSRF/UTF-8/大小/整批路径校验同UI，成功302。JSON/纯文本新协议和事务性导入未实现；旧文本导出也不是二进制或字面反斜杠n的无损备份。

## 登录、代理、容器和功能边界

- #90/#111/#70/#71：本地镜像、变量/凭据/卷及LF脚本见docker/Docker.md；其他镜像的ZKUI_ADMIN_PW不属于本项目，不保证公共zkui:latest存在。
- #14/#100：baseURL与代理保留同一前缀，链接/表单/资源/cookie及nginx示例见README；真实nginx/TLS未运行。
- #94：原Ubuntu18.04.5、具体Firefox/Chrome版本、Cookie/网络/控制台证据缺失；当前Chrome检查不等于Firefox真实登录已验。
- #52/#106：LDAP身份、精确角色、DN模板、超时、LDAPS信任见README。隔离LDAP已测，原AD/证书链未访问，#106无正文环境。
- #74：保留UI登录和ADMIN/CSRF；匿名只读REST兼容不提供匿名UI写入，不采纳删除过滤器。
- #18：zkRootPath可限制专用命名空间，配合ACL保护HA；Kerberos/KDC/JAAS/SASL及每用户独立ZK身份仍未实现或验证。
