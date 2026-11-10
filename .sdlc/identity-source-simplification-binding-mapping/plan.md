# Plan: 简化身份源配置与可选择的账号绑定映射 (from spec.md 2026-11-09)
Status: accepted

使用现有Java17、Spring Security、MyBatis-Plus、Flyway、Redis、CryptoManager、Vue3/Element Plus和现有测试工具；不增加依赖。Rain已接受spec及单组映射等关注点；本计划仍等待工程师批准。无CLAUDE.md时遵循AGENTS.md及按需规则。本轮不是fast path，不触碰Boot/Gateway、POM、部署文件、密钥、业务库或Git远端。

## Files that change
以下“目录＋文件名”逐项构成确切路径，不代表整目录可修改。新增文件标注“新增”；已列文件若无需变化则保持原样。额外文件须先记录理由；改变合同则重新取得工程师决定。

### 共享契约
目录macula-cloud-api/macula-cloud-system-api/src/main/java/dev/macula/cloud/system/enums/
- IdentityBindingSourceField.java（新增）、IdentityBindingTargetField.java（新增）、IdentityBindingMapping.java（新增）
- IdentityBindingRule.java、IdentitySourceType.java、IdentitySourceFlow.java、IdentityDirectoryPolicy.java
目录macula-cloud-api/macula-cloud-system-api/src/test/java/dev/macula/cloud/system/enums/
- IdentityBindingMappingTest.java（新增）、IdentitySourceFlowTest.java

### System配置与迁移
目录macula-cloud-system/src/main/java/dev/macula/cloud/system/
- form/IdentityProviderForm.java、vo/IdentityProviderVO.java、vo/IdentityProviderCatalogVO.java
- pojo/entity/SysIdentityProvider.java、converter/IdentityProviderConverter.java
- service/impl/IdentityProviderServiceImpl.java
- service/impl/OAuth2ClientConfigurationCatalogServiceImpl.java、vo/OAuth2ClientConfigurationCatalogVO.java
目录macula-cloud-system/src/test/java/dev/macula/cloud/system/
- service/impl/IdentityProviderServiceImplTest.java、service/impl/OAuth2ClientConfigurationCatalogServiceImplTest.java、vo/SettingsModelJsonTest.java
- integration/IdentityBindingMappingMigrationIT.java（新增）
另：macula-cloud-system/src/main/resources/db/migration/V21__identity_source_binding_mapping.sql（新增；当前最高V20，实施前重查版本）、macula-cloud-system/README.md。

### IAM匹配与资料
目录macula-cloud-iam/src/main/java/dev/macula/cloud/iam/
- pojo/entity/SysIdentityProvider.java、mapper/SysUserMapper.java
- authentication/thirdparty/IdentityProviderConfigService.java、authentication/thirdparty/VerifiedExternalIdentity.java
- authentication/thirdparty/IdentityBindingMatcher.java（新增）
- authentication/thirdparty/IdentitySourceBindingPolicyService.java、authentication/thirdparty/RegistrationIdentityBindingService.java
- authentication/thirdparty/ProviderDirectoryPolicyService.java、authentication/thirdparty/ProviderDirectoryResult.java
- authentication/thirdparty/ProviderDirectoryRefreshService.java
- authentication/wecom/WecomDirectoryClient.java、authentication/feishu/FeishuDirectoryClient.java、authentication/dingtalk/DingtalkDirectoryClient.java
另：macula-cloud-iam/src/main/resources/mapper/SysUserMapper.xml、macula-cloud-iam/README.md、macula-cloud-iam/docs/third-party-integration.md。
如官方证据证明企微手机号/邮箱必须走当前未实现的敏感资料授权，需要先补充精确接口文件及用户授权交互、state用途并回到工程师决策；不凭现有user/get返回值猜测支持，也不在本计划下偷偷改scope。

