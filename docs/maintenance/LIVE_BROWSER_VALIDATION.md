# Chrome / Firefox 真实 HTTPS 流程验收

2026-10-16。本增量在 CI 自建 loopback ZooKeeper、合成凭据、独立 H2 与真实打包 Main（/browser-live 前缀、直接 HTTPS）。JDK 生成仅此夹具使用的自签名证书；浏览器仅在该 loopback 测试 context 忽略证书信任错误，不能把它称为受信公网证书验收。

## 必须通过

- Chrome（runner Chrome）和 Playwright 固定包匹配的 Firefox 分别填写真实登录表单、进入真实后端页面。
- 登录后 Session ID 变化，cookie HttpOnly/Secure/正确上下文路径。
- 错误 CSRF 实际返回403，Java观察者确认没有对应 ZK 节点。
- 通过真实 Add Property 弹窗提交带中文/HTML字符的值，Java观察者从实际 ZooKeeper逐字段核对；值不能作为元素解析。
- 实际 Logout、再访问home回到login；USER角色的编辑菜单缺失且直接POST（正确CSRF）仍403，后端没有副作用。
- 页面无JS异常，不请求外部资源，资源/导航保留前缀；保存引擎实际版本和结果，失败截图及应用日志保全。

## 实现与限制

LiveBrowserSmoke 需要 ZKUI_OWNED_BROWSER_FIXTURE=true，并向 Node 子进程环境传递仅本夹具随机生成的密码，不输出该密码。所有浏览器URL必须是自建127.0.0.1 HTTPS及固定上下文。常规Maven不启动浏览器；CI独立live-browsers job保留所有既有Java/Chrome排序/容器/MySQL/安全审计检查。

本地Node语法检查通过；首次Maven解析因新会话缓存和未配置Java代理失败，随后使用现有环境代理重试，未更改依赖或关闭校验。本机未安装浏览器；真实Chrome/Firefox流程必须由准确head CI给出，不能以编译成功代替。

这些结果只覆盖当前CI引擎/当前维护版，不复现上游#94原Ubuntu18/Firefox版本故障。原Windows9s:1s、真实AD/LDAPS、IPv6、反向代理TLS、前端依赖升级和许可证分发缺口仍独立跟踪。

## 退出旧前端脚本依赖

页面仅使用模态框、下拉菜单、属性值填充和导入查看链接。已将 home.js 改为原生 DOM API，移除 jQuery1.10.2 与 Bootstrap JS 源文件及模板引用，组装包回归必须确认两个旧文件不存在。新增Tab焦点圈定、Escape关闭/焦点还原及无jQuery回归；现有排序、属性编辑、只读遮蔽、真实弹窗提交/退出测试继续保留。导入查看链接仅接受HTTP(S)，不把输入作为HTML解析或选择器插入。

原CSS视觉、字体和图片不改；13个实际静态文件的SHA-256和归属状态见 FRONTEND_ASSET_INVENTORY.json。Bootswatch3.1.1的MIT/作者头保留，历史字体/图片来源与发行通知仍需进一步核对，不能称前端许可证已清理完毕。

官方依据：jQuery [3.5安全修复](https://blog.jquery.com/2020/04/10/jquery-3-5-0-released/)及[安全公告](https://github.com/jquery/jquery/security/advisories/GHSA-gxr4-xjj5-5px2)；Bootstrap [3系停止维护说明](https://blog.getbootstrap.com/2019/07/24/lts-plan/)。这里只认定旧组件版本及移除事实，没有宣称已构造出本项目可达XSS利用链。CSS继续使用旧布局，不能把删除JavaScript描述为Bootstrap全量升级。

本地定向首次未clean构建触发新增打包断言：target/classes仍留有此前拷贝的旧jQuery文件，组装包因此被拒绝。没有删除断言；改用项目规定的clean verify重新生成全部资源。源码已删除不代表旧构建目录产物已清理，交付与验收必须核对真正打包内容。

本地最终clean定向验证于2026-10-16 13:00:26 +08:00完成BUILD SUCCESS：4单元+BrowserPagesIT/DependencyPackagingIT共4集成，零失败/错误/跳过。旧脚本不在新组装包、真实Main渲染夹具成功；此8项不冒充完整162项或实际浏览器运行。Chrome/Firefox和完整回归仍以准确headCI为准。
