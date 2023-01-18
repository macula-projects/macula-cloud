package dev.macula.cloud.system.handler;

import dev.macula.boot.base.IBaseEnum;
import dev.macula.cloud.system.enums.MenuTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
public class MybatisEnumKeyTypeHandler extends BaseTypeHandler<MenuTypeEnum> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, MenuTypeEnum parameter, JdbcType jdbcType) throws SQLException {
    // baseTypeHandler已经帮我们做了parameter的null判断
    ps.setInt(i, parameter.getValue());
  }

  @Override
  public MenuTypeEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
    // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
    int  i = rs.getInt(columnName);
    return rs.wasNull()? null : locateIEnum(i);
  }

  @Override
  public MenuTypeEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
    int  i = rs.getInt(columnIndex);
    return rs.wasNull()? null : locateIEnum(i);
  }

  @Override
  public MenuTypeEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
    int  i = cs.getInt(columnIndex);
    return cs.wasNull()? null : locateIEnum(i);
  }

  private MenuTypeEnum locateIEnum(int i){
      return IBaseEnum.getEnumByValue(i, MenuTypeEnum.class);
  }
}
