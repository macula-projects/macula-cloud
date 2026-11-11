# 第三方认证组件边界

根包保留共享身份模型及已有序列化状态，其余实现按职责进入子包。

| 包        | 职责                                           | 主要扩展点                                                             |
|-----------|------------------------------------------------|------------------------------------------------------------------------|
| browser   | 网页认证、受控请求、路由 Filter 的公共生命周期 | AbstractThirdPartyAuthenticationFilter、ThirdPartyLoginStateRepository |
| provider  | 平台授权与身份核验适配、集成属性               | ThirdPartyProviderAdapter、ThirdPartyProviderAdapterRegistry           |
| binding   | 浏览器pending内的账号证明与绑定完成            | 调用service.identity共享规则                                           |
| directory | 企业目录准入、成员核验、后台刷新               | ProviderDirectoryAdapter                                               |
| cache     | 平台应用 Token 缓存、加密认证                  | ProviderApplicationTokenCache、ProviderCacheCipher                     |

## 抽象原则

平台差异通过 Adapter 接口和 Registry 分派；网页 Filter 的 state/回调处理沿用现有抽象模板。共享身份策略由网页和
external_code 调用，但网页建立 Session、协议签发 Token 的发布边界独立。

不建立一个囊括网页登录、SDK、目录和绑定的 AbstractThirdPartyService，也不为每个单一实现机械增加接口。账号绑定与目录核验有不同事务和失败语义，保持组合调用。

## 根包兼容类型

VerifiedExternalIdentity、ThirdPartyLoginContext、ThirdPartyAuthenticationToken、ExternalBindingTicket，以及包含序列化内部记录的
HttpSessionThirdPartyLoginStateRepository、PendingThirdPartyBindingRepository、ExternalBindingTicketStore
保留原包名。改变这些类型名需要单独设计旧 Session/授权数据兼容，不能当作纯目录整理。现有缓存键、JSON 结构、序列化类型与信任边界保持。

各厂商 Client/Filter 继续放在 authentication 下对应厂商包，协议 external_code 仍归 protocol/oauth2/grant/externalcode。

## 与公共服务和协议的关系

来源配置、账号匹配与绑定事务规则位于 `service.identity`，显式Client账号加载位于 `service.account`。公共身份服务依赖
`IdentityDirectoryService` 端口；此处 `directory` 的 ProviderDirectoryPolicyService 实现该端口，管理平台调用、加密快照与刷新，不被公共规则直接依赖。

`ExternalIdentityBindingService` 与对应Controller属于 Token/授权码证明合同，归 `protocol.oauth2.binding`；浏览器
`ThirdPartyBindingService` 留在当前 binding 子包，负责 Session pending 与密码/SMS/会话证明。二者共享账号绑定规则，但不混合结果发布。
