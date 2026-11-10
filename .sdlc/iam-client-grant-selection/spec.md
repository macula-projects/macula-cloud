# Spec: IAM Client 授权方式勾选与认证接入辅助 (from intent.md 2026-11-05)
Status: accepted

## Source intent
[intent.md](intent.md)，Rain 于 2026-11-05 确认实施，并指定 private_key_jwt 保持配置 JWKS URL。

## Requirements
1. 授权方式改为中文复选框，附协议值和用途说明；保存仍提交逗号分隔的 authorizationGrantTypes。
2. 按当前 System/IAM 支持联动：none 禁止 client_credentials；新增 sms/email/external_code 仅允许 none；授权码与刷新令牌允许现有四种认证方式。password 作为历史兼容选项保留，明确不推荐新接入使用。
3. 编辑时完整回显旧值，包括多个认证方式和未知授权值；未知值显示迁移提示并阻止保存，不因打开页面静默丢失。旧密钥认证 sms/email 组合仅在认证方式及授权集合均未改变时保持现有兼容例外。
4. 切换认证方式后保留已选项，展示冲突和原因，允许取消不兼容项；修正前阻止保存。登录方式自动派生的 authorization_code/external_code 与复选框必须一致；取消依赖中的 grant 时提示先取消对应登录能力。
5. 认证选项使用中文名称并保留协议值与用途说明；none 强制 PKCE，且不提交密钥。
6. client_secret_basic/client_secret_post 的新建及重置提供安全随机密钥生成、显示隐藏、复制与请求示例。重置调用既有接口，成功后才标记为已生效。
7. Basic 请求头依据原始 Client ID/Secret 按 application/x-www-form-urlencoded 分别编码后，以冒号连接并 Base64；POST 示例使用表单请求体。对特殊字符、Unicode 和 shell 引用正确处理，密钥不进入 URL。
8. 原始密钥及生成的 Basic 内容仅保存在当前操作内存，不写日志或浏览器存储，关闭、切换 Client 或不再使用密钥认证时清除。读取已有 Client 不能恢复密钥，提供占位符示例或供用户临时输入已知密钥生成示例。
9. private_key_jwt 仅展示 HTTPS JWKS URL、现有 RSA 签名算法选项和说明。提供客户端生成短期 client_assertion 的签名接入示例，涵盖 kid、iss/sub=client_id、aud、exp、jti 以及 Token 表单参数。aud 明确使用实际 IAM 接受的地址，不从管理端 Origin 猜测。
10. none 提供授权码与 S256 PKCE 接入示例；示例区按已选 grant 展示适用请求，区分授权请求和 Token 请求，不暗示 client_credentials 会返回用户身份或刷新令牌。
11. 原生 Clipboard API 不可用或失败时允许手动复制；随机数能力不可用时明确提示，不退化使用 Math.random。
12. 更新 Admin/IAM README，解释登录页短信认证与 sms grant 的差异，并标明组合限制属于当前产品策略。

## Non-goals
不修改 Token 协议、服务端认证授权策略、数据库或管理 API；不新增依赖；不添加私钥上传、生成、托管或公钥直接导入；不执行真实密钥轮换、业务 Client 配置修改或部署。

## Design
沿用 Vue 3、Element Plus。在 Admin API 模型或邻近专用辅助模块集中定义 grant 目录、兼容性计算和示例生成，页面负责展示及显式交互。兼容性依据 System validate 与 IAM public token converter，不由终端名称推断。
以打开时的认证方式和授权集合快照判断 legacy 例外，不使用单一布尔值放宽后续修改。已选冲突项仍可取消；自动派生项提示依赖，不自动清空身份源。多认证方式旧记录原样回显，显式修改才收敛为单选。
密钥生成使用 Web Crypto 的 32 字节安全随机数并编码为 URL-safe 文本。新建及重置各自维护临时密钥生命周期；所有请求样例均由纯函数生成，不实际调用 IAM。私钥 JWT 样例由接入应用本地执行，管理端不收集私钥。

## Data and interfaces
沿用 authorizationGrantTypes CSV、clientAuthenticationMethods、clientSecret、jwkSetUrl 和 signingAlgorithm。复用既有创建、更新和重置接口。无 API/schema/persistence 变更；密钥仍由 System PasswordEncoder 单向保存。

## Flagged concerns
- 原始密钥无法从存储恢复：已有记录只提供占位符或临时输入，不自动重置，non-blocking。
- System 的历史短信/邮箱例外依赖整个认证和授权集合未改变：前端必须精确保持同一条件，non-blocking。
- 现有终端单选 SDLC 方案与当前源码存在差异：本次仅针对当前源码改进，不合入终端持久化改造，non-blocking。
- 浏览器 UI/E2E 使用 mock 只能验证交互及提交内容：不宣称完成真实 IAM 或第三方平台联调，non-blocking。
- 当前项目 REVIEW.md 未声明额外组织策略；遵守 AGENTS.md、frontend-development.md、architecture.md、testing.md 及 REVIEW.md 的 Bugs/Security/Compliance 要求，non-blocking。

## Verification strategy
Vitest 验证认证/grant 组合、历史集合兼容、依赖项、未知值保留、Basic 特殊字符编码、请求体与 shell 引用、密钥随机能力失败及示例无 URL 密钥。
Cypress 验证四种认证方式界面、禁用原因、冲突解除、创建/重置成功失败、敏感信息清除、旧记录回显与 CSV 提交、JWKS 条件展示与复制失败处理；接口使用 mock。
执行相关及完整 Vitest、生产构建、OAuth Client Cypress、变更文件 ESLint 和 git diff --check。桌面/窄屏检查抽屉、复选框及长示例的可用性。无后端改动时不以 Maven 测试作为前端行为证据。

2026-11-05：Rain 以“YES”明确接受本规格及 flagged concerns。
