# Intent: Admin OAuth 登录配置外置化
Author: Rain. Status: accepted.

## Problem
Macula Cloud Admin 的密码登录表单把 OAuth client ID、client secret、scope 以及示例用户名和密码直接写在组件代码中。当前根 `.env` 与各 mode 配置只能调整 API/IAM 地址，无法改变实际登录参数；Docker Compose 的运行环境配置也不能覆盖这些值。开发者和部署人员因此必须修改源码并重新构建才能切换合法的 OAuth client 或本地示例账号，且容易造成不同环境误用同一组配置。

## Proposed outcome
开发者可以通过 Admin 的公共默认配置和各 mode 覆盖配置启动本地开发环境，无需修改登录组件；部署人员可以通过 Docker Compose 的环境配置在容器启动时覆盖 OAuth client、scope 和示例账号，无需重新构建 Admin 镜像。现有默认登录值、IAM token 请求以及登录后的租户、用户和菜单流程保持兼容。

## Affected users and systems
受影响用户包括本地开发者、Macula Cloud 部署与运维人员，以及维护 Admin 登录流程的前端开发者。受影响系统包括 `macula-cloud-admin` 的环境配置、登录表单、运行时配置、Docker 镜像和 `deploy/docker-compose.yml`；IAM、Gateway、System 的接口协议和服务端实现不应改变。

## Constraints
- 参考 Macula Boot Archetype Admin 已采用的配置边界，同时保留 Macula Cloud Admin 自身 `/api`、`/iam` 同源代理拓扑。
- OAuth client ID、client secret、scope、示例用户名和密码都必须支持配置，不再写死在 Vue 组件中。
- 根 `.env` 提供当前本地 demo 默认值，各 `.env.<mode>` 可以覆盖；项目现有 mode 和启动命令保持兼容。
- Docker Compose 必须支持容器启动时覆盖相关配置，修改部署 `.env` 不应要求重新构建 Admin 镜像。
- 保持当前默认值以及登录后的租户、用户、菜单处理逻辑不变，不扩大到认证协议或页面功能重构。
- 浏览器中的 client secret 只能作为 public/demo client 的兼容值，不得提交或注入生产机密。
- 变更必须位于独立分支，不混入现有 `fix/nacos-compose-env` 工作树及其未提交修改。

## Open questions
无。
