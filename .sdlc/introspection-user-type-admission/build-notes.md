# 实施记录（IAM范围）

用户类型判断仅位于IAM ResourceTokenIntrospectionAuthenticationProvider；用户Token缺失/非法/不允许类型返回active:false，机器按持久化原始client_credentials跳过类型检查。签发时类型来自已认证账号目录并核对业务Client，保留原主体UID。

System、System API和Admin只负责allowedUserTypes字段、校验、持久化、展示及缓存视图版本。V29新增默认[]列，不猜填历史许可、不改凭据/绑定。

## 用户纠偏后的撤回
已撤回本次全部Boot源码、测试、README及SDLC增量；逐文件比对本次SHA快照，未覆盖他人工作。Boot源工作区为空；从干净源码恢复必要本地Maven工件。Cloud移除对撤销Boot新增常量的依赖，userType常量由Cloud API自持。

原Boot缓存与派生JWT扩展及其验证不再属于当前变更。当前只保证实际进入IAM的内省请求进行准入判断，不承诺改变调用方原缓存命中行为。

未提交、推送、部署、重启Docker、更新业务数据库或轮换凭据。最后一次Cloud全仓测试的Docker阻塞仍属环境限制，当前相关模块验证另行记录。

纠偏后复验：干净Boot依赖下Cloud API/IAM/System共682个测试，681通过、1既有条件跳过，0失败/错误；28个变更Java通过Checkstyle。Boot工作区为空且本地JAR无本次撤销类型/字段。

## 人工验收与继续
工程师先后明确确认当前IAM实现，并要求“继续啊”。据此继续完成验证与PR前审查；Docker恢复后本轮全仓测试已BUILD SUCCESS。不将本次“继续”扩展为部署或此前另一变更的Git授权。

## 本轮收尾完成
Docker恢复后718个全仓测试中717通过、1既有条件跳过，BUILD SUCCESS。当前IAM-only源码经独立Bugs/Security/Compliance及verify mode审查无发现；本地PR说明已准备。Boot仍干净，源码快照逐文件一致。剩余为本次提交/推送PR的明确Git授权，不自行执行远端操作。

## 本轮交付授权
工程师明确要求“推送提交，更新镜像”。据此授权本变更创建分支、提交、推送、创建PR，并定向构建/重建本地System、IAM、Admin。System负责V29新增列迁移；不擅自给现有资源服务器猜填允许类型、不轮换凭据、不改Boot、不重建其他服务；未授权合并PR。此前不提交/部署的表述记录的是当时边界，已由本次明确指令更新。
