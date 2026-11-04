# Spec: 应用 API Key 管理与网关缓存联动 (from intent.md 2026-11-04)
Status: accepted

## Source intent
[intent.md](intent.md) 已接受：应用支持多把 API Key，新增及更新缓存均使用 Key permitUrl 优先、应用 URL 兜底的规则。

## Requirements
1. 应用列表增加 APIKEY 入口，以抽屉提供分页列表、创建、默认脱敏、显示/隐藏、复制、编辑备注及 permitUrl、删除和创建时间展示。
2. 一个应用支持多把 Key；服务端生成随机 sk- 凭据，创建后不可改写凭据或所属应用。
3. 两个 URL 独立存储；新增、更新及重建缓存统一优先取 Key 的非空 permitUrl，否则取应用 accessPath，不合并、不取交集。
4. 所有操作校验应用管理权限、归属租户及 Key 所属应用；列表不返回完整 Key，创建和单独查看响应禁止缓存；日志、审计不保存完整 Key。
5. 事务成功后维护缓存，回滚不发布缓存；删除 Key 或应用后撤销相应凭据，启动可重建有效缓存，并发重建不得复活已删除 Key。

## Non-goals
不新增有效期、启停、轮换、配额或 IP 白名单；不改变 HMAC、OAuth 及网关 URL 算法，不增设两种 URL 的配置绑定规则，不部署或操作真实业务数据库。

## Design
System 新增应用 API Key Controller、Service、Mapper、Entity 及必要 Form/VO，复用现有分页、租户隔离和统一响应。Service 先校验应用，再按应用与 Key ID 约束操作；新增接口纳入现有 URL 权限规则。

Admin 复用 Element Plus 实现管理抽屉、编辑表单及创建成功弹窗。显示、复制按需调用查看接口，关闭抽屉清除完整凭据；处理 loading、失败及重复提交。

使用 SecureRandom 生成至少 256 位熵的 sk- 凭据并建立唯一索引。为支持再次显示及缓存重建，服务端需可恢复保存凭据，沿用现有应用密钥存储边界。

集中维护缓存字段组装；新增、更新、重建共用 URL 优先规则。参考现有租户缓存 afterCommit/刷新锁模式，串行化 Key 更新、撤销和重建，提交后读取数据库；Redis 失败明确报告并允许重试或重建。应用原有缓存刷新如需维护其 Key，也复用相同规则，不修改 Key 的 permitUrl。

## Data and interfaces
- 新增 Flyway V13 和 sys_application_apikey_tenant 表，保存应用关联、租户、唯一凭据、备注、permit_url、审计和逻辑删除字段，沿用既有基表约定；不回填历史 Key。
- GET/POST /api/v1/app/{appId}/api-keys：分页脱敏列表、创建。
- PUT/DELETE /api/v1/app/{appId}/api-keys/{keyId}：更新备注及 permitUrl、删除。空 permitUrl 表示未单独配置。
- GET /api/v1/app/{appId}/api-keys/{keyId}/secret：授权查看。创建和查看响应设置 Cache-Control: no-store，关闭敏感响应审计。
- Redis Hash 使用 APIKEY_CACHE_PREFIX + 完整 Key，写入现有 applicationCode、applicationId、tenantId、authTenantId、permitUrls 字符串字段；认证租户按应用配置，空则回退归属租户。
- URL 使用网关既有逗号分隔 METHOD:/path Ant 模式；两处均为空沿用网关既有“不限制应用 URL”行为。删除认证 Hash 后网关即拒绝旧 Key，无需清理共享 JWT 缓存。

## Flagged concerns
- 再次显示及既有缓存协议要求服务端可恢复保存凭据，Redis key 本身也含凭据；限制存储访问并禁止日志泄漏，数据库字段加密若有额外要求需明确范围，non-blocking。
- 数据库与 Redis 无跨资源原子事务；提交后缓存失败仍可能暂时不一致，须报告并可重试/重建，不宣称强一致，non-blocking。
- 新接口需核对现有角色授权，不能向普通用户默认开放；迁移需先于新版 System 执行，non-blocking。
- 有效期、启停、轮换不在本轮范围，non-blocking。
- 适用 AGENTS.md、architecture/backend/frontend-development 规则和 REVIEW.md；未发现额外品牌或监管政策，无待裁决 blocking concern。

## Verification strategy
后端覆盖多 Key、脱敏/查看、URL 优先与回退、新增更新缓存、删除级联、事务回滚、Redis 失败、并发刷新及跨租户/跨应用越权，断言缓存与网关契约一致、日志审计不泄漏，保留 HMAC 回归。管理端覆盖抽屉及创建/复制/显示/编辑/删除和失败流程，运行相关 Vitest、构建、定向非修复 lint 并检查实际布局。运行相关后端测试；隔离验证数据库迁移及 Redis，区分模拟和真实运行证据。同步 README 与迁移说明。

用户回复“YES”，明确接受本设计及列明风险。
