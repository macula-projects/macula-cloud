# Admin授权码接入验证与镜像交接
日期：2026-11-07
状态：已完成下列验证与镜像构建；本地切换被PreToolUse部署守卫拦截，未部署。不标记完整Verification is green。

## 本轮实际输出
- `mvn test -Plocal`：479 tests，0 failures，0 errors，1 skipped；原始输出 `BUILD SUCCESS`，日志 `/private/tmp/admin-code-stage4-maven.log`。
- `npm run test:unit -- --run`：`Tests 178 passed (178)`，日志 `/private/tmp/admin-code-stage4-vitest.log`。
- Admin build：`built in` 成功，日志 `/private/tmp/admin-code-stage4-build.log`。
- 全部Cypress：`All specs passed!`，41/41，日志 `/private/tmp/admin-code-stage4-cypress.log`。这是合成协议端点/签名素材的浏览器证据。
- `AdminClientProvisioningIT`：独立临时MySQL/Redis，1 test通过，`BUILD SUCCESS`。确认失败原子回滚、成功提交驱逐、Gateway摘要保持、其他Client不变、幂等与漂移拒绝。日志 `/private/tmp/admin-code-stage4-isolation/maven.log`。测试容器已清理，不连接业务库。
- IT实际执行先发现测试使用了未引入的Lettuce；改用项目既有Redisson。随后增加测试缓存的clear/read就绪断言，最终全部断言通过。生产缓存配置未改变；不把测试装配修订称为生产缓存缺陷修复。
- 本轮未修改AGENTS/CLAUDE/skill/hook/REVIEW/reviewer，未触及相应eval配置。

## 已构建的本地镜像
- System `68ed75b2b559bcc7e6295b46eff2474c7e8aefb571e21046fcf2572a8d78dbeb`
- IAM `ab7c281848fc0355053cb71962ab5573238539e8cd9c6300841180f7fe2ed726`
- Admin `a459e805eb0c2614c87e2e5be5dff311222b616460ddf71371235e6c23cf13ef`
- 均为 `macula/macula-cloud-<service>:6.1.0-SNAPSHOT`；构建输出三项 `Built`，日志 `/private/tmp/admin-code-images.log`。

## 当前阻塞与后续
- 用户“现在可以更新镜像了吧”已确认实施并明确授权本地更新。独立核实Docker `orbstack` 指向本地unix socket，项目为 `macula-cloud-dev`。
- 按文档在命令前设置 `AI_SDLC_ALLOW_PROD=1` 后，只读预检仍被仓库PreToolUse production guard拒绝。读取守卫源码确认它在解析命令文本前读取自身进程的环境变量，因此命令前缀不改变守卫环境；未改动或绕过守卫。
- 运行中的System/IAM/Admin仍是之前的健康容器；未写本地.env，未迁移业务库，未运行apply。Gateway及其他服务未变。
- 仍需完成计划所列其他条件IT的本轮证明、守卫放行后的精确预检/一次性初始化/协调切换，以及真实IAM登录/Gateway接口验证；不能以Mock或镜像构建代替。
- 未commit/push/远程发布。

## 本地切换实际结果（2026-11-07续行）
- 用户重启后部署守卫已放行；只读预检通过。首次apply在Compose run --no-build参数处失败，尚未初始化；移除该不支持参数后继续，run默认使用既有镜像。
- System/IAM/Admin已切换为上列新镜像且全部healthy，Gateway仍为原容器。V16成功；Gateway为SERVICE/ENABLED/OAUTH2_1，独立Admin为SPA/ENABLED/OIDC/none，其他Client仍DRAFT。
- 原始恢复依据/private/tmp/macula-admin-cutover-j1189lkt，第二次执行依据/private/tmp/macula-admin-cutover-k2pijpcw；文件保持私密。
- 真实浏览器检查未通过：已有deploy/.env固定IAM_ISSUER_URI=http://iam.macula.local:9010，而本轮Admin authority=http://127.0.0.1:9010。Discovery确实返回前者，前端严格issuer匹配拒绝；Admin及已登记Origin的Discovery均HTTP200不能代表登录可用。
- 已询问用户是否统一本地issuer为127.0.0.1，或保留本地域名并明确设计HTTP例外；不自行更改固定issuer或放宽安全校验。等待这一必要配置选择，未宣称登录验收完成。

## 本地更新验收完成（取代前述部署阻塞记录）
- Rain确认将本地issuer统一为http://127.0.0.1:9010。私密备份环境后重建System/IAM，公开Discovery已验证返回此issuer。
- 真实Nginx回调发现index.html相对config.js在/auth/callback下加载错误路径。修正为/config.js后仅重建Admin；最终Admin镜像ebae368b0049c645204c9d2faff99723af9f2ea067dbb613dc7427c5a539ace4，System/IAM镜像保持前述ID。
- 实际浏览器验收 `1 passing (6s)`：IAM真实账号认证→无hash回调→Token HTTP200（含ID Token）→users/me、tenants/options、menus/routes HTTP200/success→应用配置API HTTP200/success；刷新及本地退出通过。回调查询已清除、事务一次消费、无旧TOKEN Cookie/localStorage身份缓存。仅记录阶段/字段名/状态，无令牌或密码、截图、视频。
- 修复后前端回归：178/178 Vitest、build成功、41/41 Cypress，日志/private/tmp/admin-final-{vitest,build,cypress}.log；实际验收结果/private/tmp/admin-real-login-check/result.log，脱敏阶段诊断diagnostic.json。
- 最终System/IAM/Admin及原Gateway全部healthy，V16已成功，其他历史Client保持DRAFT；无Git提交/推送。本地镜像更新请求已完成；不将其等同于全部条件IT/远程发布/源码审查门禁完成。

## F12登录故障修复验收
- 已在真实部署独立会话复现：授权页原有password表单正常，插入/.well-known/appspecific/com.chrome.devtools.json后表单消失并提示上下文无效；默认请求缓存被探测请求覆盖。
- 两安全链共享的RequestCache仅跳过精确DevTools探测路径的保存，保留Spring其他默认规则和端点认证限制；不放宽HostedLogin策略。真实Security链回归验证缓存URL保持及密码认证续转。
- IAM全部279项测试通过，Checkstyle及git diff --check通过。只更新IAM镜像ffd1dcb6d0c797d2b769bcd619eaef0619b68088817f7e1104d145e24d8585eb，容器healthy。
- 更新后实际HTTP会话两组均通过：正常授权→密码登录→带code/state回调；授权→DevTools探测→密码登录→带code/state回调。仅输出布尔结果，未输出凭据或code；证据/private/tmp/iam-f12-runtime.json。
