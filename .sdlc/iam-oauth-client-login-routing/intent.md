# Intent: IAM OAuth Client 网页登录路由
Author: Rain. Status: accepted.

## Problem
应用从 `/oauth2/authorize` 进入 IAM 后只能落到通用登录页，IAM 尚不能按照当前 OAuth Client 的授权范围和
浏览器容器自动选择最适配的第三方网页登录。企业微信或微信公众号内网页因此需要用户再次选择登录方式；
同时，现有网页登录提供商是全局展示的，无法满足“每个 Client 必须显式配置第三方类型，未配置时只能使用
本地账号认证”的边界。企业微信端内网页还被重复暴露成 `external_code` 类型，和应由 IAM 管理的浏览器
授权闭环混在一起。

## Proposed outcome
OAuth Client 可以显式选择允许使用的网页登录提供商，并选择直接进入一个提供商或由用户在登录页自行选择。
IAM 在已验证的 `/oauth2/authorize` 请求中识别企业微信和微信浏览器环境：只有当前 Client 允许对应提供商
时才自动续转官方网页授权，回调后恢复原授权请求并签发受 S256 PKCE 保护的 IAM authorization code。
未配置网页登录提供商的 Client 只提供用户名、手机号或邮箱的密码/验证码认证。微信和企业微信小程序继续
只在小程序端取得 code 后调用 `external_code`，企业微信端内网页不再定义 `WECOM_WEBVIEW_CODE`。

## Affected users and systems
受影响用户为接入 IAM 的业务员及 OAuth Client 管理员。受影响系统包括 Macula Cloud IAM 的授权入口、登录页、
第三方认证状态续转和企业微信/微信公众号网页登录，以及 System、System API、共享数据库迁移和 Admin 的
OAuth Client 配置页面。业务应用仍只接入标准 `/oauth2/authorize` 或已声明的 `external_code`，不在本仓库
集成第三方 JSSDK、原生 SDK 或小程序代码。

## Constraints
必须保留 authorization code + S256 PKCE、精确 `redirect_uri`、一次性 `state`、SavedRequest 和现有账号
绑定语义；User-Agent 只能选择交互路径，不能充当身份或授权依据。第三方入口必须由当前 OAuth Client
显式允许，直接访问 `/login/{provider}` 也不能绕过 Client 策略。旧 Client 默认不允许第三方网页登录，
不得因为升级自动扩权。现有未提交代码和 Flyway 历史不得覆盖或改写；若相关迁移已执行，必须新增后续迁移。

## Open questions
无产品范围问题；具体字段、迁移版本、容器识别优先级和失败回退规则在设计阶段确定并接受安全评审。
