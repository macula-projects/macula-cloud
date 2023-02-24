package dev.macula.cloud.system.pojo.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SysDictItemTenant extends SysDictItem {
    private Long systemDictItemId;
}
