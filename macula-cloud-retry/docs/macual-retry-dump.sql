-- MySQL dump 10.13  Distrib 8.1.0, for macos13.3 (x86_64)
--
-- Host: 127.0.0.1    Database: macula-retry
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
-- Table structure for table `group_config`
--

DROP TABLE IF EXISTS `group_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_config`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name`        varchar(64)  NOT NULL DEFAULT '' COMMENT '组名称',
    `description`       varchar(256) NOT NULL COMMENT '组描述',
    `group_status`      tinyint      NOT NULL DEFAULT '0' COMMENT '组状态 0、未启用 1、启用',
    `version`           int          NOT NULL COMMENT '版本号',
    `group_partition`   int          NOT NULL COMMENT '分区',
    `route_key`         tinyint      NOT NULL COMMENT '路由策略',
    `id_generator_mode` tinyint      NOT NULL DEFAULT '1' COMMENT '唯一id生成模式 默认号段模式',
    `init_scene`        tinyint      NOT NULL DEFAULT '0' COMMENT '是否初始化场景 0:否 1:是',
    `create_dt`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`group_name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `group_config`
--

LOCK
TABLES `group_config` WRITE;
/*!40000 ALTER TABLE `group_config` DISABLE KEYS */;
INSERT INTO `group_config` (`id`, `group_name`, `description`, `group_status`, `version`, `group_partition`,
                            `route_key`, `id_generator_mode`, `init_scene`, `create_dt`, `update_dt`)
