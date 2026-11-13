# Plan: IAM第三方登录安全诊断 (from spec.md 2026-11-12)

用户已明确接受最小修订的实现、测试、仅更新IAM和真实回调复测；无新增人工门，Plan review不代表人工审批。精确文件为该方案的实现细化。

## Files that change
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderFailureDiagnostics.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClient.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyService.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/provider/ProviderFailureDiagnosticsTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/dingtalk/DingtalkClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/feishu/FeishuDirectoryClientTest.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/directory/ProviderDirectoryPolicyServiceTest.java`
- `macula-cloud-iam/README.md`
- `.sdlc/iam-third-party-login-diagnostics/intent.md`
- `.sdlc/iam-third-party-login-diagnostics/spec.md`
- `.sdlc/iam-third-party-login-diagnostics/plan.md`
- `.sdlc/iam-third-party-login-diagnostics/review-findings.md`
- `.sdlc/iam-third-party-login-diagnostics/verification.md`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingService.java`

- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/authentication/thirdparty/binding/ThirdPartyBindingServiceTest.java`

- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderMapper.java`
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityProviderLockedReadIT.java`

## Order of work
1. 保存全体已有改动摘要，只读当前Client/Directory实现、测试和规则；不恢复或重写已有工作。
2. 独立Plan审查后，编写失败日志回归；新建小型ProviderFailureDiagnostics，仅封装安全提取，不参与身份判定。数字字段限制10位、原文片段4096字符、HTTP响应16384字符、权限列表最多16项；已知权限显式Set白名单。不同平台保持各自字段合同，不增加接口/Abstract层次或package-info。
3. DingtalkClient业务/HTTP失败使用诊断工具，保留原异常和Code消费语义；FeishuDirectoryClient记录tenant_token/directory阶段，保留所有return与catch分类；ProviderDirectoryPolicyService仅在既有拒绝分支追加受控日志。
4. 单测固定顶层88及embedded subcode、HTTP权限字段、飞书业务与HTTP错误/缓存故障、目录Policy允许/拒绝；注入秘密/换行/任意URL/未知权限并断言不出现在日志，正常成功不增加错误日志。
5. 运行相关测试、IAM及依赖verify、Checkstyle/diff；验证原有范围外摘要保持。README说明日志字段与边界，verification记录实际结果和平台待复测范围。
6. 用户已在本次YES授权测试和仅更新IAM：验证绿色后构建原Compose IAM镜像，只替换IAM，不使用临时TRACE；比较运行环境、其他容器ID、登录/Discovery冒烟，准备再次平台复测。

## Risks
- 上游errmsg和HTTP异常包含秘密，不能直接记录。只输出有界数字和固定白名单权限；解析失败回退unknown且不改认证结果。
- 99991672等未知/未容忍响应本次仍拒绝，不顺手改成OPTIONAL放行。没有Schema、缓存序列化、数据迁移或权限授权变更。
- 用户此前代码、文档和依赖清理未提交；只在精确路径内增量修改并校验范围外hash。
- 仓库无CLAUDE.md时沿用现有AGENTS.md及相关规则；用户本次明确批准实现、测试、更新和复测，优先于技能要求再次问相同许可。

## Proof
- ProviderFailureDiagnosticsTest覆盖数字类型/嵌套字段/errmsg格式、畸形和超限响应、未知权限、敏感值及日志注入。
- DingtalkClientTest及FeishuDirectoryClientTest用MockRestServiceServer确认HTTP合同与成功/拒绝/超时分类保持；目录Policy测试断言状态日志和组织/非成员/REQUIRED拒绝仍成立。
- 定向回归先红后绿；mvn -o -pl macula-cloud-iam -am verify -Plocal；Checkstyle、git diff --check和范围外摘要。
- 更新后IAM healthy、启动ERROR为0、login/Discovery为200，原镜像以外容器ID不变；真实平台错误码只依据用户复现后的日志。

## 实施记录

独立Plan Round1/3为NO_FINDINGS。缺失日志回归修改前24项测试中5项失败，修改后相同24项全通过。新增安全投影工具和相关拒绝分支日志，保留全部return/catch语义；额外安全与HTTP错误测试也通过。用户YES已明确批准实现、测试和IAM更新，依用户指令继续正式验证，不重复请求同一许可。未修改保护策略文件、凭据、数据库或平台权限。

## 绑定失败跟进（2026-11-12，已批准诊断合同内的范围偏差）

用户报告两家平台均已通过，账号密码绑定仍失败，并要求继续排查。沿用此前YES批准的安全诊断、验证、IAM定向更新；不增加认证能力或改变任何判定。精确新增上述ThirdPartyBindingService及其测试路径，复用现有ProviderFailureDiagnostics和其测试。原Files清单内的helper/spec/README/verification继续维护。

底层异常在bindVerified catch被统一重包装而丢失起点。仅在原catch包装前记录account/account_proof/binding_write阶段、异常类型及首个已知IAM代码位置（类/方法/行号，跳过denied/unavailable辅助方法），不输出异常正文或完整堆栈。只读取JVM栈元素元数据，禁止原始异常toString/message/cause。保持failedAttempt、回滚、pending消费和所有return/throw不变。缺失或非法元数据记录unknown；测试注入敏感异常message/非法栈元数据并证明不泄露，断言失败仍消耗原次数且不消费pending，绑定成功不记录拒绝。

独立Plan pass只审本增量；定向回归先红后绿，再IAM及全仓后端verify、Checkstyle、范围外摘要与IAM单服务更新。Admin代码不变，沿用本轮已经执行的223单元/96浏览器证据，不把它们当绑定真实平台验证。最终需用户真实重试取证。该偏差不改变已批准安全日志合同或平台/数据库权限，不新增人工许可门。

## 加密身份源锁定读取修复（已接受）

真实绑定重试已定位`stage=binding_write exception=AuthenticationServiceException site=IdentityProviderConfigService.requireLoaded:138`。账号密码证明完成，随后锁定读取的配置JSON解析失败。独立空MySQL与当前依赖、真实Crypto拦截器使用合成AES记录复现：selectById可得到明文，通用Wrapper/selectOne（有无FOR UPDATE）不能得到同样明文；显式按ID的注解锁定查询能正确解密。不是凭据错误、平台权限或用户绑定冲突，不改业务密文来掩盖读取缺陷。

精确修复文件（已加入Files清单）：
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/mapper/SysIdentityProviderMapper.java`：增加显式selectByIdForUpdate(Long id)，使用绑定参数、按主键锁定，返回SysIdentityProvider。
- `macula-cloud-iam/src/main/java/dev/macula/cloud/iam/service/identity/RegistrationIdentityBindingService.java`：仅替换requireClient(lock=true)和bind中的两处来源锁定读；其余配置验证/租户/能力/版本/目录证明及绑定写入保持。
- `macula-cloud-iam/src/test/java/dev/macula/cloud/iam/service/identity/IdentityProviderLockedReadIT.java`：新隔离MySQL+真实加解密插件回归，使用合成AES密文，证明普通读取/锁定读取/同事务重复锁定均得到等价配置；缺失来源和解密后非法JSON仍被拒绝，不输出字段内容。
- 已在Files中的README、spec、plan、review-findings及verification：说明问题与验证边界，记录批准及证据。

