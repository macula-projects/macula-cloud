# feat(system): 应用 API Key 管理与接口日期规范化

应用管理新增 API Key 抽屉，支持一个应用维护多把凭据，提供创建、脱敏分页、授权显示/复制、备注及访问路径编辑、删除。字段沿用应用的 `access_path/accessPath`。

缓存新增、更新、重建均优先使用 Key 的访问路径，未配置时使用应用路径，两者不合并或取交集。复用网关现有认证 Hash 协议；事务提交后同步，删除应用级联撤销，启动时恢复有效缓存并清理撤销记录。完整凭据响应设置 `no-store` 并关闭敏感内容审计。

授权复用可信租户解析，再检查管理角色、应用及 Key 归属。创建 Key 与删除应用共用应用行锁；提交后缓存刷新复用连接，避免有限连接池再次借连接。

接口时间点使用 `Date` 或 `Instant`，数据库 Entity/BO 保留 `LocalDateTime`，通过 Boot 公共转换器映射。JSON 使用标准带时区 ISO，兼容 Instant 可变小数精度；前端按浏览器当地时间显示。详情接口使用独立 VO，写入表单隔离审计字段，用户导出先经过数据库 BO 转换并保留 Excel 格式。同步修正应用表单、选择组件和 API Key 列表布局。

本地 Compose 的 Java 应用统一上海时区，暂不配置 `macula.time.storage-zone`；System/TinyID JSON 继续输出 UTC。调整已有环境前需核对历史 DATETIME 写入语义，本次未转换历史数据。

## 验证

- 日期统一修复后全仓后端：419 条目，418 执行通过、1 既有条件跳过；最终 Date/Instant 增量复验 System/API/TinyID：175 条目，174 通过、1 跳过。
- 原 API Key 功能真实隔离 MySQL/Redis 集成 6 项通过，包含迁移、URL 优先规则、事务回滚、撤销/重建、并发及 Hikari 单连接池。日期修复后未重新运行这组需显式启用的 IT。
- 前端全量单元 93 项、Cypress 34 项通过；最终 Date/Instant 相关单元 19 项、浏览器 3 项通过。浏览器业务接口使用模拟响应。
- 前端构建、目标后端打包、Java Checkstyle、定向 ESLint、diff 检查通过。
- 用户已验收本地更新：9 个应用镜像构建并重建，全部健康；Admin/IAM 返回 200，System/Gateway 无令牌访问返回预期 401。运行中的 System/TinyID JAR 已确认包含新日期转换器；未验证真实登录后的全部业务流程或生产数据库升级。

## 构建与迁移前提

- 先执行 V13 迁移，再运行新版 System。
- 需要包含公共日期转换器和 Mapper 配置的 Boot 6.1 快照；对应 Boot main 提交为 `c30ff533d0044dd95ac585253111815702490be5`。本地及容器构建使用已安装构件，本 PR 不发布 Boot 构件；其他构建环境需确保仓库已提供该快照或先构建安装对应 Boot。
- Key 支持授权再次查看，数据库可恢复保存凭据；本轮不包含有效期、启停、轮换或独立密钥加密管理。
- 数据库与 Redis 不具备跨资源原子性，缓存失败明确报错并支持重试/重建；错误不表示撤销已完成，详见 System README。

## 审查与设计依据

依据 `REVIEW.md` Standard 完成 Bugs、Security、Compliance 审查。前两轮可信租户及有限连接池问题已修复并独立复核；第三轮补审日期、UI、部署配置增量，无新增 Important/Minor/Nit，posted0、suppressed3（已关闭问题2、证据不足候选1）、verifier-rejected0。新增重复类别0，既有类别已由规则及回归测试约束。PR 创建后等待人工审查，不自动合并。

- [需求](https://github.com/macula-projects/macula-cloud/blob/feat/application-apikey-management/.sdlc/application-apikey-management/intent.md)
- [设计](https://github.com/macula-projects/macula-cloud/blob/feat/application-apikey-management/.sdlc/application-apikey-management/spec.md)
- [实施计划](https://github.com/macula-projects/macula-cloud/blob/feat/application-apikey-management/.sdlc/application-apikey-management/plan.md)
- [验证报告](https://github.com/macula-projects/macula-cloud/blob/feat/application-apikey-management/.sdlc/application-apikey-management/test-report.md)
- [审查记录](https://github.com/macula-projects/macula-cloud/blob/feat/application-apikey-management/.sdlc/application-apikey-management/review-findings.md)
