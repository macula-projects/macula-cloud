# Intent: 修复 Macula Samples 应用 AK 登记错误
Author: Rain. Status: draft.

## Problem
Macula Samples Admin BFF 使用 `macula-samples-admin-bff` 作为 AK 调用 Macula Cloud Gateway 获取菜单，
但 System 初始化数据把同一应用登记为拼写错误的 `macual-samples-admin-bff`。Gateway 因而无法从 Redis 找到
正确 AK 对应的密钥，持续以 AK/SK 验签失败拒绝请求；已经初始化的数据库和后续新环境都会受影响。

## Proposed outcome
现有环境升级后和新环境初始化后都只保留正确的 `macula-samples-admin-bff` 应用编码，System 刷新应用缓存后
Gateway 能用该 AK 找到原有 SK 和租户配置，Macula Samples 可以正常获取菜单。修复不得输出、重置或改变 SK。

## Affected users and systems
受影响的是使用 Macula Samples Admin 的开发者和管理员，以及 Macula Samples Admin BFF、Macula Cloud
Gateway、System、`macula-system` 数据库和共享 Redis 应用缓存。

## Constraints
必须兼容已经执行 V1 的数据库，不能修改已执行迁移来代替增量升级；不得记录或回显 SK；不得宽松接受错误 AK；
迁移只能精确修正目标初始化记录，遇到正确编码已被其他记录占用时必须安全失败并要求人工处理。

## Open questions
无。
