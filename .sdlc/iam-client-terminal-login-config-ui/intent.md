# Intent: IAM Client 按终端配置登录方式
Author: Rain. Status: accepted.

## Problem
OAuth Client 管理页面同时展示网页登录提供商、`external_code` 的底层 `code_type`、身份源绑定、Origin 和
OAuth2 高级参数。管理员必须先理解协议实现才能完成业务配置，且同一平台在 APP、小程序、端内网页和浏览器
场景下的多个枚举值难以区分。

## Proposed outcome
管理员先选择当前要配置的终端类型：浏览器、APP、微信小程序、企业微信小程序或端内网页；页面随后只展示
该终端支持的中文登录方式。管理员选择登录方式后，Admin 自动映射现有网页登录提供商、`code_type`、grant
和身份源绑定。普通配置不直接展示 `code_type`，只有兼容既有特殊配置时才在高级区域展示底层值。

## Constraints
不改变 IAM Token 协议、数据库结构、System API 字段、安全校验和默认拒绝语义。已有 Client 的配置必须完整
回显并可无损保存；唯一匹配的身份源自动绑定，多个匹配身份源时才要求人工选择。普通浏览器第三方登录默认走
IAM 管理的 `/oauth2/authorize` 闭环，不把端侧自行取得浏览器 code 的兼容能力混入常用配置。
