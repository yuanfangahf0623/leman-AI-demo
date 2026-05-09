package cn.iocoder.yudao.module.system.controller.admin.permission;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.server.framework.crud.SimpleAdminDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Role, menu and user permission assignment APIs.
 */
@RestController
@RequestMapping("/admin-api/system/permission")
@RequiredArgsConstructor
public class SystemPermissionController {

    private final SimpleAdminDataService dataService;

    @GetMapping("/list-role-menus")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<List<Long>> listRoleMenus(@RequestParam("roleId") Long roleId) {
        return CommonResult.success(dataService.selectLongRelations("system_role_menu", "role_id", roleId, "menu_id"));
    }

    @PostMapping("/assign-role-menu")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')")
    public CommonResult<Boolean> assignRoleMenu(@RequestBody Map<String, Object> reqVO) {
        Long roleId = requiredId(reqVO.get("roleId"), "角色编号不能为空");
        dataService.replaceLongRelations("system_role_menu", "role_id", roleId, "menu_id", collection(reqVO.get("menuIds")));
        return CommonResult.success(true);
    }

    @PostMapping("/assign-role-data-scope")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-role-data-scope')")
    public CommonResult<Boolean> assignRoleDataScope(@RequestBody Map<String, Object> reqVO) {
        Long roleId = requiredId(reqVO.get("roleId"), "角色编号不能为空");
        Object dataScope = reqVO.get("dataScope") == null ? 1 : reqVO.get("dataScope");
        Object deptIds = reqVO.get("dataScopeDeptIds") == null ? "[]" : reqVO.get("dataScopeDeptIds");
        dataService.updateColumns("system_role", roleId, Map.of(
                "data_scope", dataScope,
                "data_scope_dept_ids", String.valueOf(deptIds)));
        return CommonResult.success(true);
    }

    @GetMapping("/list-user-roles")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')")
    public CommonResult<List<Long>> listUserRoles(@RequestParam("userId") Long userId) {
        return CommonResult.success(dataService.selectLongRelations("system_user_role", "user_id", userId, "role_id"));
    }

    @PostMapping("/assign-user-role")
    @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')")
    public CommonResult<Boolean> assignUserRole(@RequestBody Map<String, Object> reqVO) {
        Long userId = requiredId(reqVO.get("userId"), "用户编号不能为空");
        dataService.replaceLongRelations("system_user_role", "user_id", userId, "role_id", collection(reqVO.get("roleIds")));
        return CommonResult.success(true);
    }

    private Long requiredId(Object value, String message) {
        Long id = dataService.longValue(value);
        if (id == null) {
            throw new ServiceException(400, message);
        }
        return id;
    }

    private Collection<?> collection(Object value) {
        return dataService.collectionValue(value);
    }
}
