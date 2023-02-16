package dev.macula.cloud.system.converter;

import com.google.common.base.Splitter;
import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * 权限对象转换器
 *
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Mapper(componentModel = "spring")
public interface PermissionConverter {
    Splitter URL_PERM_SPLITTER = Splitter.on(":").trimResults().omitEmptyStrings();
    StringBuilder SB_USER_PERM = new StringBuilder();

    @Mappings({
            @Mapping(source = "code", target = "name"),
            @Mapping(target = "urlPerm", expression = "java(urlAndMethod2Perm(permDTO.getUrl(), permDTO.getMethod()))")
    })
    SysPermission permDTO2Entity(PermDTO permDTO);

    @Mappings({
            @Mapping(source = "name", target = "code"),
            @Mapping(target = "url", expression = "java(perm2Url(entity.getUrlPerm()))"),
            @Mapping(target = "method", expression = "java(perm2Method(entity.getUrlPerm()))")
    })
    PermDTO entity2DTO(SysPermission entity);

    List<PermDTO> listEntities2DTO(List<SysPermission> entities);

    default String urlAndMethod2Perm(String url, RequestMethod method) {
        SB_USER_PERM.setLength(0);
        return SB_USER_PERM.append(method).append(":").append(url).toString();
    }

    default String perm2Url(String urlPerm) {
        List<String> permList = URL_PERM_SPLITTER.splitToList(urlPerm);
        return permList.size() > 1 ? permList.get(1) : permList.get(0);
    }

    default RequestMethod perm2Method(String urlPerm) {
        List<String> permList = URL_PERM_SPLITTER.splitToList(urlPerm);
        return permList.size() > 1 ? RequestMethod.valueOf(permList.get(0)) : RequestMethod.GET;
    }
}
