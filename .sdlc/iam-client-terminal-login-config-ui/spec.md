# Spec: IAM Client 按终端配置登录方式 (from intent.md 2026-10-22)
Status: accepted (single-terminal revision approved by Rain 2026-10-29).

## Requirements
1. 每个 OAuth Client 必须且只能选择一个终端类型，稳定值为 `BROWSER`、`APP`、`WECHAT_MINIAPP`、
   `WECOM_MINIAPP` 和 `WEBVIEW`。切换终端必须清除前一终端已选择的登录方式、身份源绑定、direct provider，
   以及由登录方式派生的 `authorization_code` / `external_code` grant；不同终端不能同时保留勾选状态。
2. 每个终端只展示面向使用者的中文登录方式，不在常用区域展示 `ExternalCodeType` 枚举名、result type、
   capability 或身份源主键。
3. 浏览器提供本地认证说明及 GitHub、企业微信、飞书、钉钉、微信网页登录，第三方方式写入
   `browserLoginProviderTypes`，继续由 `/oauth2/authorize` 和现有 Client 策略闭环处理。
4. APP 提供 IAM 统一登录以及飞书 AppLink、企业微信 SDK、钉钉 SDK、微信开放平台 SDK、运营商一键登录；
   后五种分别映射现有 APP 场景 external code type。
5. 微信小程序提供微信身份登录和微信手机号登录；企业微信小程序仅提供企业成员身份登录；端内网页提供飞书
   免登、钉钉免登、企业微信网页授权和微信公众号网页授权。前两者映射 WEBVIEW external code，后两者映射
   网页登录提供商并继续走 `/oauth2/authorize`。
6. 选择 external code 登录方式时自动加入 `external_code` grant；取消最后一个 external code 方式时自动移除。
   选择 IAM 统一登录或网页闭环方式时保留/加入 `authorization_code`，不得关闭公共客户端的 PKCE 要求。
7. external code 登录方式只有一个满足 provider 和 capability 的启用身份源时自动绑定；没有匹配项时禁用并
   提示先配置身份源；存在多个匹配项时仅针对该方式显示可读名称选择器。网页登录不支持 Client 级身份源绑定，
   只有对应 provider 恰好存在一个启用身份源时才可选择，零个或多个时均禁用并提示整改全局身份源配置。
8. “进入登录页后”只在当前 Client 已选择网页登录提供商时显示，候选仅来自允许集合；清除提供商时同步清除
   已失效的 direct provider。
9. 企业微信和微信的浏览器与端内网页仍复用 `browserLoginProviderTypes` 协议字段，但由持久化的终端类型明确
   归属；页面只在当前终端显示选中状态，不再把两处渲染成镜像开关。System 必须拒绝与终端不匹配的 provider
   或 external code type，不能只依赖前端清理。
10. OAuth2 客户端认证、原始 grant、scope、Origin、Token TTL、Token 格式和签名算法保留在折叠的高级配置。
    不再提供历史 browser external code 配置；默认折叠不得造成当前终端合法值丢失。
11. 选择 IAM 统一登录或网页闭环方式时，常用区域必须展示并校验重定向 URI；空回调不能只依赖保存后的服务端
    报错。已有回调 URI 必须无损回显。
12. 列表页按持久化终端类型展示中文终端名称和该终端登录能力，不直接拼接原始 code type；终端为空或字段组合
    跨终端的旧试验记录显示“待选择终端”，编辑保存时必须收敛为单终端配置。
13. System REST Form/VO 和 `sys_oauth2_client` 新增 `terminalType` / `terminal_type`。该字段只管理 Client 配置，
    不加入 Token 请求；IAM 第三方换码协议不变。System 保存时按终端 allowlist 校验 provider、code type 和派生
    grant，防止绕过 Admin 写入跨终端组合。

## Non-goals
- 不删除 `code_type` 协议参数，也不修改 `/oauth2/token` 请求格式。
- 不自动创建、启用或修改身份源，不猜测多个同类身份源应使用哪一个。
- 不在 Admin 中集成第三方 SDK、JSSDK 或真实第三方授权流程。
- 不允许同一个 OAuth Client 同时服务多个终端；需要多终端时创建多个 OAuth Client。

## Verification strategy
- System 单元测试覆盖五种终端 allowlist、缺失/非法终端、跨终端 provider/code type 拒绝，以及合法组合保存；
  Flyway 隔离测试证明新增 nullable 列不改写现有 Client 登录字段。
- Vitest 覆盖终端切换时清除其他终端方式、绑定、direct provider 和派生 grant，业务方式与底层字段映射、身份源
  选择、网页登录唯一性、回调 URI校验，以及持久化终端的回显。
- Cypress 覆盖管理员只能保留一个终端，切换后旧终端无勾选、保存请求包含唯一 `terminalType`，重新打开仍定位
  到该终端并只回显其登录方式。
- Admin 生产构建验证 Vue 模板、Element Plus 组件和打包路径。
- `git diff --check` 验证补丁格式；执行 System 目标测试、根 Maven、Admin 完整单测/构建/E2E 和隔离迁移测试。
