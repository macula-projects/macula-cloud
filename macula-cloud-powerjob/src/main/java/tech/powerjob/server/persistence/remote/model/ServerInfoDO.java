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

package tech.powerjob.server.persistence.remote.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

/**
 * 服务器信息表（用于分配服务器唯一ID）
 *
 * @author tjq
 * @since 2020/4/15
 */
@Data
@Entity
@NoArgsConstructor
@Table(uniqueConstraints = {@UniqueConstraint(name = "uidx01_server_info", columnNames = "ip")},
    indexes = {@Index(name = "idx01_server_info", columnList = "gmtModified")})
public class ServerInfoDO {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private Long id;
    /**
     * 服务器IP地址
     */
    private String ip;

    private Date gmtCreate;

    private Date gmtModified;

    public ServerInfoDO(String ip) {
        this.ip = ip;
        this.gmtCreate = new Date();
        this.gmtModified = this.gmtCreate;
    }
}
