package cn.iocoder.yudao.module.system.service.menu;

import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRespVO;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRouteRespVO;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuSaveReqVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单权限 Service。
 */
public interface SystemMenuService {

    List<MenuRespVO> getMenuList(String name, Integer status);

    List<MenuRespVO> getSimpleMenuList();

    MenuRespVO getMenu(Long id);

    Long createMenu(MenuSaveReqVO reqVO);

    void updateMenu(MenuSaveReqVO reqVO);

    void deleteMenu(Long id);

    Set<String> getAllPermissions();

    List<MenuRouteRespVO> getRouteMenus();

}
