-- MySQL dump 10.13  Distrib 8.1.0, for macos13.3 (x86_64)
--
-- Host: 127.0.0.1    Database: macula-tinyid
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
-- Table structure for table `tiny_id_info`
--

DROP TABLE IF EXISTS `tiny_id_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tiny_id_info`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `biz_type`    varchar(63) NOT NULL DEFAULT '' COMMENT '业务类型，唯一',
    `begin_id`    bigint      NOT NULL DEFAULT '0' COMMENT '开始id，仅记录初始值，无其他含义。初始化时begin_id和max_id应相同',
    `max_id`      bigint      NOT NULL DEFAULT '0' COMMENT '当前最大id',
    `step`        int                  DEFAULT '0' COMMENT '步长',
    `delta`       int         NOT NULL DEFAULT '1' COMMENT '每次id增量',
    `remainder`   int         NOT NULL DEFAULT '0' COMMENT '余数',
    `create_time` datetime    NOT NULL DEFAULT '2010-01-01 00:00:00' COMMENT '创建时间',
    `update_time` datetime    NOT NULL DEFAULT '2010-01-01 00:00:00' COMMENT '更新时间',
    `version`     bigint      NOT NULL DEFAULT '0' COMMENT '版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_biz_type` (`biz_type`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb3 COMMENT='id信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tiny_id_info`
--

LOCK
TABLES `tiny_id_info` WRITE;
/*!40000 ALTER TABLE `tiny_id_info` DISABLE KEYS */;
INSERT INTO `tiny_id_info` (`id`, `biz_type`, `begin_id`, `max_id`, `step`, `delta`, `remainder`, `create_time`,
                            `update_time`, `version`)
VALUES (1, 'test', 1, 3301, 100, 1, 0, '2018-07-21 23:52:58', '2023-07-18 10:34:24', 28),
       (2, 'test_odd', 1, 201, 100, 2, 1, '2018-07-21 23:52:58', '2022-06-28 16:37:19', 5);
/*!40000 ALTER TABLE `tiny_id_info` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `tiny_id_token`
--

DROP TABLE IF EXISTS `tiny_id_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tiny_id_token`
(
    `id`          int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `token`       varchar(255) NOT NULL DEFAULT '' COMMENT 'token',
    `biz_type`    varchar(63)  NOT NULL DEFAULT '' COMMENT '此token可访问的业务类型标识',
    `remark`      varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
    `create_time` timestamp    NOT NULL DEFAULT '2009-12-31 16:00:00' COMMENT '创建时间',
    `update_time` timestamp    NOT NULL DEFAULT '2009-12-31 16:00:00' COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb3 COMMENT='token信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tiny_id_token`
--

LOCK
TABLES `tiny_id_token` WRITE;
/*!40000 ALTER TABLE `tiny_id_token` DISABLE KEYS */;
INSERT INTO `tiny_id_token` (`id`, `token`, `biz_type`, `remark`, `create_time`, `update_time`)
VALUES (1, '0f673adf80504e2eaa552f5d791b644c', 'test', '1', '2017-12-14 08:36:46', '2017-12-14 08:36:48'),
       (2, '0f673adf80504e2eaa552f5d791b644c', 'test_odd', '1', '2017-12-14 08:36:46', '2017-12-14 08:36:48');
/*!40000 ALTER TABLE `tiny_id_token` ENABLE KEYS */;
UNLOCK
TABLES;
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

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-08-31 15:52:54
