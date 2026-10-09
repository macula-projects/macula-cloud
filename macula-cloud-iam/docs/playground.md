# IAM 接入演示

作者：Rain

## 当前状态

已实现，等待 Build 完成确认及正式 Test/Review，尚未部署。入口 `/playground`。

| 场景 | 实际流程 | 接入边界 |
| --- | --- | --- |
| H5 / 移动端 | 公共授权码 + S256 PKCE、OIDC 验签、UserInfo、退出 | 浏览器生成绑定，演示后端中转换码；不是纯 SPA/原生 SDK，不签发 refresh token |
| 应用后端 | 机密授权码 + PKCE、刷新、客户端凭据、introspection/撤销 | secret 只在服务端；客户端凭据不代表用户 |
| Device | 发码、独立浏览器登录确认、按间隔轮询、取消 | 仅 playground.read，无 openid/refresh；取消轮询不是撤销 |
| 兼容接入 | password 的 username/password；sms 的 phone/captcha | 原业务不变；页面不发送短信、不伪造成功，验证码默认 true 仍为占位 |

## 配置与部署边界

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| IAM_PLAYGROUND_ENABLED | false | 演示总开关 |
| IAM_PLAYGROUND_ALLOWED_PROFILES | 空 | 显式允许的非生产 profile，逗号分隔 |
| IAM_PLAYGROUND_ALLOW_LOOPBACK_HTTP | false | 仅 local 激活时允许 localhost/127.0.0.1/IPv6 loopback 的 HTTP issuer |
| IAM_PLAYGROUND_CLIENT_SECRET | 空 | 外部注入的专用机密客户端 secret；缺失不注册机密演示客户端 |

生产 profile `prd`/`production` 拥有否决权，即便同时激活 local 或加入允许列表。配置必须真实反映环境，代码不能判断部署机器是否实际属于生产。仅用于隔离非生产环境，使用无业务权限测试身份，不自动创建账号或短信身份源。

`IAM_ISSUER_URI` 必须为浏览器和服务端均可访问的固定 HTTPS 根地址，不包含用户信息、路径前缀、query 或 fragment；本机 HTTP 需显式许可。回调固定为 `<issuer>/playground/callback`，不从请求 Host 拼接。

演示注册配置在启动时构建；更改开关、允许环境、issuer 或 secret 后应重启并重新开始流程，不依赖配置中心热刷新恢复旧流程。没有内置机密客户端密码，无数据库写入或迁移。

## 客户端与授权隔离

- `iam-playground-public`：公共授权码、强制 S256 PKCE、无 refresh grant。
- `iam-playground-confidential`：机密授权码/刷新、client_credentials、兼容 password/sms；仅注入 secret 时注册，secret 编码后供框架校验。
- `iam-playground-device`：公共 Device grant、仅 playground.read scope，不注册 openid 或 refresh grant。专用 none 认证适配仅匹配该客户端及两个设备相关端点，不放宽公共授权码认证。

`iam-playground-` 是保留前缀；数据库记录与演示 ID 冲突会拒绝，不覆盖。业务客户端查询仍走原缓存仓库，演示记录不进入该缓存。演示 access token 固定 opaque，即使由有业务权限的用户登录也不加入角色或数据权限 claims；ID Token 与 access token 格式不同。

introspection 中：业务调用者查询演示 token 返回 inactive；演示调用者只能查询所属客户端 token；业务客户端之间保持原行为。关闭后 code/access/refresh/id/device/user/state 七类存量索引均不可继续通过授权服务读取；不删除或扫描共享 Redis。不能仅靠角色为空代替令牌边界。

## 页面/API 与会话边界

`/playground`、`/playground/**` 和 `/api/v1/iam-playground/**` 关闭时连同静态资源、OPTIONS 一律 404；其他业务路径不变。开启后的副作用请求同时需要 CSRF 和精确同源 Origin。演示资源不放入默认静态资源目录，不套用原全局通配 CORS。Device 确认 POST 另外使用 CSRF filter，避免被授权服务器通用的 CSRF 忽略规则跳过；每个新设备必须明确确认，即使用户此前已同意同一 scope。

