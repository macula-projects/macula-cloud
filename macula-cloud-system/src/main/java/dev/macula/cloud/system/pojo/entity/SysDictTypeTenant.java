package dev.macula.cloud.system.pojo.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SysDictTypeTenant extends SysMenu {
    private Long systemDictTypeId;
}
