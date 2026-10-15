# 安全修复动态验证记录

日期：2026-10-15。仅使用临时目录、loopback ZooKeeper、临时 H2 和随机模拟凭据；没有访问生产服务。

## SEC-01：导入授权

基线：Fork `master` 的 `5a76b3b`（已合并文档 PR #1、测试 PR #2）。

修复前：对原始应用运行新增 `ImportAuthorizationIT`，6 项中 3 项断言失败、1 项读取超时、2 项通过。USER 可以新增、覆盖、删除节点，未知角色也可创建节点；无效上传仍进入解析和 Dao 流程；USER 提交的远程 URL 进入取文件流程并在测试监听端没有响应时超时。匿名正常路径已被 AuthFilter 重定向，ADMIN 正常导入也通过。

修复：`Import.doPost` 首先通过现有 Session 校验登录身份及精确 ADMIN 角色；不满足即 403 并返回。该检查先于 Dao 创建、multipart 解析、历史记录、URL 访问及 ZooKeeper 写入。匿名正常路径仍由 AuthFilter 跳转登录。

回归：`ImportAuthorizationIT` 6 项测试覆盖 USER 新增/覆盖/删除均不改变 ZooKeeper、匿名拒绝、未知角色拒绝、畸形上传先拒绝、URL 不被访问、ADMIN 新增/覆盖/删除正常。使用实际打包 JAR，不以 mock 或隐藏按钮代替服务端验证。

验证命令：JDK 8 + `./mvnw -B -ntp clean verify`。本地共 3 项单元测试、11 项集成测试，0 失败/错误/跳过。远程状态以 PR 的 GitHub Actions 检查为准。

范围限制：管理员的 URL 导入仍有 SSRF、超时和资源边界待处理（SEC-03/11）；CSRF（SEC-07）尚未修复；授权补丁不替代这些边界。SEC-02 将单独验证和修复。
