# Intent: IAM Client 按终端配置登录方式
Author: Rain. Status: accepted.

## Problem
OAuth Client 管理页面同时展示网页登录提供商、`external_code` 的底层 `code_type`、身份源绑定、Origin 和
OAuth2 高级参数。管理员必须先理解协议实现才能完成业务配置，且同一平台在 APP、小程序、端内网页和浏览器
场景下的多个枚举值难以区分。

## Proposed outcome
管理员为 OAuth Client 单选一个终端类型：浏览器、APP、微信小程序、企业微信小程序或端内网页；页面随后只
展示并保存该终端支持的中文登录方式。切换终端时清除前一终端的登录方式、身份源绑定和由其派生的 grant，
不同终端不能在同一个 Client 上同时勾选。管理员选择登录方式后，Admin 自动映射网页登录提供商、`code_type`、
grant 和身份源绑定，System 持久化终端类型并校验提交字段与该终端一致。

## Constraints
不改变 IAM Token 请求协议和默认拒绝语义。System 新增单值终端类型管理字段及数据库列，IAM 运行时仍使用
现有 grant、网页登录提供商、`code_type` 和身份源绑定执行认证。已有未标注终端的 Client 必须由管理员选择并
保存后完成收敛，不自动把跨终端组合归类为某个终端。普通浏览器第三方登录默认走 IAM 管理的
`/oauth2/authorize` 闭环。
