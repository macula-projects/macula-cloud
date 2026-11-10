# Intent: Admin授权码登录接入
Author: Rain. Status: accepted.

## Problem
Admin目前通过password grant换取Token，新IAM已移除此流程；因此本轮新System、IAM和Admin镜像虽然构建完成，仍不能安全整体切换。旧IAM也不识别新应用的草稿/启用状态，继续混跑不能证明新认证边界有效。

## Proposed outcome
Admin通过用户明确指定的authorization_code流程接入IAM，能够完成登录、回到管理端并使用现有功能。登录成功后继续完成用户信息、认证租户/可选租户、权限和菜单初始化；失败或登录过期时给出可恢复的提示，退出行为不回归。接入完成并验证后，解除三个本地新镜像整体切换的登录阻塞。

## Affected users and systems
Admin管理端用户；macula-cloud-admin登录入口、回调、会话及原有初始化；IAM授权服务器与Admin对应Client；System用户/租户/菜单接口及本地部署配置。Gateway仅检查必要的接入边界，不因本需求扩展无关路由功能。

## Constraints
- 使用authorization_code，保持新IAM现有S256 PKCE要求；不恢复password grant，也不通过旧IAM旁路新的草稿/启用状态。
- 保留上一轮已实现的多协议、应用草稿、具体身份源及统一绑定策略，保护大量现有未提交改动。
- 浏览器不得持有客户端Secret或私钥；登录失败、回调参数、临时登录材料和令牌不能泄漏到日志或任意跳转地址。
- 保留现有用户类型、租户、权限及菜单边界，不把账号在IAM登录成功等同于已获管理端访问权限。
- 管理端Client必须明确配置，不批量猜测或自动启用其他历史应用；已执行迁移采用追加方式。
- 本地镜像切换承接用户此前明确提出的“更新镜像”目标，实施与验证完成后再执行；不扩展为远程发布、Git提交或推送授权。

## Open questions
当前问题与目标无待用户补充的业务事实。回调路径、会话保存与过期处理、当前管理端Client的明确初始化及本地切换顺序在设计阶段依据代码核实，不在意图阶段预设实现。

Rain于2026-11-07以YES明确接受本意图；未授予Git提交/推送权限。
