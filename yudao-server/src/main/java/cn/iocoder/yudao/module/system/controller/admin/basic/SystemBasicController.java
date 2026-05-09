package cn.iocoder.yudao.module.system.controller.admin.basic;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.server.framework.crud.SimpleAdminDataService;
import cn.iocoder.yudao.server.framework.crud.SimpleAdminDataService.TableDef;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basic system management APIs used by the local yudao-ui integration.
 */
@RestController
@RequestMapping("/admin-api/system")
@RequiredArgsConstructor
public class SystemBasicController {

    private static final Set<String> AUDIT_COLUMNS = Set.of("creator", "create_time", "updater", "update_time", "deleted");
    private static final TableDef DEPT = def("system_dept",
            cols("id", "tenant_id", "name", "parent_id", "sort", "leader_user_id", "phone", "email", "status"),
            cols("name", "status"), "sort ASC, id ASC");
    private static final TableDef POST = def("system_post",
            cols("id", "tenant_id", "name", "code", "sort", "status", "remark"),
            cols("name", "code", "status"), "sort ASC, id ASC");
    private static final TableDef USER = def("system_users",
            cols("id", "tenant_id", "username", "password", "nickname", "remark", "dept_id", "email", "mobile", "sex",
                    "avatar", "status", "login_ip", "login_date"),
            cols("username", "nickname", "mobile", "status", "dept_id"), "id DESC");
    private static final TableDef TENANT = def("system_tenant",
            cols("id", "name", "contact_name", "contact_mobile", "status", "website", "package_id", "expire_time",
                    "account_count"),
            cols("name", "contact_name", "contact_mobile", "status"), "id DESC");
    private static final TableDef TENANT_PACKAGE = def("system_tenant_package",
            cols("id", "name", "status", "remark", "menu_ids"),
            cols("name", "status"), "id DESC");
    private static final TableDef ROLE = def("system_role",
            cols("id", "tenant_id", "name", "code", "sort", "status", "type", "data_scope", "data_scope_dept_ids", "remark"),
            cols("name", "code", "status", "type"), "sort ASC, id ASC");
    private static final TableDef DICT_TYPE = def("system_dict_type",
            cols("id", "name", "type", "status", "remark"),
            cols("name", "type", "status"), "id DESC");
    private static final TableDef DICT_DATA = def("system_dict_data",
            cols("id", "sort", "label", "value", "dict_type", "status", "color_type", "css_class", "remark"),
            cols("label", "dict_type", "status"), "sort ASC, id ASC");
    private static final TableDef OPERATE_LOG = def("system_operate_log",
            cols("id", "trace_id", "user_id", "user_type", "module", "name", "type", "content", "request_method",
                    "request_url", "user_ip", "user_agent", "success", "duration"),
            cols("module", "name", "user_ip", "success"), "id DESC");
    private static final TableDef LOGIN_LOG = def("system_login_log",
            cols("id", "log_type", "trace_id", "user_id", "user_type", "username", "result", "user_ip", "user_agent"),
            cols("username", "user_ip", "result"), "id DESC");

