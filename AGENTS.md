# AGENTS.md

Macula Cloud 是基于 Macula Boot 6.1、Java 17、Spring Boot 4.0 和 Spring Cloud 2025.1 的多模块微服务应用平台，并包含 Vue 3 + Vite 管理端。修改应保持平台协议兼容、租户与权限边界清晰，并聚焦目标服务或前端功能。

## 开始工作

- 先检查 `git status --short`，不要覆盖、回滚或清理用户已有改动。
- 后端修改前先读目标模块的 `pom.xml`、`README.md`、资源配置、相邻实现和测试；前端修改前先读 `macula-cloud-admin/package.json`、相关路由、API 封装、页面和组件。
- 使用 `rg` / `rg --files` 定位代码，避免无目的遍历整个仓库。
- 根项目继承 `dev.macula.boot:macula-boot-parent`，项目版本由根 `pom.xml` 的 `revision` 管理；不要在子模块重复硬编码项目版本。
- `https://macula.dev` 可作为平台定位和设计规约的参考；版本、模块、依赖、命令和已实现行为以当前仓库为准。

## 常用命令

后端命令在仓库根目录执行，并显式选择环境 profile；`local` 为默认 profile。

```bash
# 目标模块及其依赖（首选）
mvn -pl <module-path> -am test -P<profile>

# system 及其 API 契约
mvn -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system -am test -Plocal

# 全量后端测试
mvn test -Plocal

# 已确认的业务模块打包示例
mvn clean package -DskipTests=true -Pdev -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system
```

前端命令在 `macula-cloud-admin/` 中执行：

```bash
npm ci
npm run dev
npm run build
npm run build:dev
npm run test:unit
npm run test:e2e:ci
npm run lint
```

`npm run lint` 带有 `--fix`，会直接修改文件；执行前后都要检查 diff。后端测试可能依赖 MySQL、Redis、Nacos、RocketMQ、Seata、SnailJob 或其他外部服务，运行前先核对目标模块配置，不要把环境缺失误判为代码失败。

## 全局约束

- 保持 Java 17 兼容，包名沿用 `dev.macula.cloud...`；管理端沿用现有 Vue 3、Vite、Pinia、Vue Router、Element Plus 与 JavaScript 风格。
- 新增 Java 文件使用仓库现有 Apache License 2.0 版权头。新增或实质修改的顶层 Java 类型应有说明职责的类级 Javadoc，并包含非空的 `@author`、`@since`。
- 通用框架能力应进入 Macula Boot；本仓库只承载可部署的平台服务、服务间 API 契约和对应管理端，避免复制 Starter 或框架基础设施。
- `macula-cloud-api` 只放跨服务契约及必要模型、Feign 客户端和回退抽象，不放数据库访问、服务实现或运行时配置。
- Controller 只处理协议适配、校验和服务编排；事务与业务规则放在 Service，数据访问留在 Mapper。延续 Query、Form、DTO、VO、BO、Entity 的现有语义，不直接把 Entity 暴露为新的远程契约。
- 认证、授权、租户、网关路由、缓存权限规则属于跨服务安全契约。修改时必须检查 IAM、Gateway、System、System API 和管理端的调用链，默认拒绝不能确认授权的信息。
- 配置按 `local`、`dev`、`stg`、`pet`、`prd` profile 管理。不得提交真实凭据、令牌、私钥或不可公开的环境地址；生产配置优先由环境变量或配置中心注入。
- API、配置键、数据库结构、远程契约或管理端交互变化时，同步更新相关 README、SQL/迁移材料、调用方和测试。
- 不提交 `target/`、`dist/`、覆盖率、日志、IDE 临时文件或本地环境配置。
- 不主动发布、部署、打 tag、推送或执行会修改远端环境的操作；只有用户明确要求时才进行。

## 按需加载规则

只读取与当前任务相关的规则：

- 理解服务职责、依赖方向或跨模块修改：`.agents/rules/architecture.md`
- 修改 Java 后端、REST API、数据模型或数据库访问：`.agents/rules/backend-development.md`
- 修改 `macula-cloud-admin`：`.agents/rules/frontend-development.md`
- 编写测试或选择验证范围：`.agents/rules/testing.md`
- 修改 POM、依赖、环境配置、部署文件或发布流程：`.agents/rules/dependencies-release.md`

<!-- ai-sdlc:begin -->
## AI-Native SDLC Loop

This repository uses the AI-Native SDLC loop (https://claude.com/blog/the-ai-native-sdlc-playbook).

- Artifacts live in `.sdlc/<slug>/`: intent.md, spec.md, plan.md
- Project-root policy: REVIEW.md (review passes), bands.yaml (control bands)
- No source code is written for a change without an accepted plan.md
- No gate is ever self-approved by the agent
- Six stage skills guide the loop: sdlc-plan -> sdlc-design -> sdlc-build -> sdlc-test -> sdlc-deploy -> sdlc-maintain
- The loop is active for as long as .sdlc/ exists; silence it with .sdlc/OPTOUT
<!-- ai-sdlc:end -->