VALUES (3, 'macula', 'example', 1, 1, 0, 1, 1, 1, '2023-07-26 11:36:31', '2023-07-26 11:36:30');
/*!40000 ALTER TABLE `group_config` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `notify_config`
--

DROP TABLE IF EXISTS `notify_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notify_config`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name`       varchar(64)  NOT NULL COMMENT '组名称',
    `notify_type`      tinyint      NOT NULL DEFAULT '0' COMMENT '通知类型 1、钉钉 2、邮件 3、企业微信',
    `notify_attribute` varchar(512) NOT NULL COMMENT '配置属性',
    `notify_threshold` int          NOT NULL DEFAULT '0' COMMENT '通知阈值',
    `notify_scene`     tinyint      NOT NULL DEFAULT '0' COMMENT '通知场景',
    `description`      varchar(256) NOT NULL DEFAULT '' COMMENT '描述',
    `create_dt`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY                `idx_group_name` (`group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notify_config`
--

LOCK
TABLES `notify_config` WRITE;
/*!40000 ALTER TABLE `notify_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `notify_config` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `retry_dead_letter_0`
--

DROP TABLE IF EXISTS `retry_dead_letter_0`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `retry_dead_letter_0`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `unique_id`     varchar(64)  NOT NULL COMMENT '同组下id唯一',
    `group_name`    varchar(64)  NOT NULL COMMENT '组名称',
    `scene_name`    varchar(64)  NOT NULL COMMENT '场景id',
    `idempotent_id` varchar(64)  NOT NULL COMMENT '幂等id',
    `biz_no`        varchar(64)  NOT NULL DEFAULT '' COMMENT '业务编号',
    `executor_name` varchar(512) NOT NULL DEFAULT '' COMMENT '执行器名称',
    `args_str`      text         NOT NULL COMMENT '执行方法参数',
    `ext_attrs`     text         NOT NULL COMMENT '扩展字段',
    `task_type`     tinyint      NOT NULL DEFAULT '1' COMMENT '任务类型 1、重试数据 2、回调数据',
    `create_dt`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_unique_id` (`group_name`,`unique_id`),
    KEY             `idx_group_name_scene_name` (`group_name`,`scene_name`),
    KEY             `idx_idempotent_id` (`idempotent_id`),
    KEY             `idx_biz_no` (`biz_no`),
    KEY             `idx_create_dt` (`create_dt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='死信队列表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `retry_dead_letter_0`
--

LOCK
TABLES `retry_dead_letter_0` WRITE;
/*!40000 ALTER TABLE `retry_dead_letter_0` DISABLE KEYS */;
/*!40000 ALTER TABLE `retry_dead_letter_0` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `retry_task_0`
--

DROP TABLE IF EXISTS `retry_task_0`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `retry_task_0`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `unique_id`       varchar(64)  NOT NULL COMMENT '同组下id唯一',
    `group_name`      varchar(64)  NOT NULL COMMENT '组名称',
    `scene_name`      varchar(64)  NOT NULL COMMENT '场景名称',
    `idempotent_id`   varchar(64)  NOT NULL COMMENT '幂等id',
    `biz_no`          varchar(64)  NOT NULL DEFAULT '' COMMENT '业务编号',
    `executor_name`   varchar(512) NOT NULL DEFAULT '' COMMENT '执行器名称',
    `args_str`        text         NOT NULL COMMENT '执行方法参数',
    `ext_attrs`       text         NOT NULL COMMENT '扩展字段',
    `next_trigger_at` datetime     NOT NULL COMMENT '下次触发时间',
    `retry_count`     int          NOT NULL DEFAULT '0' COMMENT '重试次数',
    `retry_status`    tinyint      NOT NULL DEFAULT '0' COMMENT '重试状态 0、重试中 1、成功 2、最大重试次数',
    `task_type`       tinyint      NOT NULL DEFAULT '1' COMMENT '任务类型 1、重试数据 2、回调数据',
    `create_dt`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_unique_id` (`group_name`,`unique_id`),
    KEY               `idx_group_name_scene_name` (`group_name`,`scene_name`),
    KEY               `idx_retry_status` (`retry_status`),
    KEY               `idx_idempotent_id` (`idempotent_id`),
    KEY               `idx_biz_no` (`biz_no`),
    KEY               `idx_create_dt` (`create_dt`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `retry_task_0`
--

LOCK
TABLES `retry_task_0` WRITE;
/*!40000 ALTER TABLE `retry_task_0` DISABLE KEYS */;
/*!40000 ALTER TABLE `retry_task_0` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `retry_task_log`
--

DROP TABLE IF EXISTS `retry_task_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `retry_task_log`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `unique_id`     varchar(64)  NOT NULL COMMENT '同组下id唯一',
    `group_name`    varchar(64)  NOT NULL COMMENT '组名称',
    `scene_name`    varchar(64)  NOT NULL COMMENT '场景名称',
    `idempotent_id` varchar(64)  NOT NULL COMMENT '幂等id',
    `biz_no`        varchar(64)  NOT NULL DEFAULT '' COMMENT '业务编号',
    `executor_name` varchar(512) NOT NULL DEFAULT '' COMMENT '执行器名称',
    `args_str`      text         NOT NULL COMMENT '执行方法参数',
    `ext_attrs`     text         NOT NULL COMMENT '扩展字段',
    `retry_status`  tinyint      NOT NULL DEFAULT '0' COMMENT '重试状态 0、重试中 1、成功 2、最大次数',
    `task_type`     tinyint      NOT NULL DEFAULT '1' COMMENT '任务类型 1、重试数据 2、回调数据',
    `create_dt`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY             `idx_group_name_scene_name` (`group_name`,`scene_name`),
    KEY             `idx_retry_status` (`retry_status`),
    KEY             `idx_idempotent_id` (`idempotent_id`),
    KEY             `idx_unique_id` (`unique_id`),
    KEY             `idx_biz_no` (`biz_no`),
    KEY             `idx_create_dt` (`create_dt`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务日志基础信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `retry_task_log`
--

LOCK
TABLES `retry_task_log` WRITE;
/*!40000 ALTER TABLE `retry_task_log` DISABLE KEYS */;
INSERT INTO `retry_task_log` (`id`, `unique_id`, `group_name`, `scene_name`, `idempotent_id`, `biz_no`, `executor_name`,
                              `args_str`, `ext_attrs`, `retry_status`, `task_type`, `create_dt`)
