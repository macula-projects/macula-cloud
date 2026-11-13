# IAM第三方登录诊断合同
Status: accepted

接受依据：用户YES批准/private/tmp/macula-iam-review-20261111/callback-diagnostic-20261112.md及上一轮答复中的最小日志修订、测试、IAM更新及复测。

- 钉钉成员API错误记录数字顶层/子错误码，支持显式字段与旧errmsg内的有界subcode；HTTP错误可记录允许名单内缺失权限。
- 飞书目录和租户Token阶段记录HTTP状态、数字code、允许名单内permission_violations.subject；缓存/网络/格式异常只记录固定阶段、分类与异常类型。
- 公共目录Policy拒绝记录受控状态、required、tolerable，不输出外部身份或组织ID。
- 字符串长度、响应解析长度、列表数量有界；未知字段/权限忽略，解析失败不能覆盖原认证异常。禁止原始响应、异常message/stack、URL、Code/Token/Secret及用户资料入日志。
- 完全保留HTTP调用、异常类型、消费状态、目录错误分类（包括99991672当前拒绝）、OPTIONAL/REQUIRED/AUTO及绑定行为；不以增加日志为理由放宽认证。
- 相关单测证明成功/拒绝不变及恶意响应脱敏；模块verify与静态检查绿色后，仅更新IAM并检查其余容器保持。实际平台复测由用户完成，未复测不宣称修复登录。

## Verification strategy

先用真实失败分支的模拟HTTP回归复现缺失日志，再证明相同输入仍拒绝认证且新增日志可定位错误。对原始响应注入敏感标识、未知权限、换行、畸形与超限结构，断言日志只保留受控字段。执行IAM及依赖verify、全仓后端verify、Admin既有完整验证和静态检查；区分默认隔离测试跳过与实际执行。镜像运行验证不替代真实第三方复测。

绑定跟进诊断：用户报告同一认证链的账号密码绑定失败后，公共catch增加阶段及经过校验的IAM类/方法/行号投影，只读JVM代码位置；仍禁止原始异常message/toString、cause和完整堆栈。不得变更绑定判定、attempt、pending消费或事务语义。

用户YES接受实际修复：IAM身份源Mapper提供参数化主键FOR UPDATE读取，替换绑定两处通用Wrapper读取，保留加密处理、行锁、来源/Client版本及全部校验；独立加密读取IT与原绑定IT验证后仅更新IAM。无数据库/凭据/绑定迁移，不改Boot公共加密组件。