### IAM测试
目录macula-cloud-iam/src/test/java/dev/macula/cloud/iam/
- authentication/thirdparty/IdentityBindingMatcherTest.java（新增）
- authentication/thirdparty/IdentityProviderConfigServiceTest.java、authentication/thirdparty/IdentitySourceBindingPolicyServiceTest.java
- authentication/thirdparty/RegistrationIdentityBindingServiceTest.java、authentication/thirdparty/IdentitySourceBindingIT.java
- authentication/thirdparty/ProviderDirectoryPolicyServiceTest.java、authentication/thirdparty/ProviderDirectoryRefreshServiceTest.java
- authentication/thirdparty/IdentitySourceTestFixtures.java
- authentication/wecom/WecomDirectoryClientTest.java、authentication/feishu/FeishuDirectoryClientTest.java、authentication/dingtalk/DingtalkDirectoryClientTest.java
- authentication/wecom/WecomClientTest.java、authentication/wecom/WecomLoginModeTest.java
- authentication/wechat/WechatClientTest.java
- protocol/oauth2/grant/externalcode/ExternalCodeAccountResolverTest.java
- config/IamProtocolTest.java

### Admin与SDLC
目录macula-cloud-admin/
- src/views/system/identityProvider/index.vue、src/api/model/system/identityProvider.js
- src/views/system/oauth2Client/components/ClientGuide.vue
- src/locales/lang/zh-cn.js、src/locales/lang/en.js
- tests/unit/identity-provider-policy.test.js、tests/unit/admin-identity-security-settings.test.js
- cypress/e2e/identity-provider-policy.cy.js、cypress/e2e/admin-identity-security-settings.cy.js
- README.md
目录.sdlc/identity-source-simplification-binding-mapping/
- intent.md、spec.md（仅记录人工接受及必要的决策澄清）、plan.md
- review-findings.md、provider-contract-evidence.md、verification.md（新增）

