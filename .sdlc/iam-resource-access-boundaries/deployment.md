# 本地镜像更新准备记录

Status: deployed

四个镜像均已构建：Admin、IAM、Gateway、System。Java镜像从已经验证的当前Jar使用仓库原运行阶段构建，避免Docker Maven缓存使用旧Boot依赖；Admin沿用原Dockerfile。镜像摘要及Jar SHA-256记录在私有目录provenance.json、pending-deployment.json。

当前数据库V18，现有Admin为SPA/单OIDC，现有Gateway为SERVICE/单OAuth。已从当前Gateway镜像读取实际凭据并通过在线内省active:false响应验证；没有猜测或轮换Secret。沿用已有Admin/Gateway身份补充deploy/.env中的IAM_PLATFORM_CLIENTS_ENABLED、IAM_ADMIN_CLIENT_ID、IAM_INTROSPECTION_CLIENT_ID和IAM_INTROSPECTION_CLIENT_SECRET，其他配置保留。

完整业务库备份：/private/tmp/iam-resource-deploy/macula-system-before-v19.sql；原deploy/.env备份同目录deploy.env.before.private，均为私有文件，不入Git。

自动审批拒绝了System容器替换命令：原因是启动会执行V19业务库迁移及平台身份认领，“更新镜像”未被视为明确授权数据库变更。拒绝发生在命令执行前，四个现有容器ID未变且健康；未绕过拒绝，没有运行迁移或更新依赖服务。后续需Rain明确允许业务库V19迁移及平台身份认领，再按System→IAM→Gateway→Admin协调替换。

## 授权和执行结果
Rain随后明确回复“允许”，授权V19业务库迁移、平台身份认领和四容器替换。按System→IAM→Gateway→Admin执行，每次均使用`up -d --no-deps --no-build --force-recreate --wait`，全部成功。此前自动审批拒绝的动作没有绕过，明确授权后才重新执行。

业务库已由V18迁至V19，Flyway日志确认只应用新增迁移。平台Admin为APPLICATION/单OIDC、授权版本2；Gateway为RESOURCE_SERVER/无业务协议行、授权版本2。与升级前完整备份逐字段对比：两身份主键、client_id与Secret哈希均保持原值。

四服务均healthy，当前容器镜像摘要与已构建标签一致；Java容器内Jar SHA-256与本轮已验证构建产物完全一致。其他10个Compose服务的容器ID未变，没有重启中间件或删除卷。

实际运行检查：Admin首页200；IAM Discovery/JWKS均200且issuer与Admin配置一致；匿名System/网关受保护路径401；现有Gateway凭据内省不存在Token返回200/active:false，证明原Secret仍有效；资源服务器尝试client_credentials取Token返回400。Admin的Base64运行配置解码后client_id与已有Admin身份一致，不包含Secret。四服务日志未发现应用启动或迁移失败。

真实部署浏览器冒烟：`cypress run --config-file /private/tmp/iam-resource-deploy/cypress.config.cjs`输出`1 passing`，确认Admin读取配置并进入真实IAM授权登录表单；没有提交用户凭据，不冒称已用用户账号完成部署后登录。完整认证与故障矩阵的隔离证据仍见verification.md。

访问地址：http://127.0.0.1:5900。旧授权不进入新版本命名空间，需刷新页面并重新登录。备份仍保留在私有临时目录，不自动删除或恢复。

私有证据：`verification.json`、`identity-preservation.json`、`provenance.json`、四个`*-up.log`、`*-runtime.private.log`、`browser-smoke.log`。以下为已运行镜像摘要：
- macula-cloud-system：`sha256:9c411d4b22d8e1890404a8db355e489ae303d13c12fd6ddd6f35d8dfc3c9f637`，healthy。
- macula-cloud-iam：`sha256:5acf6dee9e3811a539e6b1557366f66803f7a2968d0cef31a8a21f9c968c3b2a`，healthy。
- macula-cloud-gateway：`sha256:9b55555a387f0af5c42d4330a9f15266ac5e2fc408e640d9c44d0e364a06930f`，healthy。
- macula-cloud-admin：`sha256:1217b288eed29ab5402f473a4bb5b17761ac27329b117192e29e440957f27db3`，healthy。
