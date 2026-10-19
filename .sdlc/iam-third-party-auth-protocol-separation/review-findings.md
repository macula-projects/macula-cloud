# Review findings: IAM 第三方身份认证与协议解耦

## Plan

Reviewer: `sdlc-reviewer` fallback；`REVIEW.md` 未指定 Stage 3 reviewer，默认 `codex-plan-review` 未安装。仅执行 Plan pass，不执行源码 Bugs、Security 或 Compliance pass。

### Round 1（实际 1 轮）

- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:4` — Important — 多个拟改 Java 与测试文件只写 basename/“同目录”，artifact gate 无法逐项确认目标 — 已改为每个目标的仓库根相对完整路径。
- `.sdlc/iam-third-party-auth-protocol-separation/plan.md:8` — Minor — 新增 `sys_user.username` 唯一约束但遗漏 System README 迁移说明 — 已将 `macula-cloud-system/README.md` 加入文件清单、实施顺序和 Proof，明确 V7 预检、失败恢复与回滚说明。

Round 1 未修改源码或运行测试；以上修订待增量复核。

### Round 2（本轮实际 1 轮，累计实际 2 轮）

仅复核 Round 1 后的改动：所有拟变更文件已列为仓库根相对完整路径，`macula-cloud-system/README.md` 已进入文件清单、实施步骤和 Proof。上一轮两项均已修订，本轮无新发现；Plan pass 通过。审查者未修改文件或运行测试。
