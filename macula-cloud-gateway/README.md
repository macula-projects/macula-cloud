# Macula Cloud Gateway 网关中心

平台对外统一入口，提供统一认证、鉴权、接口加解密等服务

## TinyID 号段接口

路由包含 `/tinyid/api/v1/admin/**` 和 `/tinyid/api/v1/id/` 下四个精确发号路径：
`nextId`、`nextIdSimple`、`nextSegmentId`、`nextSegmentIdSimple`，
通过 `StripPrefix=1` 移除 `/tinyid` 前缀后转发至 `macula-cloud-tinyid`，Server 不设置上下文前缀。
管理接口外部地址不变。除上述路径外，不开放其他 TinyID 路由。
新号段请求为 POST，业务参数只有 bizType；Starter 使用统一应用 AK/SK 进行 HMAC 签名，
网关沿用应用 URL 访问策略校验后向下游传递 JWT。不得将此路径加入匿名白名单。
沿用共享网关认证授权策略，TinyID 不额外区分 HMAC 应用与个人 Token；身份边界由后续统一策略处理。
与 system 一致，StripPrefix 先于 HMAC 验签执行；验签和应用 URL 授权使用改写后的路径，
号段接口授权表达式为 `POST:/api/v1/id/nextSegmentIdSimple`，不包含 `/tinyid`。

同步核对配置中心中的路由、应用密钥与 URL 授权，以及 TinyID Server 的 JWT 验证配置。
能访问新接口的应用可以申请已存在的任意 bizType；旧 TinyID Token 授权不再约束任何发号接口。
客户端、Server 和网关配置需协调升级与回滚。
Server 仍信任有效 JWT，不额外识别其原始认证方式。

## 加解密服务

CryptoLocaleServiceImpl是本地加解密实现，如果要接入密钥服务器需要修改
