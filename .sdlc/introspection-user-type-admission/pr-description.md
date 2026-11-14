# feat(iam): 内省校验资源服务器允许的用户类型

资源服务器此前无法在令牌内省时限定可接受的用户目录。现在在 IAM `/oauth2/introspect` 中，将用户 Token 的可信 `userType` 与已认证资源服务器的 `allowedUserTypes` 比较：缺失、非法或不在集合内时只返回 `active:false`；空集合不接受任何用户 Token。机器 Token 根据持久化授权的原始 `client_credentials` 判断，跳过用户类型限制，保留既有认证和有效性规则。

System/Admin 增加允许类型的保存、校验和多选界面；V29 新增默认 `[]` 的列。Token 用户类型来自已认证账号目录，签发时与业务 Client 核对。旧 Token/旧主体不猜填、不做过渡；上线前须由维护者明确配置资源服务器允许集合。没有改动 macula-boot 或 Gateway 缓存策略。

验证：Cloud 全仓 `mvn -o test -Plocal` 构建成功，718 个测试中通过 717 个、按既有环境开关跳过 1 个；224 个 Admin 单元测试、构建、52 个相关 Cypress、2 个隔离 MySQL V29 升级/Mapper 用例通过。28 个变更 Java 文件通过 Checkstyle，ESLint 0 errors（保留排版 warnings），`git diff --check` 通过。

[Intent](intent.md) · [Spec](spec.md) · [Plan](plan.md) · [验证记录](verification.md) · [审查记录](review-findings.md)

## 审查

独立 Standard 深度 PR Round 1/3 的 Bugs / Security / Compliance 三项审查均 NO_FINDINGS；posted=0、重复抑制=0，独立 verify mode 的 verifier-rejected=0。REVIEW.md 适用，无 Important、Minor、Nit 或 Pre-existing 问题。审查基线及源码快照见审查记录。

## 当前状态

工程师已明确授权提交推送及更新本地镜像。本轮推送分支并创建PR，定向更新System、IAM、Admin；不合并PR，不修改Boot或其他服务，部署证据另行记录。

## 本地镜像交付

System、IAM、Admin已定向更新并健康；V29已执行。按工程师明确选择，仅platform-gateway允许employee，另一资源服务器保持空集合，凭据不变。其他31个容器ID及运行配置保持。Docker Hub EOF后使用原运行层加本地当前源码构建产物离线构建，产物SHA及运行元数据已核对；HTTP冒烟通过。详见[运行证据](deployment.md)。PR未合并。
