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
    ME(1, "我的数据"),
    DEPT(2, "本部门下的数据"),
    DEPTS(3, "部门及子部门数据"),
    ALL(4, "所有数据");
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
