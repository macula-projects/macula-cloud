# Intent: 接口与实现包结构整理
Author: Rain. Status: accepted.

## Problem
macula-cloud 的部分接口与实现混在同一包，职责边界和扩展实现位置不清晰。

## Proposed outcome
接口与实现分离，实现位于 impl 或具体技术、协议子包，相关引用和文档保持一致。

## Affected users and systems
Macula Cloud 后端维护者；IAM、System、Gateway、System API 及调用方与测试。

## Constraints
只调整包结构和必要引用，不改变业务、HTTP/JSON、数据库、缓存键和授权持久化合同；不新增无必要接口。

## Open questions
无。工程师于 2026-11-13 对上轮逐项整理清单回复 OK，确认该范围和方向；不包含 Git 提交、部署或发布。

## 2026-11-13 推送与合并授权

工程师在实现及验证交付后先回复 OK，随后明确要求“推送合并”。据此允许本变更创建分支、提交、推送、创建 PR 并在审查和平台检查通过后合并 main；此前不提交/推送的约束仅记录当时授权边界，已由本次明确指令更新。不包含部署、发布镜像或数据库操作。
