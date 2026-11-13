# Spec: 接口与实现包结构整理
Author: Rain. Status: accepted.

## Requirements
按工程师以 OK 确认的上一轮完整清单，移动 16 个生产类型：IAM 的 10 个具体实现进入 impl、redis、keystore；System API 的 2 个 OAuth2 类型进入 protocol.oauth2；Gateway 的 CryptoLocaleServiceImpl 进入 crypto.impl；System 的 3 个缓存及可信租户辅助类进入 service.cache。

保持接口位置、方法签名、Bean 名称、条件装配、事务语义和全部方法体。同步仓库内 Java 引用（含历史迁移源码引用，不更改迁移 SQL 或执行逻辑）、实现测试和四个模块 README。Java 导入路径发生变化；外部源码调用方需重新编译并更新 import，不承诺原 FQCN 二进制兼容。JSON kind=oauth2、字段、数据库和缓存格式保持。

## Non-goals
不创建接口，不重构业务，不移动 Token、Principal、Session 持久化类型，不更新依赖、部署、凭据或数据库，不提交或推送。

## Verification strategy
先 API、IAM、System、Gateway 相关单测，再全仓后端测试；检查旧 FQCN 残留、接口与实现同包、变更类的方法体、JSON 多态和装配现有测试；执行适用 Checkstyle 和 diff 检查。

## Approval
2026-11-13：工程师针对上轮完整目标包映射和约束明确回复 OK。本文件将该已确认方案落盘，不扩大实现范围。

## 2026-11-13 推送与合并授权

工程师在实现及验证交付后先回复 OK，随后明确要求“推送合并”。据此允许本变更创建分支、提交、推送、创建 PR 并在审查和平台检查通过后合并 main；此前不提交/推送的约束仅记录当时授权边界，已由本次明确指令更新。不包含部署、发布镜像或数据库操作。
