# Review findings: IAM OAuth Client 网页登录路由

## Plan

### Round 1

- Important — `ThirdPartyLoginContext` 增加 `clientId` 会影响五个平台 Provider、Session state、失败处理、待绑定和
  绑定控制器测试，原文件清单不完整。已补齐所有直接构造 context 的测试文件，并要求证明 state、待绑定上下文和
  绑定成功续转保留可信 clientId，不增加默认为空的兼容构造器。
- Important — V10 只清理数据库不足以处理旧 IAM 实例和共享 OAuth Client 缓存，滚动混跑可能继续接受已删除的
  企微网页 external code。已固定“停止/排空旧 IAM → System 执行并验证 V10 → 失效专用 Client cache → 启动新
  IAM”的升级顺序，并增加预热旧缓存后的拒绝证明与 README 运维步骤。

### Round 2

- Approved — 仅复核 Round 1 后的文件清单、context clientId 证明、升级顺序和缓存失效修订，无新增发现。
