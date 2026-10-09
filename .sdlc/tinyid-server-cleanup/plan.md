# Plan: TinyID Server 遗留清理 (from spec.md 2026-10-09)
Status: accepted.

## Files that change
路径相对 Cloud 仓库；Java 名称相对 TinyID 主包 dev/macula/cloud/tinyid，测试对应同名包。
- 删除 service/TinyIdTokenService.java、service/impl/TinyIdTokenServiceImpl.java、mapper/TinyIdTokenMapper.java、pojo/entity/TinyIdToken.java、resources/mapper/TinyIdTokenMapper.xml 及 TinyIdTokenServiceImplTest。
- 删除 pojo/form/{AddApplicationBusinessesForm,CreateApplicationForm,UpdateApplicationRemarkForm}.java、pojo/query/ApplicationPageQuery.java、pojo/bo/TinyIdApplicationBO.java、pojo/vo/TinyIdApplicationVO.java。
- 修改 controller/TinyIdAdminController.java、service/TinyIdManagementService.java、service/impl/TinyIdManagementServiceImpl.java、converter/TinyIdManagementConverter.java，移除应用授权及关联补偿代码。
- 删除 config/TinyIdHttpExceptionAdvice.java；修改 service/impl/TinyIdIssuingServiceImpl.java、pojo/vo/ErrorCode.java，移除专属 HTTP 异常依赖与失效 Token 错误码。
- 修改 macula-cloud-tinyid/src/main/resources/db/migration/V1__baseline.sql。
- 删除 Admin views/tinyid/management 下 ApplicationPanel、ApplicationCreateDialog、ApplicationAuthorizationDialog、ApplicationRemarkDialog 四个 Vue 文件；修改 index.vue、src/api/model/tinyid/management.js。
- 修改 TinyIdManagementServiceImplTest、TinyIdManagementServiceIntegrationTest、TinyIdAdminControllerTest、TinyIdIssuingServiceImplTest、TinyIdExceptionIT、TinyIdSegmentSecurityIT；其余保留测试如有 Token 专属引用同步清理。
- 修改 Gateway src/main/resources/application.yml 和 TinyIdGatewayIT。
- 修改 Admin tests/unit/tinyid-management.test.js、cypress/e2e/tinyid-management.cy.js；更新 TinyID/Gateway/Admin README 和本任务记录。

## Order of work
1. 在 feat/tinyid-server-cleanup 独立工作树实施；先读模块 POM、README、相邻实现和测试，复用现有库与测试工具，不新增依赖。
2. 删除 Token 全链路及应用管理契约；简化业务删除，但保留数据源顺序、业务删除限制、跨库补偿和审计。
3. 直接清理 V1 的 Token 表相关 SQL，不增加迁移版本、不修改真实库或历史发号数据。
4. 删除专属 Advice；空 bizType 改为共享处理器已支持的 IllegalArgumentException，保持业务异常 ResultCode 和成功协议。网关改用 /tinyid/**，保留 StripPrefix 与认证。
5. 删除前端接入应用 UI/API，保留业务与审计；同步测试、文档和迁移警告。历史审计记录及显示保持。
6. 静态核对引用、差异与范围，记录偏差，展示实施结果供确认；确认后进入正式测试，不自动合并或部署。

## Risks
- 改 V1 会导致已应用环境校验差异，旧表不自动消失；仅修改源码，不运行 repair、重置或真实数据删除。
- 旧 /apps 契约删除，MVC 错误状态遵循共享 Advice，不保留专属 400/405 保证。
- /tinyid/** 覆盖范围扩大，不能增加匿名放行；ROOT 管理权限不变。
- 授权关联删除与业务补偿交织，必须保留已使用业务不可删除、失败恢复和审计。
- 原 Cloud 工作区的网关和 IAM 改动不纳入提交、不覆盖；不修改 Boot 或 IAM。

## Proof
- 静态引用检查确认无运行期 Token、应用授权入口或专属 Advice 残留；不机械清除历史文档与审计。
- Service/数据库测试覆盖业务创建、全库分页、一致性、删除限制、跨库补偿与审计；全新数据库 V1 无 tiny_id_token。
- Web/Security 测试覆盖业务数据、统一 Result、空参数、业务异常、旧 /apps 不再映射及 ROOT/匿名边界。
- Gateway 测试覆盖通配路径、StripPrefix、认证允许/拒绝、过期凭据与下游失败，不新增个人 Token 专属限制。
- 前端 Vitest/Cypress 验证业务与审计入口、空态、loading、成功及失败状态，无应用页签及请求；npm build 验证引用完整。
- 正式测试阶段：目标 TinyID/Gateway verify -Plocal，再 Cloud 全仓 verify -Plocal；执行 Admin 单测、构建和 Cypress。外部环境缺失明确报告，模拟证据不作真实 E2E。

## Implementation notes
- 补充修改 `macula-cloud-admin/src/views/tinyid/management/BusinessPanel.vue`：移除业务提示和删除确认中的旧应用授权表述，不改变业务操作行为；纳入前端回归范围，无新增迁移风险。
- 仓库不存在 `CLAUDE.md`，遵循现有 `AGENTS.md` 和已读取的相关规则。
- 实施阶段只做静态引用与差异检查；尚未执行 Maven、Vitest、Cypress 或前端构建，正式验证等待实施确认。
- 仅清理 V1 源文件，未连接、迁移或清理真实数据库；已删除源码仍可通过 Git 历史恢复。