辅助 API 包括 GET configuration、POST flows、POST flows/{id}/callback、GET flows/{id}、POST flows/{id}/actions/{action}、POST reset。动作仅接受本会话流程引用，不接受任意 URL/client/secret/token；错误输出不回显原始异常或上游错误描述。连接超时 3 秒、完整请求超时 10 秒、响应上限 256 KiB、不跟随 HTTP 重定向。

界面默认遮罩令牌，只有明确点击“查看本次令牌”才取回当前会话值，30 秒后隐藏；不提供自动复制令牌。sessionStorage 仅保存短期回调绑定、流程引用和脱敏轨迹，不保存 token/密码/验证码；不使用 localStorage。回调执行后立即移除 URL 的 code/state，成功或失败均丢弃回调绑定。部署的访问日志/代理日志也应隐藏 OAuth 回调查询参数与请求正文，不能开启 HTTP 凭据追踪日志。

Device 原生表单采用 strict-origin Referrer-Policy，只发送源地址，不发送带 user_code/state 的完整 URL；这保证同源表单 POST 保留正确 Origin，而不是 no-referrer 下的 Origin: null。其他演示响应使用 no-referrer。

短期状态跟随 HttpSession，不以可轮换的 session ID 建外部索引。每会话最多 5 个流程、最长 10 分钟，并受 token/device 自身期限限制；访问时剔除过期状态，会话销毁清理令牌引用。重置应清理当前会话，不影响其他用户。敏感状态不写日志，不持久化密码、验证码、secret 或 verifier。

当前状态存储是同一节点会话内同步；多节点请使用会话粘性。既有共享会话需要额外验证状态序列化及并发行为，本实现不提供跨节点锁，不新增共享会话基础设施。重启或会话丢失后重新开始。

Device 按 interval（缺省至少 5 秒）轮询，slow_down 增加 5 秒，网络失败退避；没有并发轮询。框架 Security 7.0.7 的 Device Provider 明确不生成 slow_down，本功能处理该响应但不新增全平台限流。过期、拒绝、重放、停止或离页均结束轮询。前端网络连续失败三次后停止并提示重新开始。

ID Token 通过固定 IAM JWKS 验签，并校验 issuer、audience、时间和初始 nonce；UserInfo sub 必须与已验证 ID Token 一致。框架刷新 ID Token 时不再携带 nonce；若刷新响应仍携带 nonce 则继续核对，同时始终检查签名、issuer、client/audience、有效期和原 subject。未通过验证不写入会话令牌。

## 验证范围

`mvn -pl macula-cloud-iam -am test -Plocal` 验证单元边界、真实 SecurityFilterChain、隔离 Redis 及随机端口 Tomcat 的实际 HTTP 协议链路。数据库身份/业务客户端服务使用测试替身，不连接 Nacos/MySQL 或业务 Redis。既有 password/sms 回归保留，验证码默认 true 未修改。

浏览器脚本 `src/test/ui/check-playground.cjs` 使用本机 Chrome，覆盖 320/390/844/1440px、键盘/复制、真实 PKCE/OIDC、双浏览器 Device、停止与真实撤销。只有错误恢复的网络失败分支使用 mock；slow_down 由独立 HTTP 测试端点产生，不能当作真实 IAM 返回证据。

可在仓库根目录启动隔离测试站点（仅本机随机端口，Ctrl+C 关闭并清理临时 Redis）：

```bash
mvn -pl macula-cloud-iam -am test-compile -Plocal
mvn -pl macula-cloud-iam dependency:build-classpath -Plocal -Dmdep.outputFile=target/playground-classpath.txt -DincludeScope=test
task_playground_cp=$(< macula-cloud-iam/target/playground-classpath.txt)
java -cp "macula-cloud-iam/target/test-classes:macula-cloud-iam/target/classes:$task_playground_cp" dev.macula.cloud.iam.config.PlaygroundHttpIntegrationTest
# 另一终端，URL 使用上一步打印的 PLAYGROUND_TEST_URL：
PLAYWRIGHT_MODULE=/path/to/node_modules/playwright PLAYGROUND_TEST_URL=http://127.0.0.1:<printed-port> node macula-cloud-iam/src/test/ui/check-playground.cjs
```

截图位于 `target/playground-browser`，不提交。当前通过数量与未验证范围记录在 `.sdlc/iam-protocol-playground/build-notes.md`；完整 reactor/package、管理端套件、真实手机系统回调及真实生产身份源仍需后续验证，不能用浏览器视口代替真机验收。