VALUES (1, '202307261138481', 'macula', 'test', '0e7cb29baade326a0222d96456e533d1', '',
        'dev.macula.example.retry.controller.RetryDemoController', '[\"error\"]', '', 1, 1, '2023-07-26 11:38:49');
/*!40000 ALTER TABLE `retry_task_log` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `retry_task_log_message`
--

DROP TABLE IF EXISTS `retry_task_log_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `retry_task_log_message`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name` varchar(64) NOT NULL COMMENT '组名称',
    `unique_id`  varchar(64) NOT NULL COMMENT '同组下id唯一',
    `create_dt`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `message`    text        NOT NULL COMMENT '异常信息',
    PRIMARY KEY (`id`),
    KEY          `idx_group_name_unique_id` (`group_name`,`unique_id`),
    KEY          `idx_create_dt` (`create_dt`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务调度日志信息记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `retry_task_log_message`
--

LOCK
TABLES `retry_task_log_message` WRITE;
/*!40000 ALTER TABLE `retry_task_log_message` DISABLE KEYS */;
INSERT INTO `retry_task_log_message` (`id`, `group_name`, `unique_id`, `create_dt`, `message`)
VALUES (1, 'macula', '202307261138481', '2023-07-26 11:39:33', ''),
       (2, 'macula', '202307261138481', '2023-07-26 11:39:52', ''),
       (3, 'macula', '202307261138481', '2023-07-26 11:40:12', ''),
       (4, 'macula', '202307261138481', '2023-07-26 11:40:33', ''),
       (5, 'macula', '202307261138481', '2023-07-26 11:40:52', ''),
       (6, 'macula', '202307261138481', '2023-07-26 11:41:12', ''),
       (7, 'macula', '202307261138481', '2023-07-26 11:41:32', ''),
       (8, 'macula', '202307261138481', '2023-07-26 11:41:52', ''),
       (9, 'macula', '202307261138481', '2023-07-26 11:42:12', ''),
       (10, 'macula', '202307261138481', '2023-07-26 11:42:32', ''),
       (11, 'macula', '202307261138481', '2023-07-26 11:42:52', ''),
       (12, 'macula', '202307261138481', '2023-07-26 11:43:12', ''),
       (13, 'macula', '202307261138481', '2023-07-26 11:43:32', ''),
       (14, 'macula', '202307261138481', '2023-07-26 11:43:52', ''),
       (15, 'macula', '202307261138481', '2023-07-26 11:44:12', ''),
       (16, 'macula', '202307261138481', '2023-07-26 11:44:32', ''),
       (17, 'macula', '202307261138481', '2023-07-26 11:44:52', ''),
       (18, 'macula', '202307261138481', '2023-07-26 11:45:12', ''),
       (19, 'macula', '202307261138481', '2023-07-26 11:45:32', ''),
       (20, 'macula', '202307261138481', '2023-07-26 11:45:52', ''),
       (21, 'macula', '202307261138481', '2023-07-26 11:46:12', ''),
       (22, 'macula', '202307261138481', '2023-07-26 11:46:32', ''),
       (23, 'macula', '202307261138481', '2023-07-26 11:46:52', ''),
       (24, 'macula', '202307261138481', '2023-07-26 11:47:12', ''),
       (25, 'macula', '202307261138481', '2023-07-26 11:47:32', ''),
       (26, 'macula', '202307261138481', '2023-07-26 11:47:52', ''),
       (27, 'macula', '202307261138481', '2023-07-26 11:48:12', ''),
       (28, 'macula', '202307261138481', '2023-07-26 11:48:32', ''),
       (29, 'macula', '202307261138481', '2023-07-26 11:48:52', ''),
       (30, 'macula', '202307261138481', '2023-07-26 11:49:12', ''),
       (31, 'macula', '202307261138481', '2023-07-26 11:49:32', ''),
       (32, 'macula', '202307261138481', '2023-07-26 11:49:52', ''),
       (33, 'macula', '202307261138481', '2023-07-26 11:50:12', ''),
       (34, 'macula', '202307261138481', '2023-07-26 11:50:32', ''),
       (35, 'macula', '202307261138481', '2023-07-26 11:50:52', ''),
       (36, 'macula', '202307261138481', '2023-07-26 11:51:12', ''),
       (37, 'macula', '202307261138481', '2023-07-26 11:51:32', ''),
       (38, 'macula', '202307261138481', '2023-07-26 11:51:52', ''),
       (39, 'macula', '202307261138481', '2023-07-26 11:52:12', ''),
       (40, 'macula', '202307261138481', '2023-07-26 11:52:30', '页面操作完成');
