# Spec: Admin授权码登录接入 (from intent.md 2026-11-07)
Status: accepted

## Source intent
[已接受意图](intent.md)：Admin改为authorization_code接入，保留现有用户、租户、权限和菜单初始化，验证后解除新版镜像整体切换阻塞。

## Requirements
1. Admin采用独立SPA公共Client（none），仅authorization_code与S256 PKCE，无浏览器Secret、无password/sms/email直接Token入口、无refresh_token。复用IAM现有真实OIDC能力，明确请求openid profile，不虚构业务Scope；密码/短信/第三方等认证由IAM托管页按已有配置提供。
2. 登录入口保留现有品牌外观，改为前往IAM登录及可恢复错误/重试，不再收集用户密码。使用Web Crypto生成state、nonce、PKCE verifier/challenge；每个标签页的登录事务存sessionStorage、10分钟有效、一次消费。防重复提交，禁止Math.random回退；HTTPS或明确的本地回环开发地址才支持。
3. 固定回调为Admin公开地址下/auth/callback，不含hash片段；Nginx/Vite正确返回SPA入口。进入回调先将code/state移至内存并清理地址栏，再完成Vue hash路由回调处理。拒绝缺失/重复参数、state错误/过期/重放、非预期issuer和外部续转目标；回调禁止缓存、Referer传播及Nginx访问日志记录原始查询。失败必须重新发起授权，不重放可能已消费的code。
4. Discovery来自显式配置的IAM地址，严格匹配配置issuer；端点限制为预期IAM origin，拒绝未知或被替换的端点。Token兑换使用隔离HTTP通道，只发client_id/code/同一redirect_uri/code_verifier，不附带旧Bearer、租户头或客户端Secret。IAM仅对已登记Admin Origin放行Discovery/JWKS的必要GET/OPTIONS，保持Token按Client的Origin校验，不允许带跨域凭据或泛化CORS。
5. 通过成熟JOSE库校验ID Token的JWKS签名和算法白名单（Admin使用RS256），以及iss/aud/多aud时azp/exp/iat/sub/nonce，存在at_hash时校验；不把仅解码视为验证。ID Token不替代System权限结果，也不作为API Bearer。保留CustomOidcTokenCustomizer及已有access claims，不为本需求重写其用户改动。
6. Access Token按expires_in保存于当前标签页sessionStorage；认证相关用户信息、菜单、权限及租户缓存同样按标签页隔离，其他偏好保持原存储。移除并清理旧TOKEN Cookie及旧认证缓存，不做旧密码流程回退。初始化必须依次获取System当前用户、可访问租户、菜单与权限，通过现有范围校验后才进入管理页面；失败清理临时令牌和认证数据。刷新页面恢复未过期会话并重新核实初始化，过期或401清理并提示重新授权，不无限重定向、不暗中签发refresh。
7. 仅恢复登录前保存的受控站内路径，禁止//、外部URL和指向登录/回调的循环目标。退出当前Admin及清缓存均清除Token、登录事务、租户Pinia状态、认证缓存与动态路由；保持应用本地退出语义，不擅自结束IAM全局SSO。之后用户主动登录可复用IAM会话，页面不自动回跳造成“退出立即又登录”。
8. Admin Client使用新独立ID，明确配置SPA/ENABLED/OIDC/openid profile/authorization_code/S256/精确回调和Origin/已确认的认证租户及用户目录。当前本地网关与旧Admin共用的Client保留为服务端机密身份，显式配置新模型用于内省，保留其服务端凭据，不转为none，不复制Secret至浏览器。只处理这两个明确指定的Client，不猜测或自动启用其他历史记录；不提供匿名bootstrap API。
9. 从Admin构建环境、运行时config.js、Compose及示例中移除浏览器Client Secret和演示账号密码配置；公开配置仅含Client ID、IAM issuer/地址、Scope、Admin公开地址等。身份材料不进入console、URL续转、截图或提交文件。机密内省凭据留在服务端，配置步骤不得输出摘要或原文。
10. 完成隔离验证和必要门禁后，承接既有本地更新授权：先保留恢复依据并受控停止旧IAM，执行追加迁移及这两个Client的显式配置，再切换新System/IAM/Admin；避免新草稿模型与旧IAM混跑窗口。验证完整浏览器登录和Gateway/System接口，不以容器健康代替登录可用。中间失败维持可诊断状态，不自动清库或恢复会绕过新状态的旧混跑。非目标服务/数据保持原样，不Git提交/推送或远程发布。

