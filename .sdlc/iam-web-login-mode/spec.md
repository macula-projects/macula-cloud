# Spec: Web/SPA 浏览器或端内登录二选一 (from intent.md 2026-11-12)
Status: accepted

## Source intent
[需求](intent.md)：标准 Web/SPA 分别选择浏览器或端内模式，二选一；原共存方案作废。

## Requirements
1. 应用类型保留 WEB_SERVER、SPA，移除独立 IN_APP 类型及转换入口。
2. 新增应用级 webLoginMode：BROWSER 或 IN_APP。浏览器模式沿用普通登录/SSO；端内模式只允许当前配置的单一平台能力，必须核验平台证明，不展示普通登录或回退入口。
3. Web 保留已配置的机密认证，SPA 保持公共认证；授权码继续 S256 PKCE，JSAPI不宣称已执行IAM PKCE。环境识别不作为身份证明。
4. 存量 IN_APP 不兼容、不转换、不迁移；不改其他应用类型和自定义grant范围。

## Non-goals
不做同Client共存、自动分流、失败后切换普通登录、多平台端内聚合或业务前端自动改造；不操作业务数据或部署。

## Design
Admin提供“应用类型”和“登录模式”两个独立单选项，不生成Web端内、SPA端内等组合类型。共享登录规则校验类型、模式及能力；InAppLoginPolicyService仅处理端内来源和证明，原端内请求隔离改按登录模式生效。登录认证与协议装配仍分开。创建Web/SPA时选择模式，创建成功后包括DRAFT均固定。详情只读，后端所有更新入口拒绝改变有效模式；省略保留，相同模式可重复提交。历史NULL按BROWSER固定，已有Client不转换；需要另一模式时新建应用。删除模式切换/确认/清理逻辑，保留凭据、身份源实体和用户绑定。

## Data and interfaces
webLoginMode进入应用Form/VO、持久化、完整配置摘要/CAS、当前Client视图及缓存失效；正常Web/SPA缺省BROWSER，非法值拒绝，更新省略时保留原模式，显式改变模式拒绝。创建草稿支持严格webLoginMode输入；Web/SPA省略缺省BROWSER，非Web不接受模式。相关结构变更只新增迁移源码并用隔离库验证，不改历史Flyway、不转换旧IN_APP记录。其他应用类型不提供此选项。WEB_SERVER端内JSAPI仍需协议校验和external_code Provider共同支持既有机密认证，不能自动改成none。

## Flagged concerns
- 独立IN_APP类型移除具有破坏性，用户已明确无需兼容；未知类型不能默认放行，non-blocking。
- 新模式字段需要结构变更；本轮仅准备与隔离验证，业务执行需另行授权，non-blocking。
- Web端内Code的机密认证必须保留，不能照搬当前仅none规则；真实SDK/平台容器联调单独记录，non-blocking。
- Author待确认，不推断署名；已有未提交代码保持，non-blocking。

## Verification strategy
覆盖Web/SPA×两模式：浏览器普通登录可用、端内普通登录/SSO绕过被拒绝、平台成功及错误/重放/跨来源/关闭/版本变化/MFA拒绝；验证Web机密认证和SPA公共认证边界、创建选择、详情只读、模式更新拒绝、CAS/缓存及缺省、存量类型拒绝。运行相关API/System/IAM测试、隔离迁移/绑定IT、全仓后端和Admin单元/构建/Cypress及规范检查；不将Mock当真机通过。当前只有设计修订，未实现或执行本功能测试。

政策来源：AGENTS.md、.agents/rules中的架构/后端/前端/测试/配置发布规则、REVIEW.md，以及用户最新二选一要求。原共存规格不再适用。

接受记录：用户对二选一简化规格及Flagged concerns回复“确认”，进入sdlc-build Phase A。未授权源码实施前跳过工程计划门，不提交或操作业务环境。

修订接受记录：用户要求选择后固定，并对创建时选择、创建成功（含草稿）后不可修改的精确修订回复YES；旧可切换说明被本修订替代。
