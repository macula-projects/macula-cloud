# Web/SPA 登录模式实现

Status: implemented — 创建后固定修订已实现，等待本轮Build完成确认；此前可切换合同的绿色证据保留为历史。

## 当前模型

- 应用类型仅保留WEB_SERVER、SPA、NATIVE、MINIAPP、SERVICE。Web/SPA用webLoginMode选择BROWSER或IN_APP，一个Client二选一。
- 新增唯一生产Java类型OAuth2WebLoginMode。共享OAuth2ApplicationLoginPolicy统一校验架构、模式、来源数量及grant路径；目录投影相同矩阵，能力携带webLoginModes，不在协议settings中放登录模式。
- 移除convertToInApp及独立端内类型/转换UI。两模式切换先确认副本，再清理不适用的Client登录配置，暂存DRAFT。取消不改草稿，失败可重试；保留架构、ID、凭据、回调/Origin、身份源实体与用户绑定。切回BROWSER不自动恢复内置登录。

## 持久化与认证边界

V26仅ADD可空web_login_mode列；没有UPDATE/DELETE，未操作业务库。新建Web/SPA写BROWSER，历史NULL读取为BROWSER；更新省略保留当前模式。JSON显式null、空值、非字符串、重复键及未知模式拒绝。非Web配置模式和旧applicationType=IN_APP拒绝，不兼容转换。

模式进入实体/Form/VO、完整配置摘要及CAS，比较原始NULL快照，不以显示默认值替代并发条件。派生Client缓存版本升级，两RegisteredClient视图均投影有效模式。授权/Token/Session持久化类型、字段、UID和白名单未变。

复用现有端内策略、请求隔离、回调/绑定版本和MFA证明。普通SSO不能绕过端内证明；端内不提供PASSWORD/SMS/EMAIL或普通登录回退。网页端内只授权码，JSAPI只external_code。Web端内Code经过配置的basic/post/private_key_jwt机密认证；SPA/Native/Miniapp保持none。直接Code不建浏览器SSO、不增加refresh；常规BROWSER行为保持。

平台Admin固定BROWSER，初始化不接受SDK/端内配置，也不隐式清理既有绑定。删除因此失效的SDK认领方法和目录依赖；其余平台凭据和幂等边界保持。

## 用例与文档

补充模式枚举、类型矩阵、单能力/grant排他、严格JSON、配置摘要、更新省略、目录、两Client视图、迁移、并发和管理端确认/取消/保存失败用例。真实安全链用例覆盖Web端内三种机密认证；厂商远端/账号解析使用显式Mock，不能据此声称平台真机联调通过。

旧类型转换IT替换为WebLoginModeConcurrencyIT，新增WebLoginModeMigrationIT。历史Email迁移测试固定到V25，保留原IN_APP历史样本；当前绑定IT schema guard升级26。正式执行器仍须先验证历史迁移夹具，再升级隔离库跑当前Mapper。

README、配置指南、类图及第三方接入说明按当前模型更新，保留官方合同链接。本轮未新增package-info.java，新增类型引用使用显式import。

## 构建与静态检查

- `mvn -o -pl macula-cloud-system,macula-cloud-iam -am clean test-compile -DskipTests -Plocal`：成功；存在原有MapStruct/测试varargs警告，未执行测试。
- 所有Git变更Java Checkstyle：成功。
- 本轮JS/Vue与测试源码无fix ESLint：0 errors；Prettier格式警告与全套前端构建属于后续验证证据，未使用lint --fix。
- `git diff --check`：成功。
- 初次编译发现调用参数、草稿Form类型及一个缺失import，已修正并clean重编成功；没有将编译错误记成测试失败。

## 基线与范围

范围外133个已有修改文件摘要保持，重叠文件在原import/Email/SMS改动上增量实施，未回滚原改动。临时实现前内容快照、增量patch及日志保留在/private/tmp/macula-iam-review-20261111；不提交生成物或凭据。

本轮无合同或路径偏离；计划中的未改测试继续作为正式回归范围。没有提交、推送、合并、镜像更新、部署或业务迁移。第三方登录bug按用户要求暂停。

工程师完成确认门：Is the implementation complete per the plan?

## Stage4修正

工程师确认后实际执行测试并按失败回到实现，详见[verification.md](verification.md)。补充Converter认证参数隔离、旧类型拒绝异常适配、只读退役grant兼容、测试夹具及双实例真实Redis缓存证明；精确补充路径已记录于plan。最终测试结果以verification为准。


## 创建后固定修订实现

用户YES接受Plan Round2/3后，完成以下增量：

- 创建DTO严格接收模式；Web/SPA创建时持久化所选模式，省略缺省BROWSER。IN_APP草稿同时明确关闭内置登录，协议/settings可为空；非Web与资源服务器不接收模式。
- 更新在任何协议/绑定写入前比较原有效模式；DRAFT/ENABLED和启停均不可双向修改，省略与相同模式保留。历史NULL仍为已固定BROWSER，原NULL快照保留在CAS中。
- 创建页提供模式单选，首次请求传入mode；详情只读。删除switchWebLoginMode、changeMode、模式切换弹窗、清理/恢复代码以及不再使用的props、imports和事件。
- 用例改为创建选择、空协议草稿、只读重载和API拒绝修改；固定模式内并发IT保留两个Redis实例、提交失效/回滚、凭据和绑定保持证明。README/配置/类图同步新合同。

本轮Build实际证据：`mvn -o -pl macula-cloud-system -am clean test-compile -DskipTests -Plocal`为BUILD SUCCESS；所有变更Java Checkstyle、相关前端无fix ESLint及git diff --check通过。初次测试源码编译发现两个漏加import，已补齐并clean重编成功，不计为测试运行。

正式单元、浏览器和隔离验证尚未执行。本轮精确25路径内22路径有增量，范围外198个已有修改文件摘要保持。未新增迁移、改IAM认证链或重建运行容器；上一轮候选Admin热修镜像未激活。没有合同/路径偏离。

本轮工程师完成门：Is the implementation complete per the plan?