## Non-goals
不恢复password grant，不新增BFF、通用账号注册/找回、刷新令牌或静默续期，不改变用户/租户/角色/URL权限模型，不扩展全局SSO退出，不重新实现JWT密码学，不改第三方来源/绑定策略，不清空或批量启用既有Client。

## Design
Vue保持hash路由，增加固定物理回调入口及授权事务/Token会话/初始化三个独立职责。Web Crypto负责随机数和PKCE，jose负责JWT/JWKS签名与声明验证；Axios现有业务请求从新会话读取Bearer。认证缓存按标签页隔离，Router在完整初始化前不装载业务动态路由。登录和退出均有明确用户动作。
Admin独立公共Client与已有Gateway机密内省Client分开，IAM开放最小元数据CORS。Discovery的issuer和端点采用显式可信配置，不从任意请求或回调推断。部署时通过受控本地配置步骤调用既有完整校验建立新模型，不新增匿名初始化入口。

## Data and interfaces
新增公开的Admin issuer/公开地址配置及/auth/callback入口；移除OAUTH_CLIENT_SECRET和浏览器演示账号密码。前端认证存储/请求拦截/租户缓存/路由/退出同步切换，不只改登录按钮。IAM授权/Token协议不新增grant；补元数据/JWKS精确Origin CORS。复用V16的Client模型；如确需结构变化只能追加迁移，不能改已执行脚本。Client配置及运行时env必须可核对且不输出凭据。

## Flagged concerns
- 当前Admin与Gateway共用机密Client；必须新建独立Admin公共Client并明确配置已有机密Client，否则修改为none会破坏内省，non-blocking（本范围已覆盖）。
- SPA的sessionStorage仍可被同源脚本访问；本次禁止长期Cookie/localStorage令牌及浏览器Secret，密钥/JWT验证采用成熟库；不宣称等价于HttpOnly/BFF隔离，non-blocking。
- 采用OIDC的openid/profile为现有真实能力，仍使用用户要求的authorization_code流程；ID Token签名/nonce校验和System权限初始化缺一不可，non-blocking。
- 固定物理回调必须兼容hash路由且抑制查询日志，不能把含code地址复制到hash后长期保留，non-blocking。
- 本轮本地退出不清除IAM全局SSO，再次主动登录可能复用已有IAM会话；无自动登录循环，non-blocking。
- 切换包含短暂本地维护窗口；不能以旧IAM和新草稿模型长期混跑作为回退，恢复必须连同Client配置/镜像/迁移边界核对，non-blocking。

## Verification strategy
Vitest验证PKCE/state/nonce、事务超时/消费、URL清理和续转白名单、JWKS签名/issuer/audience/nonce/算法错误、sessionStorage过期/标签页隔离、初始化失败清理、401与退出。Cypress验证真实跳转/回调、拒绝/重放/刷新/返回原页面/无权限/退出/可恢复错误及390px布局；Mock证据单列。IAM真实Security链验证公共授权码成功、错误verifier、无Secret/无refresh、元数据/JWKS受控CORS及网关机密内省仍正常。隔离MySQL/Redis验证两个Client的显式配置、V16草稿边界与缓存；本地实际浏览器从Admin进入IAM登录后回到Admin，读取用户/租户/菜单和新应用配置接口，再核对镜像、迁移、健康及敏感日志。执行相关构建、单测、既有配置/绑定回归、Checkstyle、无fix ESLint、diff检查；保留未做厂商联调的边界。

## Governing policy and sources
AGENTS.md及architecture/frontend-development/dependencies-release、REVIEW.md与既有新Client规格；未发现额外组织品牌/合规技能，不虚构要求。jose官方说明其支持浏览器、JWT签名与声明验证：https://github.com/panva/jose 。已检查oidc-client-ts官方回调与响应校验实现，最终选择jose完成明确要求的完整签名/声明校验，协议编排限定为本应用的授权码流程，不引入第二套会话SDK。

## Acceptance
Rain于2026-11-07以YES接受本设计及全部non-blocking concerns；尚未接受实施计划。
