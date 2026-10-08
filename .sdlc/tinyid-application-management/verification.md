# 提交后增量验证

验证代码提交：`0936d3e`（包含 TinyID 主提交 `ac909a7`）。

本次验证针对最后追加的 Admin `.env` OAuth scope 引号修正。后端代码未继续修改，沿用前序 TinyID 验证；本报告不重新宣称整个 Admin OAuth 外置化计划的 Docker 运行时证明已经完成。

## 实际执行结果

- `npm run test:unit -- --run`：退出码 0。

```text
Test Files  2 passed (2)
     Tests  8 passed (8)
```

- `npm run build`：退出码 0；`2247 modules transformed`。存在大于 500 kB 的 chunk 提示。
- `npm run test:e2e:ci`：退出码 0。

```text
example.cy.js                 3 passing
tinyid-management.cy.js       5 passing
All specs passed!             8 tests, 8 passing
```

- `sh -n macula-cloud-admin/nginx/entrypoint.d/25-admin-runtime-config.sh`：退出码 0。
- `git diff --check`：退出码 0。
- 配置解析已在提交前通过 Vite `loadEnv` 断言，OAuth scope 仍为三个空格分隔的 scope，输出 `OAuth scope parsing: PASS`。

## 覆盖边界

Cypress 使用真实浏览器和前端构建产物，但后端响应通过 intercept 模拟；不证明真实 IAM 登录、Gateway 权限、System 动态菜单或 TinyID 数据库全链路。用户已确认保留该方式。测试启动的 4173 预览服务只在测试期间运行。

本次未改 CLAUDE.md、Skill 或 Hook，无适用配置 eval。未执行 Docker 应用部署或生产验证。

当前提交的增量回归通过；后续进行 PR 人工审查。