    private final SimpleAdminDataService dataService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/dept/simple-list")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleDeptList() {
        return CommonResult.success(dataService.list(DEPT, Map.of("status", 0)));
    }

    @GetMapping("/dept/list")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<List<Map<String, Object>>> getDeptList(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.list(DEPT, params));
    }

    @GetMapping("/dept/get")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<Map<String, Object>> getDept(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(DEPT, id));
    }

    @PostMapping("/dept/create")
    @PreAuthorize("@ss.hasPermission('system:dept:create')")
    public CommonResult<Long> createDept(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(DEPT, reqVO));
    }

    @PutMapping("/dept/update")
    @PreAuthorize("@ss.hasPermission('system:dept:update')")
    public CommonResult<Boolean> updateDept(@RequestBody Map<String, Object> reqVO) {
        dataService.update(DEPT, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dept/delete")
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public CommonResult<Boolean> deleteDept(@RequestParam("id") Long id) {
        dataService.delete(DEPT, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dept/delete-list")
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public CommonResult<Boolean> deleteDeptList(@RequestParam("ids") String ids) {
        dataService.deleteList(DEPT, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/post/page")
    @PreAuthorize("@ss.hasPermission('system:post:query')")
    public CommonResult<PageResult<Map<String, Object>>> getPostPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(POST, params));
    }

    @GetMapping("/post/simple-list")
    @PreAuthorize("@ss.hasPermission('system:post:query')")
    public CommonResult<List<Map<String, Object>>> getSimplePostList() {
        return CommonResult.success(dataService.list(POST, Map.of("status", 0)));
    }

    @GetMapping("/post/get")
    @PreAuthorize("@ss.hasPermission('system:post:query')")
    public CommonResult<Map<String, Object>> getPost(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(POST, id));
    }

    @PostMapping("/post/create")
    @PreAuthorize("@ss.hasPermission('system:post:create')")
    public CommonResult<Long> createPost(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(POST, reqVO));
    }

    @PutMapping("/post/update")
    @PreAuthorize("@ss.hasPermission('system:post:update')")
    public CommonResult<Boolean> updatePost(@RequestBody Map<String, Object> reqVO) {
        dataService.update(POST, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/post/delete")
    @PreAuthorize("@ss.hasPermission('system:post:delete')")
    public CommonResult<Boolean> deletePost(@RequestParam("id") Long id) {
        dataService.delete(POST, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/post/delete-list")
    @PreAuthorize("@ss.hasPermission('system:post:delete')")
    public CommonResult<Boolean> deletePostList(@RequestParam("ids") String ids) {
        dataService.deleteList(POST, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/post/export-excel")
    public ResponseEntity<byte[]> exportPost(@RequestParam Map<String, Object> params) {
        return excel("post.xlsx", dataService.exportExcel(POST, params, columns(
                "id", "编号",
                "name", "岗位名称",
                "code", "岗位编码",
                "sort", "排序",
                "status", "状态",
                "remark", "备注",
                "createTime", "创建时间"), "岗位"));
    }

    @GetMapping("/user/page")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<Map<String, Object>>> getUserPage(@RequestParam Map<String, Object> params) {
        PageResult<Map<String, Object>> page = dataService.page(USER, params);
        page.getList().forEach(this::removeSensitiveUserFields);
        return CommonResult.success(page);
    }

    @GetMapping("/user/simple-list")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleUserList() {
        List<Map<String, Object>> users = dataService.list(USER, Map.of("status", 0));
        users.forEach(this::removeSensitiveUserFields);
        return CommonResult.success(users);
    }

    @GetMapping("/user/get")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<Map<String, Object>> getUser(@RequestParam("id") Long id) {
        Map<String, Object> user = dataService.get(USER, id);
        removeSensitiveUserFields(user);
        user.put("postIds", dataService.selectLongRelations("system_user_post", "user_id", id, "post_id"));
        user.put("roleIds", dataService.selectLongRelations("system_user_role", "user_id", id, "role_id"));
        return CommonResult.success(user);
    }

    @PostMapping("/user/create")
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    public CommonResult<Long> createUser(@RequestBody Map<String, Object> reqVO) {
        Object password = reqVO.get("password");
        if (password == null || !StringUtils.hasText(String.valueOf(password))) {
            throw new ServiceException(400, "密码不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>(reqVO);
        data.put("password", passwordEncoder.encode(String.valueOf(password)));
        Long userId = dataService.create(USER, data);
        saveUserRelations(userId, reqVO);
        return CommonResult.success(userId);
    }

    @PutMapping("/user/update")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Boolean> updateUser(@RequestBody Map<String, Object> reqVO) {
        Map<String, Object> data = new LinkedHashMap<>(reqVO);
        data.remove("password");
        dataService.update(USER, data);
        saveUserRelations(dataService.longValue(reqVO.get("id")), reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/user/update-password")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Boolean> updateUserPassword(@RequestBody Map<String, Object> reqVO) {
        Long id = dataService.longValue(reqVO.get("id"));
        Object password = reqVO.get("password");
        if (password == null || !StringUtils.hasText(String.valueOf(password))) {
            throw new ServiceException(400, "密码不能为空");
        }
        dataService.updateColumns("system_users", id, Map.of("password", passwordEncoder.encode(String.valueOf(password))));
        return CommonResult.success(true);
    }

    @PutMapping("/user/update-status")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public CommonResult<Boolean> updateUserStatus(@RequestBody Map<String, Object> reqVO) {
        dataService.updateColumns("system_users", dataService.longValue(reqVO.get("id")), Map.of("status", reqVO.get("status")));
        return CommonResult.success(true);
    }

    @DeleteMapping("/user/delete")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> deleteUser(@RequestParam("id") Long id) {
        dataService.delete(USER, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/user/delete-list")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> deleteUserList(@RequestParam("ids") String ids) {
        dataService.deleteList(USER, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/user/export-excel")
    public ResponseEntity<byte[]> exportUser(@RequestParam Map<String, Object> params) {
        List<Map<String, Object>> users = dataService.list(USER, params);
        users.forEach(this::removeSensitiveUserFields);
        return excel("user.xlsx", dataService.exportExcel(users, columns(
                "id", "编号",
                "username", "用户账号",
                "nickname", "用户昵称",
                "deptId", "部门编号",
                "mobile", "手机号",
                "email", "邮箱",
                "sex", "性别",
                "status", "状态",
                "loginIp", "最后登录 IP",
                "loginDate", "最后登录时间",
                "createTime", "创建时间"), "用户"));
    }

    @GetMapping("/user/get-import-template")
    public ResponseEntity<byte[]> getUserImportTemplate() {
        return download("user-import-template.csv", "username,nickname,password,mobile,email\n".getBytes());
    }

    @GetMapping("/tenant/page")
    @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<PageResult<Map<String, Object>>> getTenantPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(TENANT, params));
    }

    @GetMapping("/tenant/simple-list")
    @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleTenantList() {
        return CommonResult.success(dataService.list(TENANT, Map.of("status", 0)));
    }

    @GetMapping("/tenant/get")
    @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<Map<String, Object>> getTenant(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(TENANT, id));
    }

    @PostMapping("/tenant/create")
    @PreAuthorize("@ss.hasPermission('system:tenant:create')")
    public CommonResult<Long> createTenant(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(TENANT, reqVO));
    }

    @PutMapping("/tenant/update")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<Boolean> updateTenant(@RequestBody Map<String, Object> reqVO) {
        dataService.update(TENANT, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/tenant/delete")
    @PreAuthorize("@ss.hasPermission('system:tenant:delete')")
    public CommonResult<Boolean> deleteTenant(@RequestParam("id") Long id) {
        dataService.delete(TENANT, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/tenant/delete-list")
    @PreAuthorize("@ss.hasPermission('system:tenant:delete')")
    public CommonResult<Boolean> deleteTenantList(@RequestParam("ids") String ids) {
        dataService.deleteList(TENANT, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/tenant/export-excel")
    public ResponseEntity<byte[]> exportTenant(@RequestParam Map<String, Object> params) {
        return excel("tenant.xlsx", dataService.exportExcel(TENANT, params, columns(
                "id", "编号",
                "name", "租户名称",
                "contactName", "联系人",
                "contactMobile", "联系电话",
                "status", "状态",
                "website", "绑定域名",
                "packageId", "套餐编号",
                "expireTime", "过期时间",
                "accountCount", "账号额度",
                "createTime", "创建时间"), "租户"));
    }

    @GetMapping("/tenant-package/page")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<PageResult<Map<String, Object>>> getTenantPackagePage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(TENANT_PACKAGE, params));
    }

    @GetMapping("/tenant-package/simple-list")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleTenantPackageList() {
        return CommonResult.success(dataService.list(TENANT_PACKAGE, Map.of("status", 0)));
    }

    @GetMapping("/tenant-package/get")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:query')")
    public CommonResult<Map<String, Object>> getTenantPackage(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(TENANT_PACKAGE, id));
    }

    @PostMapping("/tenant-package/create")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:create')")
    public CommonResult<Long> createTenantPackage(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(TENANT_PACKAGE, reqVO));
    }

    @PutMapping("/tenant-package/update")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:update')")
    public CommonResult<Boolean> updateTenantPackage(@RequestBody Map<String, Object> reqVO) {
        dataService.update(TENANT_PACKAGE, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/tenant-package/delete")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:delete')")
    public CommonResult<Boolean> deleteTenantPackage(@RequestParam("id") Long id) {
        dataService.delete(TENANT_PACKAGE, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/tenant-package/delete-list")
    @PreAuthorize("@ss.hasPermission('system:tenant-package:delete')")
    public CommonResult<Boolean> deleteTenantPackageList(@RequestParam("ids") String ids) {
        dataService.deleteList(TENANT_PACKAGE, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/role/page")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<PageResult<Map<String, Object>>> getRolePage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(ROLE, params));
    }

    @GetMapping("/role/simple-list")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleRoleList() {
        return CommonResult.success(dataService.list(ROLE, Map.of("status", 0)));
    }

    @GetMapping("/role/get")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public CommonResult<Map<String, Object>> getRole(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(ROLE, id));
    }

    @PostMapping("/role/create")
    @PreAuthorize("@ss.hasPermission('system:role:create')")
    public CommonResult<Long> createRole(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(ROLE, reqVO));
    }

    @PutMapping("/role/update")
    @PreAuthorize("@ss.hasPermission('system:role:update')")
    public CommonResult<Boolean> updateRole(@RequestBody Map<String, Object> reqVO) {
        dataService.update(ROLE, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/role/delete")
    @PreAuthorize("@ss.hasPermission('system:role:delete')")
    public CommonResult<Boolean> deleteRole(@RequestParam("id") Long id) {
        dataService.delete(ROLE, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/role/delete-list")
    @PreAuthorize("@ss.hasPermission('system:role:delete')")
    public CommonResult<Boolean> deleteRoleList(@RequestParam("ids") String ids) {
        dataService.deleteList(ROLE, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/role/export-excel")
    public ResponseEntity<byte[]> exportRole(@RequestParam Map<String, Object> params) {
        return excel("role.xlsx", dataService.exportExcel(ROLE, params, columns(
                "id", "编号",
                "name", "角色名称",
                "code", "角色编码",
                "sort", "排序",
                "status", "状态",
                "type", "类型",
                "dataScope", "数据范围",
                "remark", "备注",
                "createTime", "创建时间"), "角色"));
    }

    @GetMapping("/dict-type/simple-list")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleDictTypeList() {
        return CommonResult.success(dataService.list(DICT_TYPE, Map.of("status", 0)));
    }

    @GetMapping("/dict-type/page")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    public CommonResult<PageResult<Map<String, Object>>> getDictTypePage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(DICT_TYPE, params));
    }

    @GetMapping("/dict-type/get")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    public CommonResult<Map<String, Object>> getDictType(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(DICT_TYPE, id));
    }

    @PostMapping("/dict-type/create")
    @PreAuthorize("@ss.hasPermission('system:dict:create')")
    public CommonResult<Long> createDictType(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(DICT_TYPE, reqVO));
    }

    @PutMapping("/dict-type/update")
    @PreAuthorize("@ss.hasPermission('system:dict:update')")
    public CommonResult<Boolean> updateDictType(@RequestBody Map<String, Object> reqVO) {
        dataService.update(DICT_TYPE, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-type/delete")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    public CommonResult<Boolean> deleteDictType(@RequestParam("id") Long id) {
        dataService.delete(DICT_TYPE, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-type/delete-list")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    public CommonResult<Boolean> deleteDictTypeList(@RequestParam("ids") String ids) {
        dataService.deleteList(DICT_TYPE, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/dict-type/export-excel")
    public ResponseEntity<byte[]> exportDictType(@RequestParam Map<String, Object> params) {
        return excel("dict-type.xlsx", dataService.exportExcel(DICT_TYPE, params, columns(
                "id", "编号",
                "name", "字典名称",
                "type", "字典类型",
                "status", "状态",
                "remark", "备注",
                "createTime", "创建时间"), "字典类型"));
    }

    @GetMapping("/dict-data/simple-list")
    public CommonResult<List<Map<String, Object>>> getSimpleDictDataList() {
        return CommonResult.success(dataService.list(DICT_DATA, Map.of("status", 0)));
    }

    @GetMapping("/dict-data/type")
    public CommonResult<List<Map<String, Object>>> getDictDataByType(@RequestParam("type") String type) {
        return CommonResult.success(dataService.list(DICT_DATA, Map.of("dictType", type, "status", 0)));
    }

    @GetMapping("/dict-data/page")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    public CommonResult<PageResult<Map<String, Object>>> getDictDataPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(DICT_DATA, params));
    }

    @GetMapping("/dict-data/get")
    @PreAuthorize("@ss.hasPermission('system:dict:query')")
    public CommonResult<Map<String, Object>> getDictData(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(DICT_DATA, id));
    }

    @PostMapping("/dict-data/create")
    @PreAuthorize("@ss.hasPermission('system:dict:create')")
    public CommonResult<Long> createDictData(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(DICT_DATA, reqVO));
    }

    @PutMapping("/dict-data/update")
    @PreAuthorize("@ss.hasPermission('system:dict:update')")
    public CommonResult<Boolean> updateDictData(@RequestBody Map<String, Object> reqVO) {
        dataService.update(DICT_DATA, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-data/delete")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    public CommonResult<Boolean> deleteDictData(@RequestParam("id") Long id) {
        dataService.delete(DICT_DATA, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-data/delete-list")
    @PreAuthorize("@ss.hasPermission('system:dict:delete')")
    public CommonResult<Boolean> deleteDictDataList(@RequestParam("ids") String ids) {
        dataService.deleteList(DICT_DATA, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/dict-data/export-excel")
    public ResponseEntity<byte[]> exportDictData(@RequestParam Map<String, Object> params) {
        return excel("dict-data.xlsx", dataService.exportExcel(DICT_DATA, params, columns(
                "id", "编号",
                "sort", "排序",
                "label", "字典标签",
                "value", "字典值",
                "dictType", "字典类型",
                "status", "状态",
                "colorType", "颜色类型",
                "cssClass", "CSS Class",
                "remark", "备注",
                "createTime", "创建时间"), "字典数据"));
    }

    @GetMapping("/operate-log/page")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    public CommonResult<PageResult<Map<String, Object>>> getOperateLogPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(OPERATE_LOG, params));
    }

    @GetMapping("/operate-log/export-excel")
    public ResponseEntity<byte[]> exportOperateLog(@RequestParam Map<String, Object> params) {
        return excel("operate-log.xlsx", dataService.exportExcel(OPERATE_LOG, params, columns(
                "id", "编号",
                "traceId", "链路编号",
                "userId", "用户编号",
                "module", "模块",
                "name", "操作名",
                "type", "类型",
                "requestMethod", "请求方法",
                "requestUrl", "请求地址",
                "userIp", "用户 IP",
                "success", "是否成功",
                "duration", "耗时",
                "createTime", "创建时间"), "操作日志"));
    }

    @GetMapping("/login-log/page")
    @PreAuthorize("@ss.hasPermission('system:login-log:query')")
    public CommonResult<PageResult<Map<String, Object>>> getLoginLogPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(LOGIN_LOG, params));
    }

    @GetMapping("/login-log/export-excel")
    public ResponseEntity<byte[]> exportLoginLog(@RequestParam Map<String, Object> params) {
        return excel("login-log.xlsx", dataService.exportExcel(LOGIN_LOG, params, columns(
                "id", "编号",
                "logType", "日志类型",
                "traceId", "链路编号",
                "userId", "用户编号",
                "username", "用户账号",
                "result", "登录结果",
                "userIp", "用户 IP",
                "userAgent", "User-Agent",
                "createTime", "创建时间"), "登录日志"));
    }

    @GetMapping("/notify-message/get-unread-count")
    public CommonResult<Integer> getUnreadNotifyMessageCount() {
        return CommonResult.success(0);
    }

    @GetMapping("/notify-message/get-unread-list")
    public CommonResult<List<Map<String, Object>>> getUnreadNotifyMessageList() {
        return CommonResult.success(List.of());
    }

    @GetMapping({"/notify-message/page", "/notify-message/my-page"})
    public CommonResult<PageResult<Map<String, Object>>> getNotifyMessagePage() {
        return CommonResult.success(new PageResult<>(List.of(), 0L));
    }

    @PutMapping({"/notify-message/update-read", "/notify-message/update-all-read"})
    public CommonResult<Boolean> updateNotifyMessageRead() {
        return CommonResult.success(true);
    }

    private void saveUserRelations(Long userId, Map<String, Object> reqVO) {
        dataService.replaceLongRelations("system_user_post", "user_id", userId, "post_id",
                dataService.collectionValue(reqVO.get("postIds")));
        dataService.replaceLongRelations("system_user_role", "user_id", userId, "role_id",
                dataService.collectionValue(reqVO.get("roleIds")));
    }

    private void removeSensitiveUserFields(Map<String, Object> user) {
        user.remove("password");
    }

    private ResponseEntity<byte[]> download(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private static LinkedHashMap<String, String> columns(String... pairs) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            columns.put(pairs[i], pairs[i + 1]);
        }
        return columns;
    }

    private static TableDef def(String tableName, Collection<String> columns, Collection<String> searchColumns, String orderBy) {
        List<String> allColumns = new ArrayList<>(columns);
        allColumns.addAll(AUDIT_COLUMNS);
        return new TableDef(tableName, Set.copyOf(allColumns), Set.copyOf(searchColumns), orderBy);
    }

    private static Set<String> cols(String... columns) {
        return Set.of(columns);
    }
}
