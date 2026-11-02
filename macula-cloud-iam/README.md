# Macula Cloud IAM

## 作用

`macula-cloud-iam` 是 Macula Cloud 的身份认证与授权中心，为管理端、业务应用和服务调用统一签发、校验及撤销访问令牌。

## 核心功能

- OAuth 2.1 授权码模式与 OpenID Connect 登录。
- 用户名、手机号、邮箱及验证码登录。
- GitHub、企业微信、飞书、钉钉、微信等第三方身份登录与绑定。
- `client_credentials` 服务身份认证。
- access token 刷新、撤销、内省以及 JWT 公钥发布。
- 按 OAuth Client 管理回调地址、scope、租户、登录方式和浏览器 Origin。

## 使用

先在 System 管理端创建 OAuth Client，配置允许的授权方式、scope 和回调地址。浏览器与移动端公共客户端推荐使用授权码模式和 S256 PKCE：

```text
GET /oauth2/authorize
    ?response_type=code
    &client_id=<client_id>
    &redirect_uri=<registered_redirect_uri>
    &scope=openid%20profile
    &state=<random_state>
    &code_challenge=<s256_challenge>
    &code_challenge_method=S256
```

回调取得授权码后，通过 `/oauth2/token` 换取令牌。应用必须校验 `state`；请求了 `openid` 时还应校验 ID Token。

常用端点：

| 路径 | 用途 |
| --- | --- |
| `/login` | 统一登录页 |
| `/oauth2/authorize` | OAuth 2.1 / OIDC 授权入口 |
| `/oauth2/token` | 获取或刷新令牌 |
| `/oauth2/introspect` | 查询令牌状态 |
| `/oauth2/revoke` | 撤销令牌 |
| `/.well-known/openid-configuration` | OIDC Discovery |
| `/oauth2/jwks` | JWT 验签公钥 |
| `/userinfo` | 获取已授权的用户信息 |
| `/connect/logout` | OIDC 退出 |

本地开发先按[根 README](../README.md)启动 MySQL、Redis、Nacos 和 System，再在 IDE 中运行 `MaculaIamApplication`。默认访问地址为 <http://127.0.0.1:9010>。

生产环境应使用 HTTPS，并通过环境变量或配置中心提供稳定的 `IAM_ISSUER_URI`、签名密钥和加密密码。完整 Compose 配置见 [`deploy/README.md`](../deploy/README.md)。
