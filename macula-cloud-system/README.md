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

