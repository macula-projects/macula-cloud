# Spec: IAM Client 按终端配置登录方式 (from intent.md 2026-10-22)
Status: accepted.

## Requirements
1. OAuth Client 表单以终端类型选择器作为登录能力配置入口，稳定选项为浏览器、APP、微信小程序、企业微信
   小程序和端内网页；切换终端只改变当前展示内容，不清除其他终端已经选择的配置。
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
9. 企业微信和微信的普通浏览器与端内网页入口共享同一个 `browserLoginProviderTypes` 权限，两处必须展示为同一
   “网页认证能力”的镜像开关，任一处修改同步生效；摘要显示“网页（浏览器/端内）”，不得伪造可独立授权的终端
   边界。
10. OAuth2 客户端认证、原始 grant、scope、Origin、Token TTL、Token 格式、签名算法及仍受服务端支持的历史
    browser external code 配置移入折叠的高级配置。默认折叠不得造成保存时丢值；真正不受当前枚举支持的历史值
    必须阻止保存并提示先迁移，不能承诺服务端无法接受的透传。
11. 选择 IAM 统一登录或网页闭环方式时，常用区域必须展示并校验重定向 URI；空回调不能只依赖保存后的服务端
    报错。已有回调 URI 必须无损回显。
12. 列表页使用中文摘要展示已配置的终端登录能力，不直接拼接原始 code type；仍受支持但未进入常用配置的值以
    “高级兼容配置”标记，不伪装成已识别业务方式。
13. 不修改 System/IAM 服务端安全校验、数据库迁移、公开 API 或第三方换码逻辑；前端自动映射不是新的授权
    边界，服务端仍按现有字段拒绝非法组合。

## Non-goals
- 不删除 `code_type` 协议参数，也不修改 `/oauth2/token` 请求格式。
- 不自动创建、启用或修改身份源，不猜测多个同类身份源应使用哪一个。
- 不在 Admin 中集成第三方 SDK、JSSDK 或真实第三方授权流程。
- 不把同一个 OAuth Client 限制为只能服务一个终端。

## Verification strategy
- Vitest 覆盖终端切换、业务方式与底层字段双向映射、grant 联动、external code 唯一身份源自动绑定/多身份源
  选择、网页登录身份源唯一性、共享网页能力同步、direct provider 联动、回调 URI 校验以及高级配置无损回显。
- Cypress 覆盖管理员编辑 Client、跨多个终端选择登录方式、保存请求字段和重新打开后的业务摘要。
- Admin 生产构建验证 Vue 模板、Element Plus 组件和打包路径。
- `git diff --check` 验证补丁格式；后端未变更，不以本轮 UI 测试替代既有服务端安全测试。
