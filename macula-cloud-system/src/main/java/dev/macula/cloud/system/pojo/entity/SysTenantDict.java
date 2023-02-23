package dev.macula.cloud.system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SysTenantDict {
    public static final Integer DICT_TYPE_TYPE_INT = 0;
    public static final Integer DICT_ITEM_TYPE_INT = 1;
    private Long dictItemId;
    private Integer type;
    private Long tenantId;
}