## Order of work
1. **保存基线与平台补证**：仅为清单文件建立私有增量备份/摘要，保留混合工作区；重新核对最高Flyway版本。整理证据表：平台、应用/来源类型、端点、HTTP方法、精确请求字段、Token用途、返回身份字段、权限/用户授权、错误语义、官方版本和可读依据。保留全部原官方链接及当前已核实合同，不能用“最新SDK”推断未核实场景。微信两种Code与企微两种网页入口先做既有合同核对；企业member_id/employee_no/mobile/email分别确认。字段证据缺失则明确列为尚未完成，不以永久禁用代替最终交付。若补证要求新授权交互，按上述条件补计划并请求决定；其余独立工作继续。
2. **共享映射**：新增源/目标枚举与单组映射值对象；只允许member_id→username、employee_no→username、mobile→mobile/username、email→email/username六种全局组合，再按来源/能力收窄：MOBILE_PROOF手机号证明型能力（微信手机号Code、阿里云号码认证）只允许mobile→mobile；mobile→username仅适用于另有稳定企业身份且手机号来自可信企业目录的来源。保留RegistrationIdentityBindingService.validateAccount在首次、已有及手动绑定中核对MOBILE_PROOF与SysUser.mobile的既有约束，不以配置映射绕过该证明。目录和UI必须解释不适用的组合。旧IdentityBindingRule双向转换仅限原三种；未知、半组或无效组合拒绝。来源能力与可信字段分开，普通社交邮箱不可匹配。成员ID保留厂商语义，飞书OpenID不冒充user_id，job_number不未经证据就当工号。既有verified_mobile属性仅在已核验手机号路径兼容转换到mobile。
3. **追加迁移与写兼容**：V21只新增两个nullable映射列，按三种明确旧规则回填；保留binding_rule、来源ID、Secret、绑定与历史目录策略，未知规则不改写。Form用显式字段出现标记区分省略、null与实际值；autoBind开关和映射共同校验。新旧字段同时提交必须等价；旧请求只带三种已知规则时可转换；省略映射保留既有配置，显式清空必须同时关闭自动绑定，旧客户端对不可表达的新规则更新不允许丢弃映射。VO只为等价旧三种投影bindingRule，其他映射不伪装旧规则。所有更新沿用CAS版本并递增来源version。ProviderConfig扩展映射后保留现有构造签名的兼容重载，只按旧三种规则转换，避免其他HTTP/目录测试夹具被机械改写。SettingsModelJsonTest验证真实Jackson3反序列化时省略/显式null/有效值三态，出现标记不能序列化到API。回滚旧服务前禁止新映射写入，不能让旧服务接管无法表达的新映射；不自动降级规则。
4. **内部Flow和自动资料**：scene=CONFIGURATION（支付宝仅凭据管理）不参与Flow选择，不要求登录合同，保持原凭据管理能力；其他新建来源在服务端选择该具体来源唯一已核实适用的默认Flow；存在多个候选或未核实合同则不可启用并显示业务原因，不恢复技术单选。更新时省略Flow/目录策略保持原值；旧API显式修改仍按既有白名单/版本约束检查。新增内部AUTO目录值供新企业来源，旧IDENTITY_ONLY/OPTIONAL/REQUIRED/BACKGROUND原值和行为不改。新AUTO始终在事务外按固定来源获取适用资料，只有已证明成员身份且属于当前固定组织才允许把可容忍资料故障与身份准入分离；来源不具备成员证明时失败关闭。固定corpId组织可从该来源已校验持久配置确定；其他组织缺失要求明确企业标识，不从请求或其他来源猜填。
5. **属性归一与可信性**：三个企业适配器只输出证据表确认的字段，统一为member_id/employee_no/mobile/email；标明平台权威/授权前提，验证响应企业和用户与已核验身份一致。资料不能覆盖原身份主键或来源元数据。明确非成员/停用一律拒绝；未知错误不当资料可选故障；缺少所选字段不自动绑定。AUTO与旧更强策略均覆盖已有绑定和手动绑定路径；旧BACKGROUND资料仅后台展示，不能拿过期/后台快照自动绑定。复用原缓存加密、TTL、来源版本及刷新栅栏，不增加员工资料存储。
6. **统一匹配与事务重查**：IdentityBindingMatcher供首次匹配与RegistrationIdentityBindingService锁内调用同一规则，不能再用“手机号规则/其他规则全查username”的二分逻辑。按枚举选择固定Mapper SQL，不接受动态列名/任意表达式；查询只针对所选目标，不调用跨字段登录兜底。SQL与UserIdentifierNormalizer对username、mobile、email采用同一规范化语义；最多取两条识别歧义，FOR UPDATE重查仍是相同条件/范围，使用有界查询，不拉全表到Java归一化。MySQL真实验证空白、区号、邮箱大小写与排序规则冲突，任何无法证明一致的情况拒绝自动绑定，不改历史用户数据。候选须通过现有Client userType/tenant/账号准入链；未支持的用户目录不启用映射。不能用某租户角色过滤凭空改变原全局账号模型。映射变化重查来源版本；已有绑定优先且不因映射变化重新关联，绑定唯一键/并发锁不变。
7. **UI与指南**：移除Flow/接口版本及通讯录策略控件；保留必要组织/凭据/状态、所需权限与旧强策略的只读业务提示。自动绑定提供源字段和目标字段两个选择器，按目录显示六种合法组合及不可用原因；清空、切换来源和编辑回显不串字段。新Admin省略内部Flow/策略和旧bindingRule，仅提交显式映射与版本；关闭自动绑定的清除行为明确。企微说明同应用凭据、不同取Code入口；小程序说明前端code_type两种来源、后端核验及手机号证明不生成OpenID，指南仍复用现有/oauth2/token external_code，不新增登录API。
8. **开发反馈与交付**：先最小相关单元/HTTP合同，再API+System+IAM回归、Admin完整单元/构建/桌面手机Cypress；适用时运行仅隔离MySQL/Redis的IT。同步README和接入文档，保存真实结果、未完成平台证据及计划偏差；检查变更Java注释/Checkstyle、无修复ESLint、git diff --check。完成后停在Build人工完成门，Stage4不自行批准；不更新镜像、提交、推送或修改业务库。

