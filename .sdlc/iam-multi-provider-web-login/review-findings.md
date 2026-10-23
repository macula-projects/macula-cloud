# Review findings: IAM 飞书、钉钉、微信网页认证接入

## Plan

- 评审者：`REVIEW.md` 未指定 Stage 3 评审者；默认 `codex-plan-review` 未安装，回退至 `sdlc-reviewer` 代理。
- 预算：只执行 Plan pass，最多 3 轮；第 1 轮完整检查，其后只检查上一轮后的变化；同类缺陷只报告一次，不重复本文件已记录发现。

### Round 1

- 实际轮次：1。
- Important — `plan.md:26` 未明确禁止提供商 HTTP 端点的跨域 3xx 自动跳转，也没有恶意 `Location` 测试。已在 Client 实现步骤中要求显式禁用重定向，并新增三个 `ClientTest` 证明每个换码/用户信息阶段均不会请求 `Location`。
- Important — `plan.md:49` 的飞书“租户不符”缺少可信租户锚点。已明确以 System 配置的企业自建应用 `appId/appSecret` 及服务端官方换码结果作为唯一租户锚点，两种模式不得信任请求中的租户声明；若官方协议不能保证 code 与配置应用绑定，则必须回到规格评审。
- Minor — Stage 4 未列出全量 Maven 与 Admin build。已加入 `mvn test -Plocal`、`npm run test:unit`、`npm run build`，并规定环境缺失时如实记录未运行范围与前置条件。

### Round 2

- 实际轮次：2，仅检查 Round 1 后的变化。
- Reviewer 报告 `ClientTest` 未列入文件清单；核对当前 `plan.md` 第 13 行，`FeishuClientTest.java`、`DingtalkClientTest.java`、`WechatClientTest.java` 已全部明确列出，因此判定为误报，不修改计划。
- Round 1 的其余三项修订均通过复审。

### Round 3

- 实际轮次：3，仅核对 Round 2 误报。
- No findings。Reviewer 确认 `plan.md` 第 13 行的三个 `ClientTest` 与 Proof 第 51 行完全一致，Round 2 为误报。