/*!40000 ALTER TABLE `retry_task_log_message` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `scene_config`
--

DROP TABLE IF EXISTS `scene_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scene_config`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene_name`       varchar(64)  NOT NULL COMMENT '场景名称',
    `group_name`       varchar(64)  NOT NULL COMMENT '组名称',
    `scene_status`     tinyint      NOT NULL DEFAULT '0' COMMENT '组状态 0、未启用 1、启用',
    `max_retry_count`  int          NOT NULL DEFAULT '5' COMMENT '最大重试次数',
    `back_off`         tinyint      NOT NULL DEFAULT '1' COMMENT '1、默认等级 2、固定间隔时间 3、CRON 表达式',
    `trigger_interval` varchar(16)  NOT NULL DEFAULT '' COMMENT '间隔时长',
    `deadline_request` bigint unsigned NOT NULL DEFAULT '60000' COMMENT 'Deadline Request 调用链超时 单位毫秒',
    `description`      varchar(256) NOT NULL DEFAULT '' COMMENT '描述',
    `create_dt`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name_scene_name` (`group_name`,`scene_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场景配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scene_config`
--

LOCK
TABLES `scene_config` WRITE;
/*!40000 ALTER TABLE `scene_config` DISABLE KEYS */;
INSERT INTO `scene_config` (`id`, `scene_name`, `group_name`, `scene_status`, `max_retry_count`, `back_off`,
                            `trigger_interval`, `deadline_request`, `description`, `create_dt`, `update_dt`)
