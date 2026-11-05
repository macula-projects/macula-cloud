# Macula Cloud Admin

## 作用

Macula Cloud Admin 是 Macula Cloud 的管理控制台，基于 Vue 3、Vite、Pinia、Vue Router 和 Element Plus，提供租户、应用、用户、角色、菜单及平台配置等管理界面。

本地开发时，浏览器通过 `/api` 访问 Gateway；默认代理目标为 `http://127.0.0.1:9000`，IAM 默认地址为 `http://127.0.0.1:9010`。

## 启动

前置条件：

- Node.js（版本要求见 `package.json`）
- npm
- 已启动 Macula Cloud Gateway 和 IAM；后端启动方式见 [项目根 README](../README.md)

```bash
cd macula-cloud-admin
npm ci
npm run dev
```

开发服务器默认访问地址为 <http://127.0.0.1:5900>。如需调整 Gateway、IAM 或登录配置，请修改 `.env` 和对应环境文件。

构建并预览生产产物：

```bash
npm run build
npm run preview
```

预览服务默认访问地址为 <http://127.0.0.1:4173>。

## 应用 API Key 管理

在系统管理的应用列表中点击 **APIKEY**，打开应用专属管理抽屉。支持创建多把 Key、脱敏列表、显示/隐藏、复制、编辑备注和 `accessPath`、删除；创建后可以立即复制保存。Key URL 不与应用 URL 合并，未配置时由 System 在新增/更新缓存时使用应用 URL；规则示例：`GET:/service/api/**,POST:/service/api/orders`。

完整 Key 只在当前打开界面中暂存，关闭后清除；复制依赖浏览器安全上下文的 Clipboard API，不可用时可显示后手动复制。操作需有当前租户应用管理权限。若缓存同步失败，先刷新列表确认数据库状态，避免重复创建。数据库迁移及缓存恢复说明见 [System README](../macula-cloud-system/README.md#应用-api-key)。

应用表单的可访问路径使用多行输入，规则格式为 `HTTP方法:/路径`，支持 Ant 通配符（`*`、`**`），多个规则以英文逗号分隔；应用留空表示不限制访问路径，API Key 留空则使用应用配置。联系方式需填写有效的 11 位手机号，前端即时校验与后端一致。API Key 列表中的凭据及操作按钮保持单行，长凭据可横向查看。

API Key 列表的 `createTime` 使用 `java.util.Date` 表达时间点，JSON 返回带 `Z` 的 ISO 时间，管理端按浏览器当地时区显示。审计字段以 JVM 当地时间写入，System 各实例及重启前后应保持相同 JVM 时区；不改变数据库历史时间值。

## 接口日期时间约定

System/TinyID接口的时间点使用带 `Z` 或偏移的ISO字符串。应用、用户、部门、日志、维护人和TinyID列表统一通过 `$TOOL.formatDateTime` 按浏览器当地时区显示，空值或非法值显示占位符。API Key使用相同格式化方法。

浏览器提交时间点时使用日期对象的 `toISOString()`，不要剥离时区或手工加减八小时。纯日期筛选保持日期语义；Excel采用后端定义的导出格式。
