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

package dev.macula.cloud.tinyid.dao.impl;

import dev.macula.cloud.tinyid.dao.TinyIdTokenDAO;
import dev.macula.cloud.tinyid.dao.entity.TinyIdToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author du_imba
 */
@Repository
public class TinyIdTokenDAOImpl implements TinyIdTokenDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<TinyIdToken> selectAll() {
        String sql = "select id, token, biz_type, remark, " + "create_time, update_time from tiny_id_token";
        return jdbcTemplate.query(sql, new TinyIdTokenRowMapper());
    }

    public static class TinyIdTokenRowMapper implements RowMapper<TinyIdToken> {

        @Override
        public TinyIdToken mapRow(ResultSet resultSet, int i) throws SQLException {
            TinyIdToken token = new TinyIdToken();
            token.setId(resultSet.getInt("id"));
            token.setToken(resultSet.getString("token"));
            token.setBizType(resultSet.getString("biz_type"));
            token.setRemark(resultSet.getString("remark"));
            token.setCreateTime(resultSet.getDate("create_time"));
            token.setUpdateTime(resultSet.getDate("update_time"));
            return token;
        }
    }
}
