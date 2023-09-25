-- MySQL dump 10.13  Distrib 8.1.0, for macos13.3 (x86_64)
--
-- Host: 127.0.0.1    Database: macula-system
-- ------------------------------------------------------
-- Server version	8.1.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `sys_application_tenant`
--

DROP TABLE IF EXISTS `sys_application_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_application_tenant`
(
    `id`               int                                                          NOT NULL AUTO_INCREMENT,
    `tenant_id`        bigint                                                                DEFAULT NULL COMMENT '租户id',
    `application_name` varchar(55) COLLATE utf8mb4_bin                              NOT NULL,
    `code`             varchar(55) COLLATE utf8mb4_bin                              NOT NULL COMMENT '应用编码',
    `sk`               varchar(55) COLLATE utf8mb4_bin                                       DEFAULT NULL,
    `homepage`         varchar(55) COLLATE utf8mb4_bin                                       DEFAULT NULL,
    `manager`          varchar(55) COLLATE utf8mb4_bin                              NOT NULL COMMENT '负责人',
    `maintainer`       varchar(255) COLLATE utf8mb4_bin                                      DEFAULT NULL COMMENT '维护人',
    `mobile`           varchar(20) COLLATE utf8mb4_bin                              NOT NULL COMMENT '联系方式',
    `access_path`      varchar(255) COLLATE utf8mb4_bin                                      DEFAULT NULL COMMENT '可访问路径',
    `use_attrs`        bigint                                                       NOT NULL DEFAULT '0' COMMENT '回传属性',
    `allowed_attrs`    varchar(255) COLLATE utf8mb4_bin                                      DEFAULT NULL COMMENT '允许的回传属性',
    `create_by`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                                                     NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                                                     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_application_tenant`
--

LOCK
TABLES `sys_application_tenant` WRITE;
/*!40000 ALTER TABLE `sys_application_tenant` DISABLE KEYS */;
INSERT INTO `sys_application_tenant` (`id`, `tenant_id`, `application_name`, `code`, `sk`, `homepage`, `manager`,
                                      `maintainer`, `mobile`, `access_path`, `use_attrs`, `allowed_attrs`, `create_by`,
                                      `create_time`, `last_update_by`, `last_update_time`)
VALUES (34, 12, 'macula-samples-admin-bff', 'macual-samples-admin-bff', 'example', 'http://127.0.0.1:5800',
        'admin', '2', '13822132000', '', 0, NULL, 'admin', '2023-09-01 15:35:27', 'admin', '2023-09-01 16:59:27');
/*!40000 ALTER TABLE `sys_application_tenant` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept`
(
    `id`               bigint                                 NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`             varchar(64) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '部门名称',
    `parent_id`        bigint                                          DEFAULT '0' COMMENT '父节点id',
    `tree_path`        varchar(255) COLLATE utf8mb4_general_ci         DEFAULT '' COMMENT '父节点id路径',
    `sort`             int                                             DEFAULT '0' COMMENT '显示顺序',
    `status`           tinyint                                         DEFAULT '1' COMMENT '状态(1:正常;0:禁用)',
    `deleted`          tinyint                                         DEFAULT '0' COMMENT '逻辑删除标识(1:已删除;0:未删除)',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=193 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK
TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` (`id`, `name`, `parent_id`, `tree_path`, `sort`, `status`, `deleted`, `create_by`, `create_time`,
                        `last_update_by`, `last_update_time`)
VALUES (179, '开发与测试中心', 0, '0', 1, 1, 0, 'zlhuan', '2023-02-17 10:39:59', 'jje', '2023-02-17 11:17:25'),
       (180, '共享能力中心', 179, '0,179', 1, 1, 0, 'zlhuan', '2023-02-17 10:40:19', 'zlhuan', '2023-02-17 10:40:19'),
       (181, '电商平台', 179, '0,179', 1, 1, 0, 'zlhuan', '2023-02-17 10:40:33', 'zlhuan', '2023-02-17 10:40:33'),
       (182, '业务平台', 179, '0,179', 1, 1, 0, 'zlhuan', '2023-02-17 10:40:50', 'zlhuan', '2023-02-17 10:40:50'),
       (183, '企微及展业平台', 179, '0,179', 1, 1, 0, 'zlhuan', '2023-02-17 10:42:24', 'zlhuan', '2023-02-17 10:42:24'),
       (189, 'Macula', 0, '0', 1, 1, 0, 'zlhuan', '2023-02-17 14:47:57', 'admin', '2023-09-01 14:31:49'),
       (190, '数字化中心', 189, '0,189', 1, 1, 0, 'zlhuan', '2023-02-17 14:48:16', 'zlhuan', '2023-02-17 14:48:16'),
       (191, '开发与测试', 190, '0,189,190', 1, 1, 0, 'zlhuan', '2023-02-17 14:49:07', 'zlhuan', '2023-02-17 14:49:24'),
       (192, '共享能力', 191, '0,189,190,191', 1, 1, 0, 'zlhuan', '2023-02-17 14:49:39', 'zlhuan',
        '2023-02-17 14:49:39');
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_dict_item_tenant`
--

DROP TABLE IF EXISTS `sys_dict_item_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item_tenant`
(
    `id`               bigint                                 NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        bigint                                          DEFAULT NULL COMMENT '租户id',
    `type_code`        varchar(64) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '字典类型编码',
    `name`             varchar(50) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '字典项名称',
    `value`            varchar(50) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '字典项值',
    `sort`             int                                             DEFAULT '0' COMMENT '排序',
    `status`           tinyint                                         DEFAULT '0' COMMENT '状态(1:正常;0:禁用)',
    `defaulted`        tinyint                                         DEFAULT '0' COMMENT '是否默认(1:是;0:否)',
    `remark`           varchar(255) COLLATE utf8mb4_general_ci         DEFAULT '' COMMENT '备注',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_item_tenant`
--

LOCK
TABLES `sys_dict_item_tenant` WRITE;
/*!40000 ALTER TABLE `sys_dict_item_tenant` DISABLE KEYS */;
INSERT INTO `sys_dict_item_tenant` (`id`, `tenant_id`, `type_code`, `name`, `value`, `sort`, `status`, `defaulted`,
                                    `remark`, `create_by`, `create_time`, `last_update_by`, `last_update_time`)
VALUES (1, 1, 'gender', '男', '1 ', 1, 1, 0, NULL, '*SYSADM ', '2019-05-05 13:07:52', 'jje', '2023-02-22 16:06:06'),
       (2, 1, 'gender', '女', '2 ', 2, 1, 0, NULL, '*SYSADM ', '2019-04-19 11:33:00', '*SYSADM ',
        '2019-07-02 14:23:05'),
       (3, 1, 'gender', '未知', '0 ', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 08:09:31', 'jje', '2023-02-23 14:52:12'),
       (6, 1, 'grant_type', '密码模式', 'password', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 09:11:52', '*SYSADM ',
        '2021-01-31 09:48:18'),
       (7, 1, 'grant_type', '授权码模式', 'authorization_code', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 09:12:15',
        '*SYSADM ', '2020-12-14 10:11:00'),
       (8, 1, 'grant_type', '客户端模式', 'client_credentials', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 09:12:36',
        '*SYSADM ', '2020-12-14 10:11:00'),
       (9, 1, 'grant_type', '刷新模式', 'refresh_token', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 09:12:57', '*SYSADM ',
        '2021-01-08 17:33:12'),
       (10, 1, 'grant_type', '简化模式', 'implicit', 1, 1, 0, NULL, '*SYSADM ', '2020-10-17 09:13:23', '*SYSADM ',
        '2020-12-14 10:11:00'),
       (11, 1, 'micro_service', '系统服务', 'youlai-admin', 1, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:14:12',
        '*SYSADM ', '2021-06-17 00:14:12'),
       (12, 1, 'micro_service', '会员服务', 'youlai-ums', 2, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:15:06', '*SYSADM ',
        '2021-06-17 00:15:06'),
       (13, 1, 'micro_service', '商品服务', 'youlai-pms', 3, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:15:26', '*SYSADM ',
        '2021-06-17 00:16:18'),
       (14, 1, 'micro_service', '订单服务', 'youlai-oms', 4, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:15:40', '*SYSADM ',
        '2021-06-17 00:16:10'),
       (15, 1, 'micro_service', '营销服务', 'youlai-sms', 5, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:16:01', '*SYSADM ',
        '2021-06-17 00:16:01'),
       (16, 1, 'request_method', '不限', ' * ', 1, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:18:34', '*SYSADM ',
        '2021-06-17 00:18:34'),
       (17, 1, 'request_method', 'GET', 'GET', 2, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:18:55', '*SYSADM ',
        '2021-06-17 00:18:55'),
       (18, 1, 'request_method', 'POST', 'POST', 3, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:19:06', '*SYSADM ',
        '2021-06-17 00:19:06'),
       (19, 1, 'request_method', 'PUT', 'PUT', 4, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:19:17', '*SYSADM ',
        '2021-06-17 00:19:17'),
       (20, 1, 'request_method', 'DELETE', 'DELETE', 5, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:19:30', '*SYSADM ',
        '2021-06-17 00:19:30'),
       (21, 1, 'request_method', 'PATCH', 'PATCH', 6, 1, 0, NULL, '*SYSADM ', '2021-06-17 00:19:42', '*SYSADM ',
        '2021-06-17 00:19:42'),
       (22, 1, 'grant_type', '验证码', 'captcha', 1, 1, 0, '', '*SYSADM ', '2022-05-28 11:44:28', '*SYSADM ',
        '2022-05-28 11:44:28'),
       (72, 1, 'cs', '测试字典项', 'cszdx', 0, 0, 0, '', 'jje', '2023-02-23 14:44:48', 'jje', '2023-02-23 14:44:52'),
       (73, 12, 'test_code1', '测试1', 'test', 0, 1, 0, '', 'admin', '2023-03-02 16:24:42', 'admin',
        '2023-03-02 16:24:42');
/*!40000 ALTER TABLE `sys_dict_item_tenant` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_dict_type_tenant`
--

DROP TABLE IF EXISTS `sys_dict_type_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type_tenant`
(
    `id`               bigint                                 NOT NULL AUTO_INCREMENT COMMENT '主键 ',
    `tenant_id`        bigint                                          DEFAULT NULL COMMENT '租户id',
    `name`             varchar(50) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '类型名称',
    `code`             varchar(50) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '类型编码',
    `status`           tinyint(1) DEFAULT '0' COMMENT '状态(0:正常;1:禁用)',
    `remark`           varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '备注',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `type_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type_tenant`
--

LOCK
TABLES `sys_dict_type_tenant` WRITE;
/*!40000 ALTER TABLE `sys_dict_type_tenant` DISABLE KEYS */;
INSERT INTO `sys_dict_type_tenant` (`id`, `tenant_id`, `name`, `code`, `status`, `remark`, `create_by`, `create_time`,
                                    `last_update_by`, `last_update_time`)
VALUES (1, 1, '性别', 'gender', 1, NULL, '*SYSADM', '2019-12-06 19:03:32', 'jje', '2023-02-22 10:30:28'),
       (2, 1, '授权方式', 'grant_type', 1, NULL, '*SYSADM', '2020-10-17 08:09:50', '*SYSADM', '2021-01-31 09:48:24'),
       (3, 1, '微服务列表', 'micro_service', 1, NULL, '*SYSADM', '2021-06-17 00:13:43', '*SYSADM',
        '2021-06-17 00:17:22'),
       (4, 1, '请求方式', 'request_method', 1, NULL, '*SYSADM', '2021-06-17 00:18:07', '*SYSADM',
        '2021-06-17 00:18:07'),
       (48, 1, '测试', 'cs', 0, NULL, 'jje', '2023-02-22 16:28:09', 'jje', '2023-02-23 14:53:55'),
       (49, 12, '测试租户字典1', 'test_code1', 1, NULL, 'admin', '2023-03-02 16:24:28', 'admin', '2023-03-02 16:24:28');
/*!40000 ALTER TABLE `sys_dict_type_tenant` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_log`
--

DROP TABLE IF EXISTS `sys_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log`
(
    `id`                bigint                                 NOT NULL AUTO_INCREMENT COMMENT '主键',
    `op_ip`             varchar(55) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '操作IP',
    `op_url`            varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '操作Url',
    `op_name`           varchar(55) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '操作人',
    `op_title`          varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '操作标题',
    `op_method`         varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '请求方法',
    `op_request_method` varchar(55) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '请求方式',
    `op_param`          varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '请求参数',
    `op_status`         int                                             DEFAULT NULL COMMENT '操作状态',
    `error_msg`         varchar(255) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '错误信息',
    `json_result`       longtext COLLATE utf8mb4_general_ci COMMENT '响应结果信息',
    `create_by`         varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`       datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`    varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time`  datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_log`
--

LOCK
TABLES `sys_log` WRITE;
/*!40000 ALTER TABLE `sys_log` DISABLE KEYS */;
INSERT INTO `sys_log` (`id`, `op_ip`, `op_url`, `op_name`, `op_title`, `op_method`, `op_request_method`, `op_param`,
                       `op_status`, `error_msg`, `json_result`, `create_by`, `create_time`, `last_update_by`,
                       `last_update_time`)
VALUES (1, '127.0.0.1', '/api/v1/users', 'admin', '新增用户',
        'dev.macula.cloud.system.controller.SysUserController.saveUser()', 'POST',
        '{\"password\":\"laborum et dolore\",\"roleIds\":[0],\"gender\":1,\"deptId\":1,\"mobile\":\"17025517201\",\"nickname\":\"邹艳\",\"avatar\":\"http://dummyimage.com/100x100\",\"id\":40,\"email\":\"j.gfwgrn@qq.com\",\"status\":1,\"username\":\"test001\"}',
        0, NULL, 'true', 'admin', '2023-03-24 00:12:28', 'admin', '2023-03-24 00:12:28'),
       (2, '127.0.0.1', '/api/v1/dept/189', 'admin', '修改部门',
        'dev.macula.cloud.system.controller.SysDeptController.updateDept()', 'PUT',
        '189 {\"name\":\"Macula\",\"sort\":1,\"parentId\":0,\"status\":1}', 0, NULL, '189', 'admin',
        '2023-09-01 14:31:50', 'admin', '2023-09-01 14:31:50'),
       (3, '127.0.0.1', '/api/v1/tenants/1', 'admin', '修改租户',
        'dev.macula.cloud.system.controller.SysTenantController.updateTenant()', 'PUT',
        '1 {\"code\":\"macula-cloud\",\"name\":\"Macula Cloud\",\"description\":\"技术平台\",\"supervisor\":[2,117,118]}',
        0, NULL, 'true', 'admin', '2023-09-01 14:49:08', 'admin', '2023-09-01 14:49:08'),
       (4, '127.0.0.1', '/api/v1/tenants/12', 'admin', '修改租户',
        'dev.macula.cloud.system.controller.SysTenantController.updateTenant()', 'PUT',
        '12 {\"code\":\"macula-samples\",\"name\":\"Macula Samples\",\"description\":\"示例平台\",\"supervisor\":[117,116]}',
        0, NULL, 'true', 'admin', '2023-09-01 14:49:37', 'admin', '2023-09-01 14:49:37'),
       (5, '127.0.0.1', '/api/v1/app/15', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '15', 0, NULL,
        'true', 'admin', '2023-09-01 15:28:25', 'admin', '2023-09-01 15:28:25'),
       (6, '127.0.0.1', '/api/v1/app/16', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '16', 0, NULL,
        'true', 'admin', '2023-09-01 15:28:27', 'admin', '2023-09-01 15:28:27'),
       (7, '127.0.0.1', '/api/v1/app/17', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '17', 0, NULL,
        'true', 'admin', '2023-09-01 15:28:29', 'admin', '2023-09-01 15:28:29'),
       (8, '127.0.0.1', '/api/v1/app/18', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '18', 0, NULL,
        'true', 'admin', '2023-09-01 15:28:31', 'admin', '2023-09-01 15:28:31'),
       (9, '127.0.0.1', '/api/v1/app/25', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '25', 0, NULL,
        'true', 'admin', '2023-09-01 15:32:56', 'admin', '2023-09-01 15:32:56'),
       (10, '127.0.0.1', '/api/v1/app/33', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '33', 0, NULL,
        'true', 'admin', '2023-09-01 15:33:04', 'admin', '2023-09-01 15:33:04'),
       (11, '127.0.0.1', '/api/v1/app/32', 'admin', '删除应用',
        'dev.macula.cloud.system.controller.SysApplicationController.deleteApplications()', 'DELETE', '32', 0, NULL,
        'true', 'admin', '2023-09-01 15:33:06', 'admin', '2023-09-01 15:33:06'),
       (12, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-arrow-down\",\"sort\":0,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/2342\",\"component\":\"Layout\",\"name\":\"测试菜单\",\"id\":174,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T15:37:39.848\"}',
        0, NULL, 'true', 'admin', '2023-09-01 15:37:40', 'admin', '2023-09-01 15:37:40'),
       (13, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-basketball\",\"sort\":10,\"type\":\"MENU\",\"parentId\":174,\"path\":\"/admin/echo\",\"component\":\"admin/echo\",\"name\":\"ECHO\",\"id\":222,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T15:38:48.457\"}',
        0, NULL, 'true', 'admin', '2023-09-01 15:38:48', 'admin', '2023-09-01 15:38:48'),
       (14, '127.0.0.1', '/api/v1/permissions/203,203,203', 'admin', '删除权限',
        'dev.macula.cloud.system.controller.SysPermissionController.deletePermissions()', 'DELETE', '203,203,203', 0,
        NULL, 'true', 'admin', '2023-09-01 15:38:48', 'admin', '2023-09-01 15:38:48'),
       (15, '127.0.0.1', '/api/v1/permissions', 'admin', '新增权限',
        'dev.macula.cloud.system.controller.SysPermissionController.addPerm()', 'POST',
        '{\"createBy\":\"admin\",\"createTime\":\"2023-09-01T15:38:48.516\",\"urlPerm\":\"GET:/tesfe\",\"name\":\"test\",\"menuId\":222,\"id\":204,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T15:38:48.516\"}',
        0, NULL, 'true', 'admin', '2023-09-01 15:38:49', 'admin', '2023-09-01 15:38:49'),
       (16, '127.0.0.1', '/api/v1/app/addMaintainer/34', 'admin', '添加维护人',
        'dev.macula.cloud.system.controller.SysApplicationController.addMaintainer()', 'PUT',
        '34 {\"useAttrs\":false,\"maintainer\":\"2\"}', 0, NULL, 'true', 'admin', '2023-09-01 16:54:39', 'admin',
        '2023-09-01 16:54:39'),
       (17, '127.0.0.1', '/api/v1/app/validtor/appCode', 'admin', '验证应用code是否规范',
        'dev.macula.cloud.system.controller.SysApplicationController.validtorAppCode()', 'GET', '{}', 0, NULL, 'true',
        'admin', '2023-09-01 16:59:27', 'admin', '2023-09-01 16:59:27'),
       (18, '127.0.0.1', '/api/v1/app/34', 'admin', '修改应用',
        'dev.macula.cloud.system.controller.SysApplicationController.updateApplication()', 'PUT',
        '34 {\"useAttrs\":false,\"code\":\"macual-samples-admin-bff\",\"manager\":\"admin\",\"accessPath\":\"\",\"mobile\":\"13822132000\",\"sk\":\"example\",\"id\":34,\"applicationName\":\"macula-samples-admin-bff\",\"homepage\":\"http://127.0.0.1:5800\"}',
        0, NULL, 'true', 'admin', '2023-09-01 16:59:27', 'admin', '2023-09-01 16:59:27'),
       (19, '127.0.0.1', '/api/v1/users/2', 'admin', '修改用户',
        'dev.macula.cloud.system.controller.SysUserController.updateUser()', 'PUT',
        '2 {\"roleIds\":[1,2,3,21],\"nickname\":\"系统管理员4\",\"avatar\":\"https://s2.loli.net/2022/04/07/gw1L2Z5sPtS8GIl.gif\",\"id\":2,\"username\":\"admin\"}',
        0, NULL, 'true', 'admin', '2023-09-01 19:56:54', 'admin', '2023-09-01 19:56:54'),
       (20, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"\",\"sort\":0,\"type\":\"MENU\",\"parentId\":174,\"path\":\"/hello\",\"component\":\"Layout\",\"name\":\"HELLO\",\"id\":223,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T20:09:06.385\"}',
        0, NULL, 'true', 'admin', '2023-09-01 20:09:06', 'admin', '2023-09-01 20:09:06'),
       (21, '127.0.0.1', '/api/v1/permissions/204,204', 'admin', '删除权限',
        'dev.macula.cloud.system.controller.SysPermissionController.deletePermissions()', 'DELETE', '204,204', 0, NULL,
        'true', 'admin', '2023-09-01 20:09:06', 'admin', '2023-09-01 20:09:06'),
       (22, '127.0.0.1', '/api/v1/roles/1/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '1 [174,223,222]', 0, NULL,
        'true', 'admin', '2023-09-01 20:09:38', 'admin', '2023-09-01 20:09:38'),
       (23, '127.0.0.1', '/api/v1/roles/21/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '21 [174,223,222]', 0, NULL,
        'true', 'admin', '2023-09-01 20:09:47', 'admin', '2023-09-01 20:09:47'),
       (24, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-home-filled\",\"sort\":0,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/home\",\"component\":\"Layout\",\"name\":\"首页\",\"id\":224,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T21:53:54.918\"}',
        0, NULL, 'true', 'admin', '2023-09-01 21:53:55', 'admin', '2023-09-01 21:53:55'),
       (25, '127.0.0.1', '/api/v1/menus/225', 'admin', '删除菜单',
        'dev.macula.cloud.system.controller.SysMenuController.deleteMenus()', 'DELETE', '225', 0, NULL, 'true', 'admin',
        '2023-09-01 21:54:27', 'admin', '2023-09-01 21:54:27'),
       (26, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-home-filled\",\"sort\":1,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/home\",\"component\":\"Layout\",\"name\":\"首页\",\"id\":224,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T21:54:35.902\"}',
        0, NULL, 'true', 'admin', '2023-09-01 21:54:36', 'admin', '2023-09-01 21:54:36'),
       (27, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-setting\",\"sort\":2,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/system\",\"component\":\"Layout\",\"name\":\"系统管理\",\"id\":1,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T21:54:39.714\"}',
        0, NULL, 'true', 'admin', '2023-09-01 21:54:40', 'admin', '2023-09-01 21:54:40'),
       (28, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-grid\",\"sort\":0,\"type\":\"MENU\",\"parentId\":224,\"path\":\"/dashboard\",\"component\":\"dashboard\",\"name\":\"控制台\",\"id\":226,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T21:56:23.386\"}',
        0, NULL, 'true', 'admin', '2023-09-01 21:56:23', 'admin', '2023-09-01 21:56:23'),
       (29, '127.0.0.1', '/api/v1/roles/1/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT',
        '1 [224,226,1,106,69,63,60,43,66,68,102,59]', 0, NULL, 'true', 'admin', '2023-09-01 21:57:21', 'admin',
        '2023-09-01 21:57:21'),
       (30, '127.0.0.1', '/api/v1/roles/2/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '2 [224,226]', 0, NULL, 'true',
        'admin', '2023-09-01 21:57:32', 'admin', '2023-09-01 21:57:32'),
       (31, '127.0.0.1', '/api/v1/roles/3/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '3 [224,226]', 0, NULL, 'true',
        'admin', '2023-09-01 21:57:38', 'admin', '2023-09-01 21:57:38'),
       (32, '127.0.0.1', '/api/v1/roles/21/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '21 [224,226,1,106,69,43,102]',
        0, NULL, 'true', 'admin', '2023-09-01 21:57:45', 'admin', '2023-09-01 21:57:45'),
       (33, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-grid\",\"sort\":0,\"type\":\"MENU\",\"parentId\":224,\"path\":\"/dashboard\",\"component\":\"common/home\",\"name\":\"控制台\",\"id\":226,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:06:17.733\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:06:18', 'admin', '2023-09-01 22:06:18'),
       (34, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-menu\",\"sort\":0,\"type\":\"MENU\",\"parentId\":224,\"path\":\"/dashboard\",\"component\":\"common/home\",\"name\":\"控制台\",\"id\":226,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:06:49.303\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:06:49', 'admin', '2023-09-01 22:06:49'),
       (35, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"\",\"sort\":1,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/dashboard\",\"component\":\"Layout\",\"name\":\"首页\",\"id\":227,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:10:05.959\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:10:06', 'admin', '2023-09-01 22:10:06'),
       (36, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-arrow-down\",\"sort\":2,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/2342\",\"component\":\"Layout\",\"name\":\"测试菜单\",\"id\":174,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:10:10.695\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:10:11', 'admin', '2023-09-01 22:10:11'),
       (37, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"\",\"sort\":0,\"type\":\"MENU\",\"parentId\":227,\"path\":\"/dashboard\",\"component\":\"common/home\",\"name\":\"控制台\",\"id\":228,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:10:36.767\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:10:37', 'admin', '2023-09-01 22:10:37'),
       (38, '127.0.0.1', '/api/v1/roles/1/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '1 [227,228,174,223,222]', 0,
        NULL, 'true', 'admin', '2023-09-01 22:10:48', 'admin', '2023-09-01 22:10:48'),
       (39, '127.0.0.1', '/api/v1/roles/2/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '2 [227,228]', 0, NULL, 'true',
        'admin', '2023-09-01 22:10:55', 'admin', '2023-09-01 22:10:55'),
       (40, '127.0.0.1', '/api/v1/roles/3/menus', 'admin', '分配角色的资源权限',
        'dev.macula.cloud.system.controller.SysRoleController.updateRoleMenus()', 'PUT', '3 [227,228,174,223,222]', 0,
        NULL, 'true', 'admin', '2023-09-01 22:11:06', 'admin', '2023-09-01 22:11:06'),
       (41, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-apple\",\"sort\":0,\"type\":\"MENU\",\"parentId\":174,\"path\":\"/hello\",\"component\":\"Layout\",\"name\":\"HELLO\",\"id\":223,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:11:18.334\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:11:18', 'admin', '2023-09-01 22:11:18'),
       (42, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-bowl\",\"sort\":10,\"type\":\"MENU\",\"parentId\":174,\"path\":\"/admin/echo\",\"component\":\"admin/echo\",\"name\":\"ECHO\",\"id\":222,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:11:25.613\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:11:26', 'admin', '2023-09-01 22:11:26'),
       (43, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-map-location\",\"sort\":2,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/2342\",\"component\":\"Layout\",\"name\":\"测试菜单\",\"id\":174,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:11:42.330\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:11:42', 'admin', '2023-09-01 22:11:42'),
       (44, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-home-filled\",\"sort\":1,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/dashboard\",\"component\":\"Layout\",\"name\":\"首页\",\"id\":227,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:11:54.262\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:11:54', 'admin', '2023-09-01 22:11:54'),
       (45, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-menu\",\"sort\":0,\"type\":\"MENU\",\"parentId\":227,\"path\":\"/dashboard\",\"component\":\"common/home\",\"name\":\"控制台\",\"id\":228,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:12:08.569\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:12:09', 'admin', '2023-09-01 22:12:09'),
       (46, '127.0.0.1', '/api/v1/menus', 'admin', '新增菜单',
        'dev.macula.cloud.system.controller.SysMenuController.addMenu()', 'POST',
        '{\"redirect\":\"\",\"visible\":1,\"perm\":\"\",\"icon\":\"el-icon-home-filled\",\"sort\":1,\"type\":\"CATALOG\",\"parentId\":0,\"path\":\"/home\",\"component\":\"Layout\",\"name\":\"首页\",\"id\":227,\"lastUpdateBy\":\"admin\",\"lastUpdateTime\":\"2023-09-01T22:15:59.191\"}',
        0, NULL, 'true', 'admin', '2023-09-01 22:15:59', 'admin', '2023-09-01 22:15:59');
/*!40000 ALTER TABLE `sys_log` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_menu_tenant`
--

DROP TABLE IF EXISTS `sys_menu_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu_tenant`
(
    `id`               bigint                                 NOT NULL AUTO_INCREMENT,
    `tenant_id`        bigint                                          DEFAULT NULL COMMENT '租户id',
    `parent_id`        bigint                                 NOT NULL COMMENT '父菜单ID',
    `type`             tinyint                                NOT NULL COMMENT '菜单类型(1：菜单；2：目录；3：外链；4：iFrame；5：按钮)',
    `name`             varchar(64) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '菜单名称',
    `path`             varchar(128) COLLATE utf8mb4_general_ci         DEFAULT '' COMMENT '路由路径(浏览器地址栏路径)',
    `component`        varchar(128) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '组件路径(vue页面完整路径，省略.vue后缀)',
    `perm`             varchar(50) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '按钮权限标识',
    `icon`             varchar(64) COLLATE utf8mb4_general_ci          DEFAULT '' COMMENT '菜单图标',
    `sort`             int                                             DEFAULT '0' COMMENT '排序',
    `visible`          tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用;1:开启)',
    `redirect`         varchar(128) COLLATE utf8mb4_general_ci         DEFAULT '' COMMENT '跳转路径',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    `full_page`        tinyint(1) DEFAULT NULL COMMENT '整页路径',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=229 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单管理';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu_tenant`
--

LOCK
TABLES `sys_menu_tenant` WRITE;
/*!40000 ALTER TABLE `sys_menu_tenant` DISABLE KEYS */;
INSERT INTO `sys_menu_tenant` (`id`, `tenant_id`, `parent_id`, `type`, `name`, `path`, `component`, `perm`, `icon`,
                               `sort`, `visible`, `redirect`, `create_by`, `create_time`, `last_update_by`,
                               `last_update_time`, `full_page`)
VALUES (1, 1, 0, 2, '系统管理', '/system', 'Layout', '', 'el-icon-setting', 2, 1, '', '*SYSADM', '2023-01-17 13:44:39',
        'admin', '2023-09-01 21:54:40', 0),
       (43, 1, 1, 1, '菜单管理', '/system/menu', 'system/menu', '', 'el-icon-fold', 4, 1, '', '*SYSADM',
        '2023-01-17 13:46:07', 'admin', '2023-03-03 13:12:42', 0),
       (59, 1, 1, 1, '审计日志', '/system/log', 'system/log', '审计日志', 'el-icon-credit-card', 9, 1, '',
        'anonymousUser', '2023-01-18 17:33:33', 'admin', '2023-02-06 16:08:31', 0),
       (60, 1, 1, 1, '部门管理', '/system/dept', 'system/dept', '', 'sc-icon-organization', 3, 1, '', 'admin',
        '2023-01-19 10:25:05', 'admin', '2023-03-03 13:11:40', 0),
       (63, 1, 1, 1, '用户管理', '/system/user', 'system/user', '', 'el-icon-avatar', 2, 1, '', 'admin',
        '2023-01-29 14:16:34', 'yh', '2023-02-16 09:54:58', 0),
       (66, 1, 1, 1, '角色管理', '/system/role', 'system/role', '', 'el-icon-lock', 5, 1, '', 'admin',
        '2023-02-06 11:13:40', 'admin', '2023-03-03 13:12:47', 0),
       (68, 1, 66, 5, '添加', '', '', 'role::add', '', 0, 1, '', 'admin', '2023-02-06 19:04:37', 'admin',
        '2023-03-02 10:36:14', 0),
       (69, 1, 1, 1, '应用管理', '/system/application', 'system/application', '', 'el-icon-coffee-cup', 1, 1, '',
        'admin', '2023-02-07 09:36:12', 'jje', '2023-02-16 18:26:04', 0),
       (82, 1, 1, 4, 'iframe菜单', 'http://10.94.108.102:5800/#/dashboard', 'test/temp', '', 'el-icon-box', 1000, 1, '',
        'yh', '2023-02-09 10:49:07', 'yh', '2023-02-16 09:54:28', 0),
       (95, 1, 1, 3, '外链菜单-百度', 'https://www.baidu.com', '', '', 'el-icon-check', 1001, 1, '', 'yh',
        '2023-02-16 09:53:53', 'yh', '2023-02-17 10:59:44', 0),
       (102, 1, 1, 1, '字典管理', '/system/dict', 'system/dict', '', 'el-icon-reading', 6, 1, '', 'jje',
        '2023-02-20 17:14:45', 'admin', '2023-03-03 13:12:52', 0),
       (106, 1, 1, 1, '租户管理', '/system/tenant', 'system/tenant', '', 'el-icon-home-filled', 0, 1, '', 'yh',
        '2023-02-27 13:56:53', 'admin', '2023-03-08 16:16:53', 0),
       (171, 1, 66, 5, '删除', '', '', 'role:del', '', 0, 1, '', 'admin', '2023-03-02 10:35:35', 'admin',
        '2023-03-02 10:36:00', NULL),
       (174, 12, 0, 2, '测试菜单', '/2342', 'Layout', '', 'el-icon-map-location', 2, 1, '', 'admin',
        '2023-03-02 16:21:33', 'admin', '2023-09-01 22:11:42', NULL),
       (222, 12, 174, 1, 'ECHO', '/admin/echo', 'admin/echo', '', 'el-icon-bowl', 10, 1, '', 'admin',
        '2023-03-03 16:05:00', 'admin', '2023-09-01 22:11:26', NULL),
       (223, 12, 174, 1, 'HELLO', '/hello', 'Layout', '', 'el-icon-apple', 0, 1, '', 'admin', '2023-09-01 20:08:50',
        'admin', '2023-09-01 22:11:18', NULL),
       (224, 1, 0, 2, '首页', '/home', 'Layout', '', 'el-icon-home-filled', 1, 1, '', 'admin', '2023-09-01 21:53:14',
        'admin', '2023-09-01 21:54:36', NULL),
       (226, 1, 224, 1, '控制台', '/dashboard', 'common/home', '', 'el-icon-menu', 0, 1, '', 'admin',
        '2023-09-01 21:54:48', 'admin', '2023-09-01 22:06:49', NULL),
       (227, 12, 0, 2, '首页', '/home', 'Layout', '', 'el-icon-home-filled', 1, 1, '', 'admin', '2023-09-01 22:09:50',
        'admin', '2023-09-01 22:15:59', NULL),
       (228, 12, 227, 1, '控制台', '/dashboard', 'common/home', '', 'el-icon-menu', 0, 1, '', 'admin',
        '2023-09-01 22:10:18', 'admin', '2023-09-01 22:12:09', NULL);
/*!40000 ALTER TABLE `sys_menu_tenant` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_oauth2_client`
--

DROP TABLE IF EXISTS `sys_oauth2_client`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oauth2_client`
(
    `id`                              bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `client_id`                       varchar(100) NOT NULL COMMENT 'oauth2客户端id',
    `client_id_issued_at`             timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '客户端创建时间',
    `client_secret`                   varchar(200) NOT NULL COMMENT '客户端密码',
    `client_secret_expires_at`        timestamp NULL DEFAULT NULL COMMENT '客户端密码过期时间',
    `client_name`                     varchar(200) NOT NULL COMMENT '客户端名称',
    `client_authentication_methods`   varchar(255) NOT NULL COMMENT '客户端认证方式',
    `authorization_grant_types`       varchar(255) NOT NULL COMMENT '客户端授权方式',
    `redirect_uris`                   varchar(255) NOT NULL COMMENT '客户端允许重定向的uri',
    `scopes`                          varchar(255) NOT NULL COMMENT '客户端允许的scope 来自role表',
    `require_proof_key`               tinyint(1) DEFAULT '0' COMMENT '客户端是否需要证明密钥',
    `require_authorization_consent`   tinyint(1) DEFAULT '0' COMMENT '客户端是否需要授权确认页面',
    `jwk_set_url`                     varchar(255)          DEFAULT NULL COMMENT 'jwkSet url',
    `signing_algorithm`               varchar(255)          DEFAULT NULL COMMENT '支持的签名算法',
    `authorization_code_time_to_live` bigint                DEFAULT NULL COMMENT 'authorization_code 有效时间(秒)',
    `access_token_time_to_live`       bigint                DEFAULT NULL COMMENT 'access_token 有效时间(秒)',
    `token_format`                    varchar(255)          DEFAULT NULL COMMENT 'token 格式  jwt、opaque',
    `reuse_refresh_tokens`            tinyint(1) DEFAULT '1' COMMENT '是否重用 refresh_token',
    `refresh_token_time_to_live`      bigint                DEFAULT NULL COMMENT 'refresh_token 有效时间(秒)',
    `id_token_signature_algorithm`    varchar(255)          DEFAULT NULL COMMENT 'oidc id_token 签名算法',
    `user_type`                       varchar(25)  NOT NULL COMMENT '用户类型(member,employee)',
    `create_by`                       varchar(50)  NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`                     datetime     NOT NULL COMMENT '创建时间',
    `last_update_by`                  varchar(50)  NOT NULL DEFAULT '*SYSADM' COMMENT '最后更新人',
    `last_update_time`                datetime     NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_drwlno5wbex09l0acnnwecp7r` (`client_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oauth2_client`
--

LOCK
TABLES `sys_oauth2_client` WRITE;
/*!40000 ALTER TABLE `sys_oauth2_client` DISABLE KEYS */;
INSERT INTO `sys_oauth2_client` (`id`, `client_id`, `client_id_issued_at`, `client_secret`, `client_secret_expires_at`,
                                 `client_name`, `client_authentication_methods`, `authorization_grant_types`,
                                 `redirect_uris`, `scopes`, `require_proof_key`, `require_authorization_consent`,
                                 `jwk_set_url`, `signing_algorithm`, `authorization_code_time_to_live`,
                                 `access_token_time_to_live`, `token_format`, `reuse_refresh_tokens`,
                                 `refresh_token_time_to_live`, `id_token_signature_algorithm`, `user_type`, `create_by`,
                                 `create_time`, `last_update_by`, `last_update_time`)
VALUES (1, 'e2fa7e64-249b-46f0-ae1d-797610e88615', '2022-04-12 06:21:20',
        '{bcrypt}$2a$10$uHWdt9Ackncw6s5BJlYO9OOdpD3Q44aan0SjttGRCZU2qvvk3fAZO', NULL, 'felord',
        'client_secret_basic,client_secret_post', 'authorization_code,client_credentials,refresh_token',
        'http://127.0.0.1:8083/login/oauth2/code/felord', 'message.read,message.write,userinfo', 0, 1, NULL, NULL, 300,
        43200, 'self-contained', 1, 2592000, 'RS256', 'employee', '*SYSADM', '2023-04-12 14:24:40', '*SYSADM',
        '2023-04-12 14:24:44'),
       (2, 'e4da4a32-592b-46f0-ae1d-784310e88423', '2022-04-12 06:21:20',
        '{bcrypt}$2a$10$uHWdt9Ackncw6s5BJlYO9OOdpD3Q44aan0SjttGRCZU2qvvk3fAZO', NULL, 'macula-cloud-gateway',
        'client_secret_basic,client_secret_post', 'authorization_code,client_credentials,refresh_token,password,sms',
        'http://127.0.0.1:8083/login/oauth2/code/felord', 'message.read,message.write,userinfo', 0, 1, NULL, NULL, 300,
        43200, 'reference', 1, 2592000, 'RS256', 'employee', '*SYSADM', '2023-04-12 14:24:40', '*SYSADM',
        '2023-04-12 14:24:44');
/*!40000 ALTER TABLE `sys_oauth2_client` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_permission_tenant`
--

DROP TABLE IF EXISTS `sys_permission_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission_tenant`
(
    `id`               bigint NOT NULL AUTO_INCREMENT,
    `name`             varchar(64) COLLATE utf8mb4_general_ci  DEFAULT NULL,
    `menu_id`          int                                     DEFAULT NULL,
    `url_perm`         varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci  DEFAULT NULL,
    `create_time`      datetime                                DEFAULT NULL,
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci  DEFAULT NULL,
    `last_update_time` datetime                                DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `urlPerm_unique` (`url_perm`)
) ENGINE=InnoDB AUTO_INCREMENT=205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission_tenant`
--

LOCK
TABLES `sys_permission_tenant` WRITE;
/*!40000 ALTER TABLE `sys_permission_tenant` DISABLE KEYS */;
INSERT INTO `sys_permission_tenant` (`id`, `name`, `menu_id`, `url_perm`, `tenant_id`, `create_by`, `create_time`,
                                     `last_update_by`,
                              `last_update_time`)
VALUES (198, '添加', 68, 'GET:/add', 1, 'admin', '2023-03-02 10:36:15', 'admin', '2023-03-02 10:36:15');
/*!40000 ALTER TABLE `sys_permission_tenant` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role`
(
    `id`               bigint                                 NOT NULL AUTO_INCREMENT,
    `name`             varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '角色名称',
    `code`             varchar(32) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '角色编码',
    `sort`             int                                             DEFAULT NULL COMMENT '显示顺序',
    `status`           tinyint                                         DEFAULT '1' COMMENT '角色状态(1-正常；0-停用)',
    `deleted`          tinyint                                NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0-未删除；1-已删除)',
    `data_scope`       tinyint                                         DEFAULT '0' COMMENT '数据权限(1-全部可见；2-本人可见；3-所在部门可见；4-所在部门及子级可见；5-选择部门可见；6-自定义)',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK
TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` (`id`, `name`, `code`, `sort`, `status`, `deleted`, `data_scope`, `create_by`, `create_time`,
                        `last_update_by`, `last_update_time`)
VALUES (1, '超级管理员', 'ROOT', 1, 1, 0, 1, '*SYSADM', '2021-05-21 14:56:51', 'yh', '2023-02-17 13:41:26'),
       (2, '系统管理员', 'ADMIN', 1, 1, 0, 1, '*SYSADM', '2021-03-25 12:39:54', 'yh', '2023-02-16 14:47:52'),
       (3, '访问游客', 'GUEST', 3, 1, 0, 3, '*SYSADM', '2021-05-26 15:49:05', 'yh', '2023-02-20 18:22:29'),
       (21, '普通管理员', 'USER', 4, 1, 0, 9, 'anonymousUser', '2022-12-27 15:48:04', 'admin', '2023-02-27 11:12:42');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu`
(
    `role_id`   bigint NOT NULL COMMENT '角色ID',
    `menu_id` bigint NOT NULL COMMENT '菜单ID'
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK
TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (1, 1),
       (1, 43),
       (1, 59),
       (1, 60),
       (1, 63),
       (1, 66),
       (1, 68),
       (1, 69),
       (1, 102),
       (1, 106),
       (1, 174),
       (1, 222),
       (1, 223),
       (1, 224),
       (1, 226),
       (1, 227),
       (1, 228),
       (2, 224),
       (2, 226),
       (2, 227),
       (2, 228),
       (3, 174),
       (3, 222),
       (3, 223),
       (3, 224),
       (3, 226),
       (3, 227),
       (3, 228),
       (21, 1),
       (21, 43),
       (21, 69),
       (21, 102),
       (21, 106),
       (21, 174),
       (21, 222),
       (21, 223),
       (21, 224),
       (21, 226);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission`
(
    `role_id`       int NOT NULL,
    `permission_id` int NOT NULL,
    PRIMARY KEY (`permission_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK
TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
VALUES (1, 198),
       (41, 198),
       (42, 198),
       (41, 203),
       (42, 203),
       (42, 205);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_tenant_info`
--

DROP TABLE IF EXISTS `sys_tenant_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant_info`
(
    `id`               int                                                          NOT NULL AUTO_INCREMENT,
    `name`             varchar(50) COLLATE utf8mb4_bin                                       DEFAULT NULL,
    `create_by`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                                                     NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                                                     NOT NULL COMMENT '更新时间',
    `code`             varchar(50) COLLATE utf8mb4_bin                                       DEFAULT NULL,
    `description`      varchar(100) COLLATE utf8mb4_bin                                      DEFAULT NULL,
    `supervisor`       varchar(50) COLLATE utf8mb4_bin                                       DEFAULT NULL COMMENT '绑定了多个负责人，暂时不用，后续删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_tenant_info`
--

LOCK
TABLES `sys_tenant_info` WRITE;
/*!40000 ALTER TABLE `sys_tenant_info` DISABLE KEYS */;
INSERT INTO `sys_tenant_info` (`id`, `name`, `create_by`, `create_time`, `last_update_by`, `last_update_time`, `code`,
                               `description`, `supervisor`)
VALUES (1, 'Macula Cloud', 'anonymousUser', '2023-02-21 15:36:08', 'admin', '2023-09-01 14:49:08', 'macula-cloud',
        '技术平台', 'zlh'),
       (12, 'Macula Samples', 'admin', '2023-02-28 15:04:21', 'admin', '2023-09-01 14:49:37', 'macula-samples',
        '示例平台', NULL);
/*!40000 ALTER TABLE `sys_tenant_info` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_tenant_user`
--

DROP TABLE IF EXISTS `sys_tenant_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant_user`
(
    `user_id`   bigint NOT NULL COMMENT '用户id',
    `tenant_id` bigint NOT NULL COMMENT '租户id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_tenant_user`
--

LOCK
TABLES `sys_tenant_user` WRITE;
/*!40000 ALTER TABLE `sys_tenant_user` DISABLE KEYS */;
INSERT INTO `sys_tenant_user` (`user_id`, `tenant_id`)
VALUES (2, 8),
       (116, 8),
       (116, 9),
       (2, 9),
       (117, 9),
       (2, 10),
       (2, 11),
       (2, 13),
       (2, 14),
       (2, 1),
       (117, 1),
       (118, 1),
       (117, 12),
       (116, 12);
/*!40000 ALTER TABLE `sys_tenant_user` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user`
(
    `id`               int                                    NOT NULL AUTO_INCREMENT,
    `username`         varchar(64) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '用户名',
    `nickname`         varchar(64) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '昵称',
    `gender`           tinyint(1) DEFAULT '1' COMMENT '性别((1:男;2:女))',
    `password`         varchar(100) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '密码',
    `dept_id`          int                                             DEFAULT NULL COMMENT '部门ID',
    `avatar`           varchar(255) COLLATE utf8mb4_general_ci         DEFAULT '' COMMENT '用户头像',
    `mobile`           varchar(20) COLLATE utf8mb4_general_ci          DEFAULT NULL COMMENT '联系方式',
    `status`           tinyint(1) DEFAULT '1' COMMENT '用户状态((1:正常;0:禁用))',
    `email`            varchar(128) COLLATE utf8mb4_general_ci         DEFAULT NULL COMMENT '用户邮箱',
    `deleted`          tinyint(1) DEFAULT '0' COMMENT '逻辑删除标识(0:未删除;1:已删除)',
    `create_by`        varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '创建人',
    `create_time`      datetime                               NOT NULL COMMENT '创建时间',
    `last_update_by`   varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '*SYSADM' COMMENT '更新人',
    `last_update_time` datetime                               NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                `login_name` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK
TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `gender`, `password`, `dept_id`, `avatar`, `mobile`, `status`,
                        `email`, `deleted`, `create_by`, `create_time`, `last_update_by`, `last_update_time`)
VALUES (1, 'root', '有来技术', 0, '{bcrypt}$2a$10$xVWsNOhHrCxh5UbpCE7/HuJ.PAOKcYAqRxD2CO2nVnJS.IAXkr5aq', NULL,
        'https://s2.loli.net/2022/04/07/gw1L2Z5sPtS8GIl.gif', '17621590365', 1, 'youlaitech@163.com', 0, '*SYSADM',
        '2019-10-10 13:41:22', '*SYSADM', '2019-10-10 13:41:22'),
       (2, 'admin', '系统管理员4', 1, '{bcrypt}$2a$10$RPN.7c8mBjDYbUfajFGq0.26qc1KCfl80KwgqjKK3LKfE78/y//jO', 180,
        'https://s2.loli.net/2022/04/07/gw1L2Z5sPtS8GIl.gif', '17621210366', 1, '', 0, '*SYSADM', '2019-10-10 13:41:22',
        'admin', '2023-09-01 19:56:53'),
       (40, 'test001', '邹艳', 1, '{bcrypt}$2a$10$OKtTsZbc8zDOfz059V5D1O1M51Ge.aoGVUZww3hIFtbw1GOWyfUWW', 1,
        'http://dummyimage.com/100x100', '17025517201', 1, 'j.gfwgrn@qq.com', 0, 'admin', '2023-03-24 00:12:27',
        'admin', '2023-03-24 00:12:27'),
       (116, 'yh', 'yh', 1, 'a2e15211b81b83d15b3adb5522d5834d', 182, '', NULL, 1, NULL, 0, '*SYSADM',
        '2023-02-07 18:41:40', 'admin', '2023-03-06 18:16:48'),
       (117, 'zlhuan', 'zlhuan', 1, '21232f297a57a5a743894a0e4a801fc3', 180, '', NULL, 1, NULL, 0, '*SYSADM',
        '2023-02-08 14:24:38', 'zlhuan', '2023-02-17 10:46:01'),
       (118, 'jje', 'jje', 1, 'a8b6ee9cb18146b60a33192f9beff917', 182, '', NULL, 1, NULL, 0, '*SYSADM',
        '2023-02-08 14:25:06', 'zlhuan', '2023-02-17 10:46:17'),
       (135, 'zhanglonghuan', 'zlh', 1, '21232f297a57a5a743894a0e4a801fc3', 190, '', NULL, 1, NULL, 0, 'zlhuan',
        '2023-02-17 10:52:33', 'zlhuan', '2023-02-17 14:50:26'),
       (136, 'fsd', 'sdfda', 1, '900150983cd24fb0d6963f7d28e17f72', 190, '', NULL, 1, NULL, 1, 'admin',
        '2023-03-06 19:16:18', 'admin', '2023-03-06 19:16:44');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role`
(
    `user_id` int NOT NULL COMMENT '用户ID',
    `role_id` int NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK
TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (1, 1),
       (2, 1),
       (2, 2),
       (2, 3),
       (2, 21),
       (40, 0),
       (116, 21),
       (117, 1),
       (118, 1),
       (135, 21),
       (136, 1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK
TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-09-02 21:46:58
