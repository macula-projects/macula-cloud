<h2 align="center">Macula Cloud</h2>

<p align="center">
	<strong>基于Macula Boot开发的通用(认证、权限等)技术应用平台</strong>
</p>

<p align="center">
    <a href="https://github.com/macula-projects/macula-cloud/blob/main/LICENSE" target="_blank">
        <img src="https://img.shields.io/github/license/macula-projects/macula-cloud.svg" >
    </a>
    <a href="https://central.sonatype.com/search?q=macula&smo=true" target="_blank">
        <img src="https://img.shields.io/maven-central/v/dev.macula.boot/macula-boot-starters" />
    </a>
    <a>
        <img src="https://img.shields.io/badge/JDK-1.8+-green.svg" >
    </a>
    <a>
        <img src="https://img.shields.io/badge/SpringBoot-2.7+-green.svg" >
    </a>
    <a>
        <img src="https://img.shields.io/badge/SpringCloud-2021.x+-green.svg" >
    </a>
</p>

## 概述

基于Macula Boot的微服务应用开发平台，提供多租户、应用管理、权限、工作流、低代码、报表、批处理、数据订阅、资源中心、API管理、表结构管理和SQL审计等通用技术平台能力。

### Macula Cloud Gateway 网关中心

平台对外统一入口，提供统一认证、鉴权、接口加解密等服务

### Macula Cloud API 平台对外API SDK

提供各微服务给外部访问的接口定义

### Macula Cloud IAM 认证中心

提供基于OAUTH/CAS/OIDC/SAML协议的统一认证服务，所有服务经过网站认证

### Macula Cloud ID ID中心

统一的ID生成服务

### Macula Cloud System 管理中心

统一的租户、应用、用户、权限等管理

### Macula Cloud Seata 分布式事务管理

分布式事务管理

### Macula Cloud Task 调度中心

提供基于PowerJob/XxlJob的统一任务管理

### Macula Cloud RocketMQ MQ管理

基于RocketMQ 和 RocketMQ Connect管理

### Macula Cloud Docs

API接口文档和数据库结构文档服务

### Macula Cloud Channel 渠道中心(TODO)

移动生态应用的管理和微信能力对接服务

### Macula Cloud Msg 消息中心(TODO)

消息发送服务

### Macula Cloud OSS 资源中心(TODO)

资源管理服务

### Macula Cloud ...

该模块下面是可选择的一些加快开发周期的低代码应用，比如低代码、报表、 工作流管理等应用

## 编译说明

通过pl指定需要编译的模块，api模块为必须要指定的模块，需要指定需要编译的profile.包括local、dev、stg、pet、prd等

```shell
mvn clean package -DskipTests=true -Pdev -pl macula-cloud-api,macula-cloud-api/macula-cloud-system-api,macula-cloud-system
```

## License

Macula Boot and Macula Cloud is Open Source software released under the Apache 2.0 license.