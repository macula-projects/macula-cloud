# Verification: 邮件和短信发送渠道与模板配置优化

日期：2026-10-18。用户确认合并后验收修复按计划实施完成，本轮重新执行 Stage 4 验证。

## 执行证据

| 命令 | 实际结果 | 退出码 |
| --- | --- | --- |
| `mvn test -Plocal` | 11 个 Maven 模块全部成功，`BUILD SUCCESS`；IAM 49/49、System 43/43、TinyID 33/33，失败/错误/跳过均为 0 | 0 |
| `SYSTEM_SETTINGS_IT=true ... mvn -pl macula-cloud-system -am -Dtest=SettingsPersistenceIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` | 专用 `macula_settings_it_mac3_reviewfix_20261018` MySQL 库执行 V1–V5 后，`SettingsPersistenceIT` 5/5，失败/错误/跳过均为 0，`BUILD SUCCESS` | 0 |
| `npm ci` | 按 lockfile 安装 680 个包；报告既有 60 个依赖漏洞，未自动修改依赖 | 0 |
| `npm run test:unit -- --run` | `Test Files 5 passed (5)`；`Tests 18 passed (18)` | 0 |
| `npm run build` | `✓ 2259 modules transformed.`；生产构建完成，仅有既有大 chunk 提示 | 0 |
| `npm run test:e2e:ci` | `All specs passed!`；4 个 spec、25/25 通过，失败/待定/跳过均为 0 | 0 |
| `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config -q` | Compose 配置有效，无输出 | 0 |
| `git diff --check` | 无输出 | 0 |

首次直接运行 `npm run test:unit` 因当前工作树尚未安装依赖而返回 `vitest: command not found`（退出码 127）；执行仓库规定的 `npm ci` 后，以 `--run` 非 watch 模式重跑并得到上述 18/18 结果。测试专用数据库和 Redis database 14 已在验证后清理，未触碰现有业务数据。

## Proof 与 Verification strategy 对照

- System：固定提供商、场景和语言目录、未知字段拒绝、全局单配置、启停与凭据留空保留、默认/自定义邮件模板、HTML 清洗与变量渲染、供应商请求构造、失败脱敏和管理 API 授权由 43 项测试覆盖；本轮新增完整默认邮件正文的 renderer/service 断言通过。
- 持久化：隔离 MySQL/Redis 的 5 项集成测试证明 V1–V5 结构、凭据密文往返、读取不回显、留空更新保留和缓存失效。按已接受范围，Macula Boot `@CryptoField` 的加密异常失败关闭行为仍不在本变更验证范围。
- IAM：49 项测试覆盖登录短信固定场景、供应商调度、验证码摘要/TTL/限流/失败次数/一次性消费、CSRF 与认证拒绝路径。
- Admin：18 项 Vitest 与 25 项 Cypress 覆盖三个页签、固定提供商/场景、首次启用短信立即加载当前语言模板、8 个邮件场景各自的配置/浏览入口、带转义主题且使用服务端标准样例变量的邮件卡片预览、短信模板请求竞态隔离、加载失败清空与 loading/error/empty/save 状态，以及被移除能力不出现；生产构建同时通过。
- 仓库与部署配置：Compose 展开校验和 `git diff --check` 均通过。功能提交及本轮审查修复没有修改 `CLAUDE.md`、任何 Skill 或 hook，因此没有适用的配置行为 eval；工作树中的 `AGENTS.md` 与 `.agent_context/` 是既有平台/用户改动，不属于本次交付。

## 验证边界

- Cypress 使用前端拦截模拟 API，不代表真实供应商联调。
- 未提供阿里云/腾讯云短信及 SMTP 专用测试凭据和审核模板，因此没有执行真实供应商受理/终端送达验证；这仍是 spec 已记录的环境依赖边界。
- `npm ci` 报告 `60 vulnerabilities (4 low, 19 moderate, 30 high, 7 critical)`；本轮未修改 `package.json` 或 lockfile，也未执行可能引入破坏性升级的自动修复。
- 当前验证对象为 `feat/MAC-3` 上、已合并 PR #22 之后的 5 个验收修复提交；尚未再次交付到 `main`。

第五轮独立审查修复后的正式验证已覆盖邮件主题展示、服务端标准样例变量，以及短信供应商/语言请求竞态与失败隔离；所有必需命令均成功。

Verification is green