顺序：独立Plan增量审查 -> 用户确认 -> 隔离回归先红后绿 -> Mapper与两处调用修正 -> 原IdentitySourceBindingIT与新加密读取IT、IAM/全仓verify、Checkstyle/diff和范围外摘要 -> 按已授权范围仅更新IAM -> 用户重新完成第三方登录/账号密码绑定复测。

风险/边界：保持SELECT FOR UPDATE及当前读，不能改回无锁selectById或使用缓存旧行；不能自行解密两次、把密文当JSON放过、忽略secret_config_json、停用来源版本/目录/Client校验或改Boot公共加密组件。无Schema/业务数据/凭据/权限迁移；修复仅在IAM查询入口兼容现有Crypto组合。新IT只使用受限隔离数据库并在结束关闭自建进程。修复实施、测试和部署证据见verification；真实账号绑定结果仅以新镜像后用户复测为准。

用户明确YES接受本精确修复方案、验证及仅更新IAM，开始实施。Plan Round3/3 NO_FINDINGS已记录，不自批人工门。

专用锁定读取修复已按批准范围完成，19项隔离集成和全仓verify绿色后仅更新IAM。首次镜像构建遇Docker Hub认证端EOF，重试成功；未改变Dockerfile或运行环境配置。尚无用户真实绑定成功确认。
