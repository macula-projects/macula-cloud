<h2 align="center">Macula Cloud Admin</h2>

<p align="center">
	<strong>基于Vuejs 3.x和Element Plus的Macula Cloud控制台</strong>
</p>

<p align="center">
    <a href="https://github.com/macula-projects/macula-cloud-admin/blob/main/LICENSE" target="_blank">
        <img src="https://img.shields.io/github/license/macula-projects/macula-cloud-admin.svg" >
    </a>
    <a>
         <img src="https://img.shields.io/npm/v/element-plus.svg" />
    </a>
    <a>
        <img src="https://img.shields.io/badge/node-%20%3E%3D%2016-47c219" >
    </a>
	<a href="https://v3.vuejs.org/" target="_blank">
		<img src="https://img.shields.io/badge/VueCLI-5-green" alt="VueCLI">
	</a>
	<a href="https://v3.vuejs.org/" target="_blank">
		<img src="https://img.shields.io/badge/Vue.js-3.x-green" alt="Vue">
	</a>
</p>


## 介绍

基于Vuejs 3.x和Element Plus的Macula Cloud控制台，本身基于[SCUI](https://gitee.com/lolicode/scui)改造，引入Vite和Pinia。

注：
> SCUI 是一个中后台前端解决方案，基于VUE3和elementPlus实现。 使用最新的前端技术栈，提供各类实用的组件方便在业务开发时的调用，并且持续性的提供丰富的业务模板帮助你快速搭建企业级中后台前端任务。

## 身份提供商、消息与 IAM 接入应用管理

System 的 V2 迁移为超级管理员添加身份提供商、消息设置及 IAM 接入应用菜单。
前端使用现有动态路由和 `src/api/model/system` 请求封装。V2 默认只为 ROOT 关联菜单和 URL 权限；
后续可通过系统权限配置授权其他角色。菜单隐藏不等于授权，管理 API 由 Gateway 的 URL 权限控制，
System Controller 不额外硬编码 ROOT；部署时须防止客户端绕过 Gateway 直连 System。

身份提供商在本轮仅管理配置，尚未接入 IAM 第三方登录流程。
身份提供商页面按社交（GitHub、支付宝、微信）和企业（企业微信、飞书、钉钉）分组；各类型仅显示
相应的配置字段。分类、类型 code 和字段规则从 System 元数据接口 `/api/v1/identity-providers/catalog`
读取，页面不维护第二份提供商清单；元数据加载失败时可重试。接口传输对象型 `config`，不是让管理员填写原始 JSON；私钥/Secret 不回显，
编辑留空保留已配置值。System 的 V3 迁移增加备注并扩容加密凭据列，升级与回滚要求见 System README。
创建/修改使用右侧 Drawer，随所选提供商展示官方开放平台入口、创建路径及每个字段的获取位置提示；
这些说明来自 System 元数据。第三方平台可能调整菜单，实际创建时以其官方后台为准。
身份源列表沿用角色管理的紧凑顶栏和 `scTable`，表格填满分类栏下方空间、分页停靠底部；
六种品牌图标随 Admin 构建打包，不在浏览器运行时
请求第三方站点。图标分别取自 GitHub、支付宝、微信开放平台、企业微信、飞书、钉钉官网的 favicon；
相应品牌标识归各权利人所有，仅用于识别对应接入服务。
身份源表单以只读文本展示回调地址，不提交此字段。页面使用 System 返回的
`/login/{providerType 小写}` 路径和 `IAM_URL` 对外基址组成完整 URL，供第三方平台配置；
未来 IAM `authentication` 包的 LoginFilter
必须采用相同路径。当前回调 Filter 尚未接入，展示地址不能用于实际登录。
消息设置使用“邮件模板 / 邮件服务 / 短信服务”三个页签。邮件模板固定八个场景，支持启停
自定义模板、发件人显示名、主题、HTML 内容和 sandbox 预览；邮件服务支持自定义 SMTP、阿里云
企业邮、腾讯云企业邮、网易企业邮。短信服务支持阿里云、腾讯云和九个固定场景，模板编码按
中文与 English 分开保存，并可逐场景测试发送。七牛云、自定义短信和 Exchange 独立类型不展示。
测试返回的是供应商提交状态，不是最终收件证明。凭据不回显，原服务商编辑时留空保留原值；
切换服务商必须重新输入凭据。
OAuth 接入应用的创建/编辑使用右侧 Drawer，需选择所属租户；公共客户端启用 PKCE，客户端配置修改不撤销现有令牌。

## 租户安全策略

系统管理使用 `/system/security-policy` 单一入口，页面以“通用安全 / 防御策略 / 密码策略”三个
Tab 管理当前认证租户的配置。三个 Tab 独立校验和保存；读取失败时保存按钮保持禁用，可重新加载，
避免用默认值覆盖已有设置。请求不提交 `tenantId`。旧 `/system/security-settings` 和
`/system/password-policy` 入口已删除。页面提供中英文文案，并始终提示配置当前仅保存，尚未接入
IAM/Gateway 运行时。字段、单位及 API 详见 `../docs/security-policy.md`。

## 安装说明
```sh
# 克隆项目
git clone https://github.com/macula-projects/macula-cloud-admin

# 进入项目目录
cd macula-cloud-admin

# 安装依赖
npm i

# 启动项目(开发模式)
npm run dev
```

## 环境模式

浏览器通过同源路径 `/api` 访问 Gateway，通过 `/iam` 访问 IAM。开发服务器和容器 Nginx 分别代理这两个前缀，避免浏览器跨域并保持各环境访问方式一致：

```sh
# local：Vite 开发服务器代理到本机 Gateway/IAM
npm run dev

# Docker Compose、开发、预发、性能测试、生产构建
npm run build:docker
npm run build:dev
npm run build:stg
npm run build:pet
npm run build:prd
```

本地 Vite 默认把 `/api` 代理到 `127.0.0.1:9000`、把 `/iam` 代理到 `127.0.0.1:9010`，可分别通过 `VITE_APP_GATEWAY_PROXY_TARGET`、`VITE_APP_IAM_PROXY_TARGET` 覆盖。Docker 镜像内置 Nginx 将 `/api`、`/iam` 代理到 Compose 中的 Gateway、IAM 服务。共享环境的入口代理也应提供这两个同源前缀；如部署拓扑确实不同，可在构建产物的 `config.js` 中覆盖 `API_URL`、`IAM_URL`。

## 登录配置

OAuth public client 和本地示例账号由以下配置项提供：

```dotenv
VITE_APP_OAUTH_CLIENT_ID=e4da4a32-592b-46f0-ae1d-784310e88423
VITE_APP_OAUTH_CLIENT_SECRET=secret
VITE_APP_OAUTH_SCOPE=message.read message.write userinfo
VITE_APP_DEMO_USERNAME=admin
VITE_APP_DEMO_PASSWORD=admin
```

通过 IDE 或 `npm run dev` 启动时，Vite 自动加载根 `.env`，无需读取 `deploy/.env`。覆盖优先级为根 `.env`、当前 mode 的 `.env.<mode>`、启动命令的进程环境，后者优先级最高。例如：

```sh
VITE_APP_DEMO_USERNAME=local-admin npm run dev
VITE_APP_OAUTH_CLIENT_ID=dev-client npm run build:dev
```

Docker 镜像构建时仍使用 Vite 配置生成静态资源；容器启动时，`deploy/docker-compose.yml` 把同名变量传入 Admin 容器，entrypoint 据此重新生成 `config.js`。运行时 `APP_CONFIG` 对以上五项具有最终优先级，因此修改部署变量后只需重建容器，不需要重新构建镜像。`/api` 和 `/iam` 仍由 Nginx 分别代理到 Compose 内的 Gateway 和 IAM，不使用这些登录变量改变服务地址。

> `client_secret`、示例用户名和密码最终都会发送到浏览器，Base64 只用于安全生成 JavaScript，并不提供加密或保密能力。这里仅允许使用 public/demo client 和本地示例账号；不得把生产 confidential client secret 或真实账号密码放入前端环境变量、构建产物或 `config.js`。

## TinyID 管理接口

仅保留发号业务（含数据源配置状态）及审计日志；接入应用、Token 和业务授权管理已移除，访问权限由统一网关管理。

TinyID 管理接口与 system 使用相同的 Result 结构：先检查 `success`，再读取 `data`；业务失败展示 `cause/msg`。
保存或删除失败时不关闭表单、不显示成功。该响应升级需要同步部署 TinyID Server 与管理前端。
相关回归用例位于 `tests/unit/tinyid-management.test.js` 和 `cypress/e2e/tinyid-management.cy.js`；后者使用模拟接口，不代表真实后端联调。

## License

MMacula Cloud Admin is Open Source software released under the Apache 2.0 license.