## Risks
- **官方资料和敏感授权仍需补证**：设计接受不等于已获得所有平台字段。新增敏感授权/JSAPI合同可能超出已列源码，必须先形成可审查增量；不能静默降级为“字段不支持”后声称全部完成。
- **策略隐藏的兼容差异**：历史更强准入保持；新AUTO只能容忍已证明组织身份之后的资料缺失。新请求省略与旧请求显式值不得混同；未解决组织证明时拒绝启用。
- **映射可信边界**：企业目录邮箱是已接受的组织权威匹配前提，不宣称完成邮箱OTP；社交显示邮箱不可用。手机号来源与OpenID身份独立；映射修改不改既有绑定。
- **规范化/并发**：普通SQL等值可能遗漏历史格式变体或受数据库排序规则影响；首次和锁内匹配必须一致，以真实MySQL证明拒绝规范化多匹配，不能只靠Mock或原值查询。
- **迁移、旧客户端与回滚**：只确定转换三种旧规则；旧代码不理解的新映射不能丢失或误读。迁移不提供破坏性down脚本；旧服务回滚前必须关闭新映射写入口并选择能读取该合同的版本，不自动修改绑定。
- **工作区与外部环境**：当前已有大量未提交源码/测试/配置，不整体回滚或夹带；隔离IT不得使用运行中业务库/Redis逻辑库。没有平台真实凭据时明确未完成厂商/设备验证。

## Proof
- IdentityBindingMappingTest：六种全局组合及MOBILE_PROOF仅允许mobile→mobile、两类空值、非法目标、来源不支持、旧三规则等价与冲突；IdentitySourceFlowTest：默认仅选已核实适用合同，旧Flow不被隐藏UI切换、CONFIGURATION无Flow仍正常管理。
- IdentityProviderServiceImplTest及目录测试：新增/更新、CAS冲突、省略与显式null、旧客户端、未知旧规则、不可表达映射、Flow/策略保留、AUTO默认和敏感信息不回显。
- IdentityBindingMappingMigrationIT：隔离MySQL V20→V21、空库初始化、三种旧规则/未知规则、ID/Secret/绑定/策略不变、幂等重跑，不改历史迁移校验和。
- IdentityBindingMatcherTest、IdentitySourceBindingPolicyServiceTest、RegistrationIdentityBindingServiceTest：MOBILE_PROOF首次/已有/手动绑定及映射变更始终核对本地mobile、非法mobile→username按能力拒绝；稳定企业身份可按目录手机号匹配username；仅查所选字段、归一化一致、0/1/多匹配、无效/跨目录账号、已有绑定优先、锁内版本/规则变化与并发冲突。
- IdentitySourceBindingIT：真实MyBatis/事务/MySQL规范化多匹配、大小写排序规则、锁内重查、回滚无半绑定；真实Redis/目录测试证明来源隔离、停用/版本变化、过期和BACKGROUND快照不用于自动绑定。
- 三个平台DirectoryClientTest及Policy/Refresh测试：精确HTTP合同、组织/成员核对、字段可信白名单、邮箱/手机号授权缺失、权限不足/非成员/停用/超时5xx、旧强策略与新AUTO、日志脱敏。
- WecomClientTest/WecomLoginModeTest、WechatClientTest、IamProtocolTest与ExternalCodeAccountResolverTest：企微双入口state及统一网页来源；微信两种Code固定端点/来源/Client/能力分流、防重放及手机号不造OpenID；既有认证/账号证明闭环不变。
- Admin Vitest与Cypress：Flow/策略控件不存在、合法映射联动、错误/空态/重复提交、版本冲突、旧源只读说明、保存回显及双Code指南；桌面1440与手机390px无溢出且页脚可见，截图实际视检。
- 执行命令：先目标mvn -pl macula-cloud-iam,macula-cloud-system -am test -Plocal，再按仓库规则扩大mvn test -Plocal；隔离IT用verify与-Dit.test及-Dfailsafe.failIfNoSpecifiedTests=false，明确运行条件。Admin使用npm run test:unit -- --run、npm run build -- --mode docker、针对性Cypress及无fix ESLint。只有真实执行后才记录通过；Mock不代表平台/设备联调。

## Plan review
REVIEW.md未指定Stage3 reviewer；工具与已安装技能未发现codex-plan-review。按sdlc-build回退独立sdlc-reviewer子代理，仅Plan pass，最多三轮；实际轮次及发现写入review-findings.md。工程师批准前不改源码。Round1发现1项Important（MOBILE_PROOF证明约束）和1项Minor（CONFIGURATION无Flow例外），已在步骤2/4及Proof修正；Round2仅复核这些变化及直接影响，结果NO_FINDINGS；实际共2轮。完整记录见review-findings.md，该轮审查结束时计划为 draft；其后工程师接受记录见下节。

