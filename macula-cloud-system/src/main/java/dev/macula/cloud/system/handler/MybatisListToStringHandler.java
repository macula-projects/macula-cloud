package dev.macula.cloud.system.handler;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MybatisListToStringHandler extends BaseTypeHandler<List> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, Joiner.on(",").skipNulls().join(parameter));
    }

    @Override
    public List getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        if(StringUtils.isBlank(value)){
            return null;
        }
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(value);
    }

    @Override
    public List getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        if(StringUtils.isBlank(value)){
            return null;
        }
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(value);
    }

    @Override
    public List getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        if(StringUtils.isBlank(value)){
            return null;
        }
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(value);
    }
}
