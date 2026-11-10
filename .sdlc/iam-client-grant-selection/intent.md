# Intent: IAM Client 授权方式勾选与认证接入辅助
Author: Rain. Status: accepted.

## Problem
OAuth Client 的授权方式使用逗号分隔协议值输入，配置人员难以理解与认证方式的兼容关系，错误到保存时才出现；选择认证方式后也缺少可直接使用的凭据和接入示例。

## Proposed outcome
配置人员通过中文名称勾选授权方式，了解兼容限制和用途。密钥认证提供密钥生成、重置及对应请求示例。private_key_jwt 保持配置 JWKS URL，提供签名算法说明和客户端签名接入示例。

## Affected users and systems
OAuth Client 配置人员、Macula Cloud Admin 的 Client 页面；以 System 校验及 IAM 实现为兼容依据。

## Constraints
保留现有认证和授权边界、历史兼容及 API 字段格式。private_key_jwt 的私钥由接入应用持有，保持 JWKS URL 配置。不静默删除已有配置，不把 IAM 登录页短信登录误解释为必须配置 sms grant。

## Open questions
无意图层面未决问题；具体冲突提示、示例和敏感信息生命周期在 spec 中审阅。

2026-11-05：Rain 以“private_key_jwt 保持配置JWKS URL”限定范围，并以“好的，确实实施。”明确接受上述意图。尚不代表接受后续未审阅的 spec/plan。
