package dev.macula.cloud.system.dto;

import dev.macula.cloud.system.enums.MenuTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 我的菜单结构的元数据结构
 *
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "我的菜单结构的元数据结构")
@Data
public class MenuMetaDTO implements Serializable {
    @Schema(description = "元数据标题")
    private String title;
    @Schema(description = "元数据图标")
    private String icon;
    @Schema(description = "元数据类型")
    private MenuTypeEnum type;
    @Schema(description = "整页路由")
    private Boolean fullpage;
    @Schema(description = "是否显示")
    private Boolean visible;
    @Schema(description = "标签")
    private String tag;
    @Schema(description = "颜色")
    private String color;
}
