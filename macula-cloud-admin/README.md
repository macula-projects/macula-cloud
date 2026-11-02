# Macula Cloud Admin

## 作用

Macula Cloud Admin 是 Macula Cloud 的管理控制台，基于 Vue 3、Vite、Pinia、Vue Router 和 Element Plus，提供租户、应用、用户、角色、菜单及平台配置等管理界面。

本地开发时，浏览器通过 `/api` 访问 Gateway；默认代理目标为 `http://127.0.0.1:9000`，IAM 默认地址为 `http://127.0.0.1:9010`。

## 启动

前置条件：

- Node.js（版本要求见 `package.json`）
- npm
- 已启动 Macula Cloud Gateway 和 IAM；后端启动方式见 [项目根 README](../README.md)

```bash
cd macula-cloud-admin
npm ci
npm run dev
```

开发服务器默认访问地址为 <http://127.0.0.1:5900>。如需调整 Gateway、IAM 或登录配置，请修改 `.env` 和对应环境文件。

构建并预览生产产物：

```bash
npm run build
npm run preview
```

预览服务默认访问地址为 <http://127.0.0.1:4173>。
