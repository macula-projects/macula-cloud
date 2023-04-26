# Macula Cloud System 管理中心

本模块基于[youlai](https://gitee.com/youlaitech/youlai-mall) 代码定制整理。

统一的租户、应用、用户、权限等管理

## 关于DTO的说明

在使用MVC三层架构时，我们通常将项目分为mapper(dao)、service、controller。

```
    controller      # 展示层，依赖service
        vo          # 展示层的输入输出对象，简化dto，适配不同展示层。如果展示层不需要定制
                    # 可以直接使用service层的dto
            req     # 展示层接口请求类
            res     # 展示层返回响应类
            
    service         # 服务层，依赖mapper
        dto         # 接口的输入输出对象，接口方法中只允许出现dto类
        
    mapper          # DAO层，不可以依赖上层
        entity      # 表映射实体
        bo          # 多表关联业务实体（主要用在sql查询）
    
    converter       # pojo类转换
                    # 建议按照主实体类聚合转换方法    
```

- mapper层定义DAO接口和SQL，包含entity（映射数据表）和bo（主要用于查询和多表关联）
- service层依赖mapper层，将dto转为entity或则将entity或者bo转为dto。不可以在service接口出现entity或者bo
- controller层依赖service，将vo转为dto或者dto转为vo返回。如果vo与dto没有差别，可以直接使用dto

### 认证、鉴权记录

- 在oauth2服务的UserDetailService的loadUserByName方法中读取用户信息，包括基本信息、角色等信息

- 我的菜单和按钮权限，前端有一个directive，调用/api/v1/users/me 接口，按钮权限。system-starter需要中转这个接口

- URL权限通过定时任务后台缓存，在网关层获取URL权限信息，并检查URL访问权限
    - 网关需要依赖system缓存URL角色关系的redis，可以考虑每个租户独立的redis
    - **TODO** 监控数据表，触发更新
- 通过AK/SK鉴权
    - 网关支持AK/SK鉴权，基于KONG的HMAC AUTH协议
    - **TODO** 需要把应用的sk，允许的url path缓存到redis供网关读取

- macula-boot-start-system模块只需要给输出管理界面的应用依赖，比如xxx-admin，其他模块不需要依赖

- 所以对于每个网关来说，需要通过redis获取URL角色对应关系、应用的配置（建议每个租户的redis可以隔离，system同步各个租户的redis）

### 租户、应用

创建应用，设置应用的租户ID。具体某个应用的后端界面获取菜单时提供应用ID，获取该租户的菜单信息。（后面搞一个模版，默认就包含系统管理功能，租户隔离）
需要租户隔离的包括：应用、菜单、字典