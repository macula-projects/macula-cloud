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

## License

MMacula Cloud Admin is Open Source software released under the Apache 2.0 license.
