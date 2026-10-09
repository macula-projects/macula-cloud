# 实现进度：IAM 接入指南与交互演示

2026-10-09，分支 `feat/iam-protocol-playground`。Rain 先以 YES 批准完整计划，随后在实现完成门禁再次回复 YES，确认 Build 完成；现进入正式 Test，不代表验证或评审已通过。

## 已落地

- 计划步骤 1–3 的安全底座：默认关闭、显式 profile 许可、生产否决；独立 SecurityFilterChain、静态资源映射及同源要求。
- 专用客户端装饰器，保留业务仓库的 Spring 缓存代理；演示客户端固定 opaque，不附加业务权限 claims。
- 真实框架 introspection provider 的边界包装：跨演示/业务及不同演示客户端查询 inactive，正常业务查询不变。
- Redis 主记录与七种索引的关闭拒绝；业务授权不变；未修改 password/sms provider 或验证码默认 true。
- 步骤 4 短期状态组件：五流程限额、最长十分钟、令牌/设备期限上限、单次回调绑定、session ID 轮换、销毁清理和会话内串行轮询。
- 已在 plan.md 记录必要的 CORS 窄范围调整，以及不重复修改 DefaultSecurityConfiguration/LocalAccessTokenIntrospector 的原因。

## 本轮开发回归

- `mvn -pl macula-cloud-iam -am test -Plocal`：最终 57 tests，0 failures，0 errors，0 skipped；BUILD SUCCESS。
- 其中旧有 IAM 套件原 34 项，新加 23 项。新测试包含环境/issuer/CORS、客户端注册、introspection 隔离矩阵、权限 claims、会话生命周期、真实过滤链关闭及隔离 Redis 七类授权拒绝。
- 中间一次新增测试编译因 AssertJ 与泛型返回值重载推断失败；改为显式 String 局部变量后重跑成功。
- Redis 由测试自行启动随机端口、关闭持久化并自动销毁，未连接业务 Redis。
- `git diff --check` 通过。没有提交、推送、部署或更新已合并 IAM 变更的历史证据。

## 后续实现与开发回归（同日）

- 步骤 5–7：固定目的 HTTP 客户端、3 秒连接/10 秒完整响应超时、256 KiB 上限及禁用重定向；会话 API、未知输入拒绝、凭据脱敏；公共/机密 PKCE、JWKS 验签、UserInfo sub 绑定、OIDC 退出、刷新/凭据/password/sms 均通过真实 HTTP 集成。
- 步骤 8：仅演示 Device 的 none 客户端认证、独立框架确认 filter/provider、强制逐次确认及 CSRF；不把业务设备路由到演示页面。pending、批准、拒绝、客户端不匹配和重放已有 HTTP 断言。
- 步骤 9：五类中文接入场景、请求/响应/示例、移动布局、遮罩/显式查看/清空、复制、取消和错误恢复；没有前端新依赖，没有改变原 password/sms Provider。
- 最终 `mvn -pl macula-cloud-iam -am test -Plocal`：67 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS。其中新增真实 HTTP 测试 7 项；保留前轮测试，新增签名/issuer/audience/nonce/expiry 拒绝及 HTTP 重定向/大小边界测试。
- `node --check src/main/resources/playground/playground.js` 通过（在 IAM 模块路径下）。
- Chrome/Playwright 实测 8 组全部通过：4 视口 × 五场景布局、键盘和占位符复制；真实公共 PKCE/OIDC/UserInfo/遮罩；两个独立浏览器上下文的 Device 授权及轮询；停止设备轮询；mock 网络失败后的真实客户端凭据、introspection 和撤销。截图在 `target/playground-browser`，已人工查看 1440 与 390px 成图。
- HTTP 测试服务为随机本机端口 Tomcat，独立临时 Redis；浏览器验收结束后已停止该 JVM，相关临时 Redis 已退出。

## 发现并修正的问题

- 评审 I1：默认全局异常处理会记录校验/解析异常中的敏感输入。Rain 批准继续后新增演示控制器局部安全处理，实际 advice 的 16 组输入回归从红转绿；HTTP fixture 同步加入 advice。完整复验见 verification.md，待独立复审，不改变兼容 grant。

- 手动启动的测试 Tomcat 最初缺 RequestContextListener，原 IAM 身份服务无法获取当前请求；补齐测试容器装配，未改身份业务。
- Security 7 刷新 ID Token 不带 nonce：初次必须核对 nonce，刷新允许缺省，但有值时仍核对，始终验证签名/issuer/audience/subject/时效。
- Security 7 Device 端点需显式启用，仅在演示有效环境开启；框架不产生 slow_down，因此此分支使用独立 HTTP 响应验证客户端退避，已记录计划调整。
- 真实浏览器设备表单在 no-referrer 下发出 Origin: null；仅设备页面改为 strict-origin，保留精确 Origin 和 CSRF 校验，完整 URL 不作为 Referer 发送。
- 浏览器测试复跑可能复用先前 consent；测试允许框架直接回调，不能要求每次普通授权码登录必出确认页。Device 则每次强制明确确认。

## 下一门禁与尚未覆盖的证明

Rain 随后以 YES 确认 Build 完成。正式 sdlc-test 已执行完整 reactor、package、管理端回归、新旧 IAM 浏览器脚本，并补齐环境关闭的 HTTP 矩阵、设备到期/未知代码、授权码到期及 UI 超时/加载/重复提交证明，详见 `verification.md`。未提交、推送或部署。

防重复提交 guard 已由正式浏览器新增断言验证，新脚本共 9 组通过。共享会话跨节点一致性没有实现/验证，当前并发保证仅针对同节点会话。真实手机系统回调、代理部署和生产身份源未验证。
