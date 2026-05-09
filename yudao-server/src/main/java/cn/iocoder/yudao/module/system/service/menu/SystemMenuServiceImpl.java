package cn.iocoder.yudao.module.system.service.menu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRespVO;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRouteRespVO;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.SystemMenuDO;
import cn.iocoder.yudao.module.system.dal.mysql.SystemMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单权限 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class SystemMenuServiceImpl implements SystemMenuService {

    private static final Integer MENU_TYPE_DIR = 1;
    private static final Integer MENU_TYPE_MENU = 2;
    private static final Integer MENU_TYPE_BUTTON = 3;

    private final SystemMenuMapper menuMapper;

    @Override
    public List<MenuRespVO> getMenuList(String name, Integer status) {
        return menuMapper.selectListByQuery(name, status).stream()
                .map(this::convert)
                .toList();
    }

    @Override
    public List<MenuRespVO> getSimpleMenuList() {
        return menuMapper.selectListByQuery(null, 0).stream()
                .filter(menu -> !MENU_TYPE_BUTTON.equals(menu.getType()))
                .map(this::convert)
                .toList();
    }

    @Override
    public MenuRespVO getMenu(Long id) {
        SystemMenuDO menu = menuMapper.selectById(id);
        if (menu == null || Boolean.TRUE.equals(menu.getDeleted())) {
            throw new ServiceException(404, "菜单不存在");
        }
        return convert(menu);
    }

    @Override
    public Long createMenu(MenuSaveReqVO reqVO) {
        SystemMenuDO menu = convert(reqVO);
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        menu.setCreator("admin");
        menu.setUpdater("admin");
        menu.setDeleted(false);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    public void updateMenu(MenuSaveReqVO reqVO) {
        if (reqVO.getId() == null) {
            throw new ServiceException(400, "菜单编号不能为空");
        }
        SystemMenuDO oldMenu = menuMapper.selectById(reqVO.getId());
        if (oldMenu == null || Boolean.TRUE.equals(oldMenu.getDeleted())) {
            throw new ServiceException(404, "菜单不存在");
        }
        SystemMenuDO menu = convert(reqVO);
        menu.setUpdater("admin");
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }

    @Override
    public void deleteMenu(Long id) {
        if (id == null) {
            throw new ServiceException(400, "菜单编号不能为空");
        }
        menuMapper.deleteById(id);
    }

    @Override
    public Set<String> getAllPermissions() {
        Set<String> permissions = menuMapper.selectEnabledMenus().stream()
                .map(SystemMenuDO::getPermission)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        permissions.add("*:*:*");
        return permissions;
    }

    @Override
    public List<MenuRouteRespVO> getRouteMenus() {
        List<SystemMenuDO> menus = menuMapper.selectEnabledMenus().stream()
                .filter(menu -> MENU_TYPE_DIR.equals(menu.getType()) || MENU_TYPE_MENU.equals(menu.getType()))
                .sorted(Comparator.comparing(SystemMenuDO::getSort).thenComparing(SystemMenuDO::getId))
                .toList();
        Map<Long, MenuRouteRespVO> routeMap = new LinkedHashMap<>();
        menus.forEach(menu -> routeMap.put(menu.getId(), convertRoute(menu)));
        List<MenuRouteRespVO> roots = new ArrayList<>();
        for (SystemMenuDO menu : menus) {
            MenuRouteRespVO route = routeMap.get(menu.getId());
            if (menu.getParentId() == null || menu.getParentId() == 0) {
                roots.add(route);
                continue;
            }
            MenuRouteRespVO parent = routeMap.get(menu.getParentId());
            if (parent == null) {
                roots.add(route);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(route);
        }
        return roots;
    }

    private MenuRespVO convert(SystemMenuDO menu) {
        MenuRespVO respVO = new MenuRespVO();
        respVO.setId(menu.getId());
        respVO.setName(menu.getName());
        respVO.setPermission(menu.getPermission());
        respVO.setType(menu.getType());
        respVO.setSort(menu.getSort());
        respVO.setParentId(menu.getParentId());
        respVO.setPath(menu.getPath());
        respVO.setIcon(menu.getIcon());
        respVO.setComponent(menu.getComponent());
        respVO.setComponentName(menu.getComponentName());
        respVO.setStatus(menu.getStatus());
        respVO.setVisible(defaultTrue(menu.getVisible()));
        respVO.setKeepAlive(defaultTrue(menu.getKeepAlive()));
        respVO.setAlwaysShow(defaultTrue(menu.getAlwaysShow()));
        respVO.setCreateTime(menu.getCreateTime());
        return respVO;
    }

    private SystemMenuDO convert(MenuSaveReqVO reqVO) {
        SystemMenuDO menu = new SystemMenuDO();
        menu.setId(reqVO.getId());
        menu.setName(reqVO.getName());
        menu.setPermission(defaultString(reqVO.getPermission()));
        menu.setType(reqVO.getType());
        menu.setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        menu.setParentId(reqVO.getParentId() == null ? 0L : reqVO.getParentId());
        menu.setPath(defaultString(reqVO.getPath()));
        menu.setIcon(defaultString(reqVO.getIcon()));
        menu.setComponent(defaultString(reqVO.getComponent()));
        menu.setComponentName(reqVO.getComponentName());
        menu.setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        menu.setVisible(defaultTrue(reqVO.getVisible()));
        menu.setKeepAlive(defaultTrue(reqVO.getKeepAlive()));
        menu.setAlwaysShow(defaultTrue(reqVO.getAlwaysShow()));
        return menu;
    }

    private MenuRouteRespVO convertRoute(SystemMenuDO menu) {
        return MenuRouteRespVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .path(menu.getPath())
                .component(menu.getComponent())
                .componentName(menu.getComponentName())
                .redirect(null)
                .icon(menu.getIcon())
                .visible(defaultTrue(menu.getVisible()))
                .keepAlive(defaultTrue(menu.getKeepAlive()))
                .alwaysShow(defaultTrue(menu.getAlwaysShow()))
                .build();
    }

    private Boolean defaultTrue(Boolean value) {
        return value == null || value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

}
