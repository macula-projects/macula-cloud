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

## License

MMacula Cloud Admin is Open Source software released under the Apache 2.0 license.
