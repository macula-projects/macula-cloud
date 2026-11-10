# Plan: IAM Client 授权方式勾选与认证接入辅助 (from spec.md 2026-11-05)
Status: accepted

## Files that change
- `.sdlc/iam-client-grant-selection/intent.md`、`spec.md`、`plan.md`、`review-findings.md`：记录人类接受证据、计划和评审。
- `macula-cloud-admin/src/utils/oauthClientConfiguration.js`（新增）：grant/认证目录、集合兼容性和依赖判断、密钥生成、Basic 编码、shell 安全引用及示例生成。
- `macula-cloud-admin/src/views/system/oauth2Client/index.vue`：中文认证选项、grant 勾选联动、敏感内存状态、密钥生成/重置抽屉、请求示例、JWKS 条件字段及窄屏布局。
- `macula-cloud-admin/tests/unit/oauth-client-configuration.test.js`（新增）：兼容矩阵、legacy 集合、编码和示例、安全随机失败测试。
- `macula-cloud-admin/tests/unit/admin-identity-security-settings.test.js`：调整与旧原始输入布局绑定的断言，保留现有目录/路由测试。
- `macula-cloud-admin/cypress/e2e/admin-identity-security-settings.cy.js`：将原始 grant 输入用例改为勾选交互，并覆盖 legacy、未知值和原页面回归。
- `macula-cloud-admin/cypress/e2e/oauth-client-configuration.cy.js`（新增）：认证切换、密钥生成/重置、示例复制、关闭清除及 JWKS 交互。
- `macula-cloud-admin/README.md`、`macula-cloud-iam/README.md`：记录配置含义、产品限制、凭据生命周期与接入说明。

## Order of work
1. 新增纯函数辅助模块，复用既有 API 模型导出的授权码及 external_code 标识，不更改后端。定义完整七种 grant 和四种认证方式。none 禁止 client_credentials；其余认证禁止新增 sms/email/external_code。password 保留历史兼容说明。
2. 在辅助模块实现集合快照比较：只有原始机密验证码 Client 且 auth/grant 集合完全不变时允许旧 sms/email；多个认证方式原样保留并展示，不在打开表单时自动替换。
3. grant 多选通过 computed 与 CSV 双向映射；不让既有 normalizeTerminalLoginConfiguration 清除旧 grant/未知值，修改 open() 仅复制值并保留原始快照。未知 grant 可显示和显式移除，保留时拦截保存。external_code 已选类型时不可直接取消；browser provider 存在时不可直接取消 authorization_code，显示依赖。切换认证方式后已选冲突项保持可取消，未选非法项禁用。
4. 替换认证选项文案，添加认证用途说明。JWKS URL 和 RSA 算法仅在 private_key_jwt（含旧多值）时展示，保留已有字段值而不提交私钥。none 开启 PKCE 并清除密钥。
5. 使用 Web Crypto 生成 32 字节 URL-safe Secret；新建表单支持显示/复制/生成，编辑已有记录通过专用“临时密钥”输入生成示例，保存更新请求必须剔除临时值。用专用重置抽屉替换 prompt，包含生成、复制、重置提交、成功态；调用既有 resetSecret API，失败保持当前输入可重试，不自动生成或提交第二次。
6. 请求示例独立展示授权入口与 Token 请求，使用可编辑 IAM 基地址和 JWT audience（有明确占位提示），默认基地址取现有 config.IAM_URL；不把该配置自动当作已验证的 issuer/aud。基本认证正确表单编码再 Base64；POST 参数经 shell 单引号转义并使用 curl --data-urlencode。none 给出可在客户端执行的 S256 PKCE 生成与交换示例；各 grant 使用对应必需参数，缺少用户 code/token 使用明显占位符。private_key_jwt 提供 Node.js 原生 crypto 签名脚本及 client_assertion 表单示例，使用本地私钥文件、显式 audience、短期 exp、随机 jti 和 kid，不收集私钥。
7. 复制使用 Clipboard API，异常时提示选中示例手动复制。默认遮蔽含真实密钥的示例，显式显示/复制后才使用；关闭抽屉（包括取消和保存）、切换 Client、切换非密钥方式时同步清除 Secret、临时 Secret、重置状态和派生示例，隐藏状态也恢复。示例不存储到 local/sessionStorage，不写日志。
8. 更新 README，编写目标 Vitest/Cypress。执行目标/完整 Vitest、变更文件 ESLint、生产构建、OAuth Client 目标 Cypress，并通过桌面/390px 窄屏检查交互及示例横向溢出边界。正式 Stage 4 运行与证据报告按 sdlc-test 处理。

## Risks
- 旧多认证 Client 的 single select 无对应普通值：添加当前旧组合只读回显选项，用户显式选择标准认证才改变，切换后失去 legacy 例外时立即标记。
- 当前登录能力自动派生 grant 与新复选框可能相互覆盖：统一以 form CSV 为源，操作后重新计算依赖和兼容性；禁止以清理未知值的 normalize 函数初始化编辑数据。
- 前端支持与后端策略漂移：不扩大后端能力，组合测试按当前 service/converter 核对；未知值只保留显示并阻止保存。
- 原始 Secret 和 Basic 请求头均为敏感凭据：仅组件内存，关闭及时清理；失败、复制失败、切换及多次打开均覆盖生命周期。
- 示例基地址和 JWT aud 可能与真实 issuer 不同：显式填写并说明由部署配置确认，不声称示例默认 audience 已通过运行时验证。
- 现有测试包含文本输入选择器，需改为用户可观察勾选及提交 CSV，不仅替换字符串断言。
- 无后端、依赖或数据库变更；浏览器 Mock 测试不代表真实 IAM/第三方平台验证；不提交、不部署或修改真实 Client。

