# feat(tinyid): 新增 ROOT 接入应用与跨库发号业务管理

## 变更

为 ROOT 超级管理员提供接入应用、发号业务与审计管理页面及 API，复用现有 TinyID 发号协议。应用 Token 由服务端生成，可查看、追加业务授权及整体删除；业务按全部物理数据源创建，只有各库均未发号时才能删除。

- 管理访问使用平台认证和 ROOT 方法授权，Gateway 仅代理管理接口；System 通过前向迁移发布菜单和权限。
- 数据访问统一使用 MyBatis-Plus Mapper，管理操作按物理数据源下标显式路由，保留逐库预检、本地事务及失败补偿。
- 发号业务列表取全部物理库的业务名称并集，去重排序后分页。例如 sequence=0 缺少某业务、sequence=1 存在时，列表仍显示该业务及 PENDING 状态。
- 标准 AuditLog 事件异步写入 sequence=0；关闭请求和响应正文采集，删除重复的请求日志 Filter。
- 多库集成测试由 Testcontainers 创建两套隔离 MySQL，无需手工提供数据库 URL。

## 设计依据

- [Intent](./intent.md)
- [Spec](./spec.md)
- [Plan 与已确认偏差](./plan.md)

## 验证证据与边界

本会话已执行后端 Maven 测试、前端 Vitest、生产构建及 Cypress。最近的 TinyID 集成测试报告原文：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

- `mvn -pl macula-cloud-tinyid -am test -Plocal`：模块回归通过。
- `mvn test -Plocal`：已执行全 reactor 回归；历史对话已报告通过，提交前应保留完整命令退出状态及最终汇总供 CI 复核。
- `npm run test:unit -- --run`：`Test Files 2 passed (2)`、`Tests 8 passed (8)`。
- `npm run build`：成功，存在 chunk size 提示。
- `npm run test:e2e:ci`：已执行浏览器交互用例。用例模拟后端响应，并注入测试用户与菜单；不证明真实登录、Gateway 权限、动态菜单和数据库的全链路联通。用户已确认保持此测试方式。
- `mvn -pl macula-cloud-tinyid -am package -DskipTests -Plocal`：`BUILD SUCCESS`，该命令仅作为打包证据。
- `git diff --check`：通过。

本功能未修改 CLAUDE.md、Skill 或 Hook；未发现适用 eval suite。未以本次结果声明部署环境或生产控制带健康。

## 审查结果

按根 REVIEW.md 执行 Bugs、Security、Compliance 独立审查。原有两项发现已经修复：跨库列表遗漏，以及应用查询注释误称支持 Token 搜索。修复范围的独立复核未发现新增 Important 或 Nit。此结果不代替人工批准。

## 部署约束

- 已有物理数据源顺序不得重排，扩容只能末尾追加；新库投入发号前由部署流程完成迁移和历史数据初始化。
- 跨库写入不提供分布式原子性，补偿失败需要人工修复。
- 管理请求实例即时更新 Token 缓存；其他实例通过既有定时刷新收敛。
- 审计异步尽力落库，鉴权前被拒绝的请求可能不产生 Controller 审计事件。

## 提交范围

分支：`feat/tinyid-application-management`。仅提交 TinyID 功能及相关 Gateway、System、管理端和 SDLC 材料；排除 `macula-cloud-admin/.env` 与生成产物。提交使用工作树最终内容，包含新增 Mapper、迁移和集成测试，并移除已废弃的 DAO 中间文件。

状态：本地 PR 草稿，尚未创建远端 PR、推送或合并；等待人工审阅。
