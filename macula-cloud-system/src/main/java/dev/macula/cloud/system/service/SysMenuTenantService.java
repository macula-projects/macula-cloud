package dev.macula.cloud.system.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import dev.macula.cloud.system.query.MenuPageQuery;
import dev.macula.cloud.system.query.MenuQuery;

import java.util.List;

public interface SysMenuTenantService extends IService<SysMenuTenant> {
    /**
     * 根菜单id
     */
    Long ROOT_ID = 0L;

    /**
     * 菜单可见
     */
    Integer VISIBLED = 1;

    JSONObject getMyMenu(MenuQuery menuQuery);

    IPage<MenuBO> pagesMenus(MenuPageQuery menuPageQuery);

    JSONObject add(MenuDTO menuDTO);

    List<Long> del(List<Long> menuIds);
}