## Engineer acceptance
Rain以YES接受本计划及两轮Plan审查，开始Phase B。不包含Git、镜像更新或业务库授权。逐文件首次修改前保存在/private/tmp/identity-binding-build-baseline。

## 2026-11-09 用户明确缩小企微范围
官方user/get正文确认新应用敏感资料需要本人授权。用户选择“暂保留成员 ID 绑定，敏感字段后续处理”。因此本轮企微仅提供member_id→username，不新增snsapi_privateinfo、user_ticket或授权弹窗，也不把userid冒充工号。此为用户明确暂缓，不能记为敏感资料已经实现；其他平台及映射实施继续。

## Phase B实施说明
- 规范化SQL在READ COMMITTED下没有范围锁，不能证明无并发新候选。锁内匹配读取实际MySQL事务隔离级别，只在真实REPEATABLE READ/SERIALIZABLE事务执行；其他情况失败关闭。文件沿用已列Matcher、Mapper与IdentitySourceBindingIT，增加真实隔离级别拒绝测试，不改生产配置或历史用户数据。
- 用户明确暂缓企微敏感资料后，AUTO企微仅消费固定企业基础授权已经核实的member_id，不强加通讯录或敏感资料权限；历史更强策略仍查原目录。钉钉资料字段已从现有官方SDK源码核实，飞书从官方contact-v3文档核实；真实应用权限/设备仍未验收。

## Phase B 完成记录
- 已按接受顺序完成共享映射、V21 写兼容、内部默认合同、企业属性、统一匹配与锁内重查、Admin 与指南。清单中无行为变更需要的相邻实现 / 测试保持原样，未机械改写。
- 最终开发反馈：后端 556 总计（555 通过、1 环境门跳过）、Admin 199 单元、Cypress 20、迁移 IT 1、真实绑定 IT 15；Checkstyle 35 文件零错误，构建 / ESLint / diff 检查通过。完整范围及证据见 verification.md。
- 桌面截图使用实际可用的 1280px 视口，手机 390px，已视检溢出及页脚；原计划 1440px 桌面改用更窄视口覆盖。
- 实际偏差与限制：企微敏感字段由用户明确延后；新增锁内真实隔离级别限制与非 ASCII 邮箱拒绝边界；均已同步说明和验证。未更新业务镜像、未执行业务迁移、未提交或推送。
- 隔离 MySQL / Redis 测试容器及临时凭据已清理。Phase B 完成确认仍待工程师，未自行进入正式 Stage 4。

## Build 完成人工确认与 Stage 4
Rain 在 Build 完成问题后回复 YES，确认按计划实现完成。已按 sdlc-test 重新执行全后端、Admin 全单元 / 全 Cypress、构建、无修复 ESLint、Checkstyle 及两个隔离 IT；实际命令、输出、Proof 对照、跳过和配置 eval 边界见 verification.md 的 Stage 4 一节。独立 IdentitySourceFlowTest 为计划命名偏差，实际默认 / 保留 / CONFIGURATION 场景在 IdentityProviderServiceImplTest 覆盖，未虚构独立测试类。正式结论为 Verification is green，下一步仅为 sdlc-deploy 审查准备，未部署。

## 部署后回归修复：应用身份源选择列表为空
用户报告应用“添加身份源”无候选。现有 System 目录将 secret_config_json 的任意非空字符串视为混合密钥，与 IAM / 保存侧已接受的历史 JSON null 和空对象合同不一致。修复在原文件清单内：OAuth2ClientConfigurationCatalogServiceImpl.java、对应测试及 System README；不改前端过滤、不改凭据或业务数据。新增用例先复现 JSON null 被拒绝，再对齐 IAM：仅 null / 空对象视为空，非空对象、数组、字符串、损坏 JSON 仍拒绝。全后端重跑 557 总计、556 通过 / 1 既有跳过、0 失败错误，Checkstyle 36 文件零错误。部署沿用用户明确的本地镜像更新授权，仅更新 System。
