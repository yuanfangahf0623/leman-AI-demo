package cn.iocoder.yudao.module.system.controller.admin.menu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuRespVO;
import cn.iocoder.yudao.module.system.controller.admin.menu.vo.MenuSaveReqVO;
import cn.iocoder.yudao.module.system.service.menu.SystemMenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理 Controller。
 */
@RestController
@RequestMapping("/admin-api/system/menu")
@Validated
@RequiredArgsConstructor
public class SystemMenuController {

    private final SystemMenuService menuService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public CommonResult<List<MenuRespVO>> getMenuList(@RequestParam(value = "name", required = false) String name,
                                                      @RequestParam(value = "status", required = false) Integer status) {
        return CommonResult.success(menuService.getMenuList(name, status));
    }

    @GetMapping("/simple-list")
    @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public CommonResult<List<MenuRespVO>> getSimpleMenuList() {
        return CommonResult.success(menuService.getSimpleMenuList());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public CommonResult<MenuRespVO> getMenu(@RequestParam("id") @NotNull(message = "菜单编号不能为空") Long id) {
        return CommonResult.success(menuService.getMenu(id));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('system:menu:create')")
    public CommonResult<Long> createMenu(@Valid @RequestBody MenuSaveReqVO reqVO) {
        return CommonResult.success(menuService.createMenu(reqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('system:menu:update')")
    public CommonResult<Boolean> updateMenu(@Valid @RequestBody MenuSaveReqVO reqVO) {
        menuService.updateMenu(reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:menu:delete')")
    public CommonResult<Boolean> deleteMenu(@RequestParam("id") @NotNull(message = "菜单编号不能为空") Long id) {
        menuService.deleteMenu(id);
        return CommonResult.success(true);
    }

}