VALUES (1, 'test', 'macula', 1, 21, 1, '', 60000, '自动初始化场景', '2023-07-26 11:38:48', '2023-07-26 11:38:48');
/*!40000 ALTER TABLE `scene_config` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `sequence_alloc`
--

DROP TABLE IF EXISTS `sequence_alloc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sequence_alloc`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name` varchar(64) NOT NULL DEFAULT '' COMMENT '组名称',
    `max_id`     bigint      NOT NULL DEFAULT '1' COMMENT '最大id',
    `step`       int         NOT NULL DEFAULT '100' COMMENT '步长',
    `update_dt`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`group_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号段模式序号ID分配表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sequence_alloc`
--

LOCK
TABLES `sequence_alloc` WRITE;
/*!40000 ALTER TABLE `sequence_alloc` DISABLE KEYS */;
INSERT INTO `sequence_alloc` (`id`, `group_name`, `max_id`, `step`, `update_dt`)
VALUES (1, 'macula', 101, 100, '2023-07-26 11:38:48');
/*!40000 ALTER TABLE `sequence_alloc` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `server_node`
--

DROP TABLE IF EXISTS `server_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server_node`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name`   varchar(64)  NOT NULL COMMENT '组名称',
    `host_id`      varchar(64)  NOT NULL COMMENT '主机id',
    `host_ip`      varchar(64)  NOT NULL COMMENT '机器ip',
    `context_path` varchar(256) NOT NULL DEFAULT '/' COMMENT '客户端上下文路径 server.servlet.context-path',
    `host_port`    int          NOT NULL COMMENT '机器端口',
    `expire_at`    datetime     NOT NULL COMMENT '过期时间',
    `node_type`    tinyint      NOT NULL COMMENT '节点类型 1、客户端 2、是服务端',
    `ext_attrs`    varchar(256)          DEFAULT '' COMMENT '扩展字段',
    `create_dt`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_host_id_host_ip` (`host_id`,`host_ip`),
    KEY            `idx_expire_at_node_type` (`expire_at`,`node_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1653 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务器节点';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `server_node`
--

LOCK
TABLES `server_node` WRITE;
/*!40000 ALTER TABLE `server_node` DISABLE KEYS */;
/*!40000 ALTER TABLE `server_node` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `shedlock`
--

DROP TABLE IF EXISTS `shedlock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shedlock`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`       varchar(64)  NOT NULL COMMENT '锁名称',
    `lock_until` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP (3) COMMENT '锁定时长',
    `locked_at`  timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '锁定时间',
    `locked_by`  varchar(255) NOT NULL COMMENT '锁定者',
    `create_dt`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='锁定表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shedlock`
--

LOCK
TABLES `shedlock` WRITE;
/*!40000 ALTER TABLE `shedlock` DISABLE KEYS */;
INSERT INTO `shedlock` (`id`, `name`, `lock_until`, `locked_at`, `locked_by`, `create_dt`, `update_dt`)
VALUES (1, 'clearOfflineNode', '2023-07-29 09:30:54.523', '2023-07-29 09:30:49.540', 'Rains-WorkBook.local',
        '2023-07-18 11:47:47', '2023-07-18 11:47:47'),
       (5, 'retryErrorMoreThreshold', '2023-07-26 05:55:00.005', '2023-07-26 05:44:00.005', 'Rains-WorkBook.local',
        '2023-07-18 14:11:00', '2023-07-18 14:11:00'),
       (6, 'retryTaskMoreThreshold', '2023-07-26 06:00:00.019', '2023-07-26 05:50:00.021', 'Rains-WorkBook.local',
        '2023-07-18 14:20:00', '2023-07-18 14:20:00'),
       (10, 'clearLog', '2023-07-26 06:00:00.198', '2023-07-26 05:00:00.199', 'Rains-WorkBook.local',
        '2023-07-25 09:00:00', '2023-07-25 09:00:00'),
       (11, 'clearFinishAndMoveDeadLetterRetryTask', '2023-07-26 05:01:00.198', '2023-07-26 05:00:00.199',
        'Rains-WorkBook.local', '2023-07-25 09:00:00', '2023-07-25 09:00:00');
/*!40000 ALTER TABLE `shedlock` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `system_user`
--

DROP TABLE IF EXISTS `system_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user`
(
    `id`        bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`  varchar(64)  NOT NULL COMMENT '账号',
    `password`  varchar(128) NOT NULL COMMENT '密码',
    `role`      tinyint      NOT NULL DEFAULT '0' COMMENT '角色：1-普通用户、2-管理员',
    `create_dt` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_user`
--

LOCK
TABLES `system_user` WRITE;
/*!40000 ALTER TABLE `system_user` DISABLE KEYS */;
INSERT INTO `system_user` (`id`, `username`, `password`, `role`, `create_dt`, `update_dt`)
VALUES (1, 'admin', '465c194afb65670f38322df087f0a9bb225cc257e43eb4ac5a0c98ef5b3173ac', 2, '2023-07-18 11:05:05',
        '2023-07-18 11:05:05');
/*!40000 ALTER TABLE `system_user` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `system_user_permission`
--

DROP TABLE IF EXISTS `system_user_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_permission`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name`     varchar(64) NOT NULL COMMENT '组名称',
    `system_user_id` bigint      NOT NULL COMMENT '系统用户id',
    `create_dt`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_dt`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name_system_user_id` (`group_name`,`system_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_user_permission`
--

LOCK
TABLES `system_user_permission` WRITE;
/*!40000 ALTER TABLE `system_user_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_user_permission` ENABLE KEYS */;
UNLOCK
TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*
 * Copyright (c) 2023 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-08-31 15:56:07
