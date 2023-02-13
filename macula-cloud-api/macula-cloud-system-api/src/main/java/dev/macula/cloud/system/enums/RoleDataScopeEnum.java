package dev.macula.cloud.system.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import dev.macula.boot.base.IBaseEnum;
import lombok.Getter;

/**
 * 角色数据权限范围枚举
 *
 * @author qiuyuhao
 * @date 2023.02.10
 */

public enum RoleDataScopeEnum implements IBaseEnum<Integer> {
    ALL(1, "全部可见"),
    ME(2, "本人可见"),
    DEPT(3, "所在部门可见"),
    DEPTS(4, "所在部门及子级可见"),
    CUSTOM_DEP(5, "选择部门可见"),
    CUSTOM(6, "自定义");
    @Getter
    @EnumValue //  Mybatis-Plus 提供注解表示插入数据库时插入该值
    private Integer value;

    @Getter
    // @JsonValue //  表示对枚举序列化时返回此字段
    private String label;

    RoleDataScopeEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }
}
