# 依赖、配置、部署与发布

修改 POM、前端依赖、环境配置、部署文件或发布流程时加载。

## Maven 与版本

- 根项目继承 `dev.macula.boot:macula-boot-parent`，Macula Boot 及大部分第三方版本由父 POM 管理；先在 Macula Boot 的 dependency management 中查找，不在子模块随意重复版本。
- 根版本使用 `${revision}`，内部模块依赖也使用 `${revision}`；不要在子模块硬编码当前项目版本。
- 新依赖放在实际使用它的最窄模块，避免根 POM 或 API 模块污染所有服务的 classpath。
- 升级 Macula Boot、Spring Boot、Spring Cloud、Spring Cloud Alibaba、Security、Seata 或 SnailJob 时核对兼容矩阵，并对所有受影响服务做编译、测试和启动验证。
- POM 变化至少运行目标模块的 `-am test` 与 `package`；父 POM、插件、资源过滤或 profile 变化应扩大到整个 reactor。

## 前端依赖

- 依赖声明和 `package-lock.json` 必须同步；普通构建使用 `npm ci` 验证锁文件可重现。
- 新依赖前先确认现有 Vue、Element Plus 或工具库不能满足需求，并评估体积、许可证、维护状态和浏览器兼容性。
- 依赖升级后运行 lint、相关单测和生产构建；Vite、Vue、Router、Pinia 或 Element Plus 升级还要做关键页面冒烟验证。

## 环境配置

- Maven profile 为 `local`、`dev`、`stg`、`pet`、`prd`，`local` 默认启用；构建和报告中应明确实际 profile。
- `bootstrap.yml` 中的 `@profile.active@` 依赖 Maven 资源过滤。改名或改 profile 时检查所有服务的引导配置和打包产物。
- Nacos namespace 用于平台或业务线隔离，环境连接信息按 profile 或外部注入管理；不要把个人、内网或生产配置写成通用默认值。
- 密码、token、AK/SK、证书和私钥通过环境变量、密钥管理或配置中心注入，不提交真实值。示例值必须明确仅用于本地开发。
- 修改配置键时同步搜索 Java 绑定、YAML、Nacos 配置说明、部署清单和 README，并提供兼容别名或迁移说明。

## 部署

- 修改 `deploy/docker-compose.yml` 或 `deploy/k8s.yml` 前先核对镜像、端口、依赖服务、健康检查、卷、网络、资源限制和配置来源。
- 不在未验证的情况下把本地地址、空密码或示例账号推广到共享及生产环境。
- 数据库、Nacos、Redis、RocketMQ、Seata 和 SnailJob 的启动顺序与初始化数据属于部署契约，变化时同步文档和可重复初始化步骤。
- 仅修改部署文件不代表应用已验证；至少做语法/渲染检查，并在条件允许时验证受影响服务启动。

## 发布安全

- 除非用户明确要求，否则不发布镜像、部署环境、创建 release、打 tag 或推送远端。
- 发布前确认版本、目标分支、远端、凭据、数据库兼容性、配置迁移和工作区状态；不得夹带用户的无关改动。
- `-DskipTests=true` 只证明跳过测试后的打包结果，不能作为发布质量证据。
- 发布或部署完成后报告生成的版本、commit/tag、镜像、目标环境和实际验证结果。