## Proof
- Vitest：四种 auth × 七种 grant、legacy auth/grant 顺序变化等价/增删失效、未知值无损、自动依赖、Basic 空格/冒号/Unicode、shell 单引号/换行/$/反引号、各 grant 示例参数以及安全随机不可用/失败。
- Cypress：公共/Basic/Post/私钥切换、非法项不可新增且已选可移除、依赖和 Origin 现有校验、未知值显式清理、旧多认证验证码集合原样提交；生成 Secret 符合长度且成功创建；reset 成功/失败/重试；关闭/切换后密钥及派生结果清除；复制失败手动选取；JWKS 字段和算法按认证展示，私钥输入不出现。
- 完整 Vitest、build、两份 OAuth Client Cypress、定向 ESLint、git diff --check。桌面与 390px 视口验证抽屉内控件可操作、长代码仅在示例容器内滚动。

2026-11-05：Rain 以“YES”明确接受本实施计划和 Plan 评审结果，进入编码。

## Implementation notes and deviations
- 2026-11-05：完成辅助模块、页面、两份 Cypress、单元测试及 Admin/IAM README；没有后端、数据库或依赖变更。仓库不存在 CLAUDE.md，按 AGENTS.md 与按需规则执行。
- 保留旧 grant/认证值时发现原始初始化函数还会清除失效 external code：改为显示失效类型，并由用户点击“移除失效凭证类型”明确清理；对应既有回归改为显式操作，符合不静默丢失配置的约束。
- JWT audience 在客户端签名脚本中显式使用 JWT_AUDIENCE 环境变量，而不在管理表单另增输入框；示例说明由实际 IAM 部署确认，避免管理界面误传为持久化字段。
- 含原始密钥的请求示例使用即时函数生成，避免 computed 缓存保留旧凭据；主抽屉 close 清理全部临时值，重置提交中阻止关闭，成功才显示生效状态。
- 浏览器回归定位并修复：legacy 仅允许原有 sms/email，不允许新增另一验证码 grant；抽屉关闭时清理高级面板展开状态；示例函数正确渲染；窄屏终端分段控件撑宽抽屉，改为 600px 以下下拉选择，桌面继续分段选择。
- 所有实际修改均在原声明文件集合内。未提交、推送、部署、重置真实 Client 或发送真实 IAM 请求。

## Build verification evidence
- npm run test:unit -- --run：14 个文件、131 项通过。包含 4×7 兼容矩阵、legacy 完整集合与候选新增边界、Basic 特殊字符、随机数失败，以及动态执行 RS256/RS384/RS512 签名示例并用公钥验签。
- npm run build：最终生产构建通过。存在既有 Sass @import 废弃与大 chunk 提示。
- 定向 ESLint：本次 helper、Vue、单元测试和两份 Cypress 通过；git diff --check 通过。
- 两份 OAuth Client Cypress：26/26 通过（新增配置辅助 5 项，现有身份配置 21 项）；最终样式块整理后再次运行新增 5 项，5/5 通过。
- 浏览器实测覆盖密钥生成与创建、临时示例密钥不随更新提交、关闭后清除、复制失败回退、reset 失败重试和成功、禁用与冲突、未知 grant 显式清理、依赖、JWKS 条件字段、390px 抽屉溢出。
- 已查看桌面请求示例及手机授权复选框截图，代码长行只在 pre 容器内滚动；390px 下 drawer body 的 scrollWidth 不超过 clientWidth。
- 上述为 Build 阶段实际检查，浏览器接口均使用 mock，不代表真实 IAM/第三方联调或部署；正式 sdlc-test 尚待实现完成确认。

## User review remediation — 2026-11-05
- 用户截图指出配置界面过于复杂，正常依赖/禁用原因被呈现为红色错误。本轮按已接受范围修正呈现：授权选项为紧凑双列/窄屏单列，协议值仍可见，external_code 使用短名并在提示中保留完整值。用途和禁用原因放入不阻挡鼠标的说明浮层；仅已选的实际冲突显示红色错误。
- 接入示例默认折叠；JWKS URL 保持地址配置，说明精简为客户端公钥集用途。终端导航明确不清空已有能力，显示 Client 已配置摘要，直登选择仅在当前终端有相应网页登录能力时展示。
- 补充桌面授权区高度小于260px断言；131项单元测试通过，26项浏览器Mock回归通过，生产构建、ESLint和diff检查通过。已查看桌面与390px截图。
- 延续用户“更新容器我看看”的授权，修正通过后只重新构建并更新本地Admin容器，不修改其他服务和真实Client配置。

## 2026-11-05 用户追加要求
- 新建时自动生成 UUID Client ID，保留必要的接入约定手动输入能力；已保存 ID 只读。使用 Web Crypto，不把 ID 当作客户端密钥。沿用当前 helper/Vue/单元测试和 Cypress 文件。
- 终端切换清理及终端持久化回到已接受的 iam-client-terminal-login-config-ui 计划处理，不能继续把终端仅解释为导航。编辑是否锁定等待用户对歧义作出选择。

2026-11-06：后续应用模型重构由已接受的 iam-application-configuration/plan.md 承接；保留UUID、grant复选框与密钥生命周期，旧终端模型被应用架构类型替代，历史证据保持原样。
