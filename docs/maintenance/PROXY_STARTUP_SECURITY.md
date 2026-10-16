# 示例账号与可信代理边界

2026-10-16。SEC-05 的示例账号启动策略、SEC-10 的转发元数据可信边界独立修复。

## 行为

- 默认拒绝 admin/manager、appconfig/appconfig；必须替换两对示例凭据。仅明确 allowSampleUsers=true 可用于隔离演示并输出警告。
- 本地用户 JSON 格式/条目、非空账号密码角色、唯一用户名在数据库/hosts初始化/监听前校验；错误不回显 JSON 或原始 parser exception。LDAP 模式不启用本地用户。
- X-Forwarded-For 默认关闭。开启必须给 trustedProxyAddresses，以逗号分隔精确 IPv4/IPv6 数字地址；拒绝空列表、DNS、通配、CIDR 和非法地址。
- Jetty customizer 根据原始连接 EndPoint 的真实 TCP 对端决定是否调用原生 ForwardedRequestCustomizer，覆盖 HTTP 与直接 HTTPS 连接。ServletUtil 只读取经此边界处理的 request.getRemoteAddr，不再次直接读报头。
- 代理必须覆盖或清除所有客户端转发元数据，配置见 README；信任的是立即对端，不自动推断任意代理链。地址列表变化需重启。

## 验收

- StartupSecurityTest：示例默认拒绝/演示显式放行/自定义用户/格式与空值重复拒绝/LDAP旁路/IPv4IPv6与非法代理列表。
- StartupSecurityIT：真实打包 Main 拒绝示例用户或缺失可信代理配置，退出非零、端口不监听、H2文件不创建、不输出密码JSON。
- ForwardedHeadersIT：默认关闭、可信loopback、非可信地址三组真实应用，伪造scheme仅可信对端改变Secure cookie；伪造客户端IP仅可信对端进入实际审计记录。
- 既有历史迁移负向 Main 夹具使用独立合成凭据，保持原数据库失败断言；不让新启动策略掩盖旧库拒绝检查。

准确 head 本地和全 CI 结果待独立核验，不把编译通过等同于安全验收。此修复不将默认 HTTP 或 OPEN_ACL_UNSAFE 宣称为生产推荐配置；HTTPS/真实代理部署/ACL仍需明确配置。旧 Windows/AD环境缺口不在本项覆盖。

## 中间验证记录

首轮独立 clean verify：36 单元通过，121 集成中2失败（DatabaseReadinessIT 仍用默认示例账号，触发新凭据保护；新增代理写请求漏 currentPath 返回400）。均为测试夹具适配，不删除数据库失败/HTTP拒绝/角色断言：为数据库负向 Main 提供独立合成凭据，代理请求补齐必需路径。另补 REST 隐式 host 的实际可信/非可信回归。定向构建在最后数据库夹具修订编译前已启动，不能把它当最终源码全通过；最终独立 clean verify 和准确 head CI 必须重新完成。此前失败不能称为157项通过。
