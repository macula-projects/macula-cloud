package dev.macula.cloud.system.enums;

import dev.macula.boot.base.IBaseEnum;

public enum DataScopeEnum implements IBaseEnum<Integer> {
    ALL(0, "所有数据"),
    DEPT_AND_SUB(1, "部门及子部门数据"),
    DEPT(2, "本部门数据"),
    SELF(3, "本人数据"),
    DEFAULT(9, "默认范围");

    private Integer value;
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
