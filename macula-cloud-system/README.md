# Macula系统管理

本模块基于[youlai](https://gitee.com/youlaitech/youlai-mall) 代码定制整理。

租户、应用、权限、用户、菜单、角色

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

- 在oauth2服务的UserDetailService的loadUserByName方法中读取用户信息，包括基本信息、角色、按钮权限等信息

  考虑到按钮权限数据量大（登录的时候按钮权限信息放到redis，不作为JWT或者introspect接口返回）
  - 对于JWT，在Token生成的地方把按钮权限缓存到redis
  - 对于Token，在introspect方法中，把按钮权限缓存到redis，不要直接返回
  - **TODO** oauth2要在JWT或者introspect方法返回deptId、dataScope、nickname属性

- 按钮权限在菜单表，前端有一个directive，调用/api/v1/users/me 接口，返回用户的角色、按钮权限数据，按钮权限数据来自redis

- 在macula-boot-starter-security模块中的PermissionService也是给Controller的方法注解用于判定按钮权限，权限数据来自redis
  - （这里依赖redis有问题，导致每个微服务都要依赖system的redis）

- URL权限通过定时任务后台缓存，在网关层获取URL权限信息，并检查URL访问权限
  - **TODO** 监控数据表，触发更新

### 租户、应用

创建应用，设置应用的租户ID。具体某个应用的后端界面获取菜单时提供应用ID，获取该租户的菜单信息。（后面搞一个模版，默认就包含系统管理功能，租户隔离）