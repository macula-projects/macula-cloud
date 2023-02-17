package dev.macula.cloud.system.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import dev.macula.boot.base.IBaseEnum;
import lombok.Getter;

public enum DataScopeEnum implements IBaseEnum<Integer> {
    ALL(1, "所有数据"),
    DEPT_AND_SUB(2, "部门及子部门数据"),
    DEPT(3, "本部门数据"),
    SELF(4, "本人数据"),
    DEFAULT(5, "默认范围");

    @Getter
    @EnumValue //  Mybatis-Plus 提供注解表示插入数据库时插入该值
    private Integer value;
    @Getter
    private String label;

    private DataScopeEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getLabel() {
        return this.label;
    }
}
