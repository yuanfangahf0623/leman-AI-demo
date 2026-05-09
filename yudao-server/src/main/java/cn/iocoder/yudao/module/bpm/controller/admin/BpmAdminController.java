package cn.iocoder.yudao.module.bpm.controller.admin;

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
 * Minimal BPM admin APIs for page integration.
 */
@RestController
@RequestMapping("/admin-api/bpm")
@RequiredArgsConstructor
public class BpmAdminController {

    private static final Set<String> AUDIT_COLUMNS = Set.of("creator", "create_time", "updater", "update_time", "deleted");
    private static final TableDef CATEGORY = def("bpm_category",
            cols("id", "name", "code", "status", "sort", "tenant_id"),
            cols("name", "code", "status"), "sort ASC, id ASC");
    private static final TableDef FORM = def("bpm_form",
            cols("id", "name", "status", "conf", "fields", "remark", "tenant_id"),
            cols("name", "status"), "id DESC");
    private static final TableDef MODEL = def("bpm_model",
            cols("id", "name", "key", "description", "category", "form_type", "form_id", "form_name",
                    "form_custom_create_path", "form_custom_view_path", "status", "remark", "bpmn_xml", "tenant_id"),
            cols("name", "key", "category", "status"), "id DESC");
    private static final TableDef PROCESS_EXPRESSION = def("bpm_process_expression",
            cols("id", "name", "status", "expression", "remark", "tenant_id"),
            cols("name", "status"), "id DESC");
    private static final TableDef PROCESS_LISTENER = def("bpm_process_listener",
            cols("id", "name", "type", "status", "event", "listener_type", "listener_value", "remark", "tenant_id"),
            cols("name", "type", "status"), "id DESC");
    private static final TableDef USER_GROUP = def("bpm_user_group",
            cols("id", "name", "description", "status", "member_user_ids", "tenant_id"),
            cols("name", "status"), "id DESC");
    private static final TableDef LEAVE = def("bpm_oa_leave",
            cols("id", "user_id", "type", "reason", "start_time", "end_time", "process_instance_id", "status", "tenant_id"),
            cols("user_id", "status"), "id DESC");

    private final SimpleAdminDataService dataService;

    @GetMapping("/category/page")
    @PreAuthorize("@ss.hasPermission('bpm:category:query')")
    public CommonResult<PageResult<Map<String, Object>>> getCategoryPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(CATEGORY, params));
    }

    @GetMapping("/category/simple-list")
    @PreAuthorize("@ss.hasPermission('bpm:category:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleCategoryList() {
        return CommonResult.success(dataService.list(CATEGORY, Map.of("status", 0)));
    }

    @GetMapping("/category/get")
    @PreAuthorize("@ss.hasPermission('bpm:category:query')")
    public CommonResult<Map<String, Object>> getCategory(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(CATEGORY, id));
    }

    @PostMapping("/category/create")
    @PreAuthorize("@ss.hasPermission('bpm:category:create')")
    public CommonResult<Long> createCategory(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(CATEGORY, reqVO));
    }

    @PutMapping("/category/update")
    @PreAuthorize("@ss.hasPermission('bpm:category:update')")
    public CommonResult<Boolean> updateCategory(@RequestBody Map<String, Object> reqVO) {
        dataService.update(CATEGORY, reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/category/update-sort-batch")
    @PreAuthorize("@ss.hasPermission('bpm:category:update')")
    public CommonResult<Boolean> updateCategorySortBatch(@RequestParam("ids") String ids) {
        updateSorts("bpm_category", ids);
        return CommonResult.success(true);
    }

    @DeleteMapping("/category/delete")
    @PreAuthorize("@ss.hasPermission('bpm:category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        dataService.delete(CATEGORY, id);
        return CommonResult.success(true);
    }

    @GetMapping("/form/page")
    @PreAuthorize("@ss.hasPermission('bpm:form:query')")
    public CommonResult<PageResult<Map<String, Object>>> getFormPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(FORM, params));
    }

    @GetMapping("/form/simple-list")
    @PreAuthorize("@ss.hasPermission('bpm:form:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleFormList() {
        return CommonResult.success(dataService.list(FORM, Map.of("status", 0)));
    }

    @GetMapping("/form/get")
    @PreAuthorize("@ss.hasPermission('bpm:form:query')")
    public CommonResult<Map<String, Object>> getForm(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(FORM, id));
    }

    @PostMapping("/form/create")
    @PreAuthorize("@ss.hasPermission('bpm:form:create')")
    public CommonResult<Long> createForm(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(FORM, reqVO));
    }

    @PutMapping("/form/update")
    @PreAuthorize("@ss.hasPermission('bpm:form:update')")
    public CommonResult<Boolean> updateForm(@RequestBody Map<String, Object> reqVO) {
        dataService.update(FORM, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/form/delete")
    @PreAuthorize("@ss.hasPermission('bpm:form:delete')")
    public CommonResult<Boolean> deleteForm(@RequestParam("id") Long id) {
        dataService.delete(FORM, id);
        return CommonResult.success(true);
    }

    @GetMapping("/model/list")
    @PreAuthorize("@ss.hasPermission('bpm:model:query')")
    public CommonResult<List<Map<String, Object>>> getModelList(@RequestParam(value = "name", required = false) String name) {
        Map<String, Object> params = name == null ? Map.of() : Map.of("name", name);
        return CommonResult.success(withModelDefaults(dataService.list(MODEL, params)));
    }

    @GetMapping("/model/get")
    @PreAuthorize("@ss.hasPermission('bpm:model:query')")
    public CommonResult<Map<String, Object>> getModel(@RequestParam("id") Long id) {
        return CommonResult.success(withModelDefault(dataService.get(MODEL, id)));
    }

    @PostMapping("/model/create")
    @PreAuthorize("@ss.hasPermission('bpm:model:create')")
    public CommonResult<Long> createModel(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(MODEL, reqVO));
    }

    @PutMapping("/model/update")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModel(@RequestBody Map<String, Object> reqVO) {
        dataService.update(MODEL, reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/model/update-bpmn")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModelBpmn(@RequestBody Map<String, Object> reqVO) {
        dataService.update(MODEL, reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/model/update-state")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModelState(@RequestBody Map<String, Object> reqVO) {
        Object state = reqVO.get("state") == null ? 0 : reqVO.get("state");
        dataService.updateColumns("bpm_model", dataService.longValue(reqVO.get("id")), Map.of("status", state));
        return CommonResult.success(true);
    }

    @PutMapping("/model/update-sort-batch")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateModelSortBatch(@RequestParam("ids") String ids) {
        updateSorts("bpm_model", ids);
        return CommonResult.success(true);
    }

    @PostMapping("/model/deploy")
    @PreAuthorize("@ss.hasPermission('bpm:model:deploy')")
    public CommonResult<Boolean> deployModel(@RequestParam("id") Long id) {
        dataService.updateColumns("bpm_model", id, Map.of("status", 0));
        return CommonResult.success(true);
    }

    @DeleteMapping("/model/clean")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> cleanModel(@RequestParam("id") Long id) {
        dataService.updateColumns("bpm_model", id, Map.of("bpmn_xml", ""));
        return CommonResult.success(true);
    }

    @DeleteMapping("/model/delete")
    @PreAuthorize("@ss.hasPermission('bpm:model:delete')")
    public CommonResult<Boolean> deleteModel(@RequestParam("id") Long id) {
        dataService.delete(MODEL, id);
        return CommonResult.success(true);
    }

    @PostMapping("/model/simple/update")
    @PreAuthorize("@ss.hasPermission('bpm:model:update')")
    public CommonResult<Boolean> updateSimpleModel(@RequestBody Map<String, Object> reqVO) {
        dataService.update(MODEL, reqVO);
        return CommonResult.success(true);
    }

    @GetMapping("/model/simple/get")
    @PreAuthorize("@ss.hasPermission('bpm:model:query')")
    public CommonResult<Map<String, Object>> getSimpleModel(@RequestParam("id") Long id) {
        return CommonResult.success(withModelDefault(dataService.get(MODEL, id)));
    }

    @GetMapping("/process-expression/page")
    @PreAuthorize("@ss.hasPermission('bpm:process-expression:query')")
    public CommonResult<PageResult<Map<String, Object>>> getProcessExpressionPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(PROCESS_EXPRESSION, params));
    }

    @GetMapping("/process-expression/get")
    @PreAuthorize("@ss.hasPermission('bpm:process-expression:query')")
    public CommonResult<Map<String, Object>> getProcessExpression(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(PROCESS_EXPRESSION, id));
    }

    @PostMapping("/process-expression/create")
    @PreAuthorize("@ss.hasPermission('bpm:process-expression:create')")
    public CommonResult<Long> createProcessExpression(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(PROCESS_EXPRESSION, reqVO));
    }

    @PutMapping("/process-expression/update")
    @PreAuthorize("@ss.hasPermission('bpm:process-expression:update')")
    public CommonResult<Boolean> updateProcessExpression(@RequestBody Map<String, Object> reqVO) {
        dataService.update(PROCESS_EXPRESSION, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/process-expression/delete")
    @PreAuthorize("@ss.hasPermission('bpm:process-expression:delete')")
    public CommonResult<Boolean> deleteProcessExpression(@RequestParam("id") Long id) {
        dataService.delete(PROCESS_EXPRESSION, id);
        return CommonResult.success(true);
    }

    @GetMapping("/process-expression/export-excel")
    public ResponseEntity<byte[]> exportProcessExpression(@RequestParam Map<String, Object> params) {
        return excel("process-expression.xlsx", dataService.exportExcel(PROCESS_EXPRESSION, params, columns(
                "id", "编号",
                "name", "表达式名称",
                "status", "状态",
                "expression", "表达式",
                "remark", "备注",
                "createTime", "创建时间"), "流程表达式"));
    }

    @GetMapping("/process-listener/page")
    @PreAuthorize("@ss.hasPermission('bpm:process-listener:query')")
    public CommonResult<PageResult<Map<String, Object>>> getProcessListenerPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(PROCESS_LISTENER, params));
    }

    @GetMapping("/process-listener/get")
    @PreAuthorize("@ss.hasPermission('bpm:process-listener:query')")
    public CommonResult<Map<String, Object>> getProcessListener(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(PROCESS_LISTENER, id));
    }

    @PostMapping("/process-listener/create")
    @PreAuthorize("@ss.hasPermission('bpm:process-listener:create')")
    public CommonResult<Long> createProcessListener(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(PROCESS_LISTENER, reqVO));
    }

    @PutMapping("/process-listener/update")
    @PreAuthorize("@ss.hasPermission('bpm:process-listener:update')")
    public CommonResult<Boolean> updateProcessListener(@RequestBody Map<String, Object> reqVO) {
        dataService.update(PROCESS_LISTENER, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/process-listener/delete")
    @PreAuthorize("@ss.hasPermission('bpm:process-listener:delete')")
    public CommonResult<Boolean> deleteProcessListener(@RequestParam("id") Long id) {
        dataService.delete(PROCESS_LISTENER, id);
        return CommonResult.success(true);
    }

    @GetMapping("/user-group/page")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:query')")
    public CommonResult<PageResult<Map<String, Object>>> getUserGroupPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(USER_GROUP, params));
    }

    @GetMapping("/user-group/simple-list")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:query')")
    public CommonResult<List<Map<String, Object>>> getSimpleUserGroupList() {
        return CommonResult.success(dataService.list(USER_GROUP, Map.of("status", 0)));
    }

    @GetMapping("/user-group/get")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:query')")
    public CommonResult<Map<String, Object>> getUserGroup(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(USER_GROUP, id));
    }

    @PostMapping("/user-group/create")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:create')")
    public CommonResult<Long> createUserGroup(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(USER_GROUP, reqVO));
    }

    @PutMapping("/user-group/update")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:update')")
    public CommonResult<Boolean> updateUserGroup(@RequestBody Map<String, Object> reqVO) {
        dataService.update(USER_GROUP, reqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/user-group/delete")
    @PreAuthorize("@ss.hasPermission('bpm:user-group:delete')")
    public CommonResult<Boolean> deleteUserGroup(@RequestParam("id") Long id) {
        dataService.delete(USER_GROUP, id);
        return CommonResult.success(true);
    }

    @GetMapping("/process-definition/page")
    @PreAuthorize("@ss.hasPermission('bpm:process-definition:query')")
    public CommonResult<PageResult<Map<String, Object>>> getProcessDefinitionPage() {
        return CommonResult.success(emptyPage());
    }

    @GetMapping({"/process-definition/list", "/process-definition/simple-list"})
    @PreAuthorize("@ss.hasPermission('bpm:process-definition:query')")
    public CommonResult<List<Map<String, Object>>> getProcessDefinitionList() {
        return CommonResult.success(List.of());
    }

    @GetMapping("/process-definition/get")
    @PreAuthorize("@ss.hasPermission('bpm:process-definition:query')")
    public CommonResult<Map<String, Object>> getProcessDefinition() {
        return CommonResult.success(Map.of());
    }

    @GetMapping({"/process-instance/my-page", "/process-instance/manager-page", "/process-instance/copy/page"})
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<PageResult<Map<String, Object>>> getProcessInstancePage() {
        return CommonResult.success(emptyPage());
    }

    @PostMapping("/process-instance/create")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:create')")
    public CommonResult<Long> createProcessInstance() {
        return CommonResult.success(0L);
    }

    @DeleteMapping({"/process-instance/cancel-by-start-user", "/process-instance/cancel-by-admin"})
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:cancel')")
    public CommonResult<Boolean> cancelProcessInstance() {
        return CommonResult.success(true);
    }

    @GetMapping({"/process-instance/get", "/process-instance/get-approval-detail",
            "/process-instance/get-next-approval-nodes", "/process-instance/get-form-fields-permission",
            "/process-instance/get-bpmn-model-view", "/process-instance/get-print-data"})
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<Map<String, Object>> getProcessInstanceDetail() {
        return CommonResult.success(Map.of());
    }

    @GetMapping({"/task/todo-page", "/task/done-page", "/task/manager-page"})
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<PageResult<Map<String, Object>>> getTaskPage() {
        return CommonResult.success(emptyPage());
    }

    @GetMapping({"/task/list-by-process-instance-id", "/task/list-by-return", "/task/list-by-parent-task-id"})
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<Map<String, Object>>> getTaskList() {
        return CommonResult.success(List.of());
    }

    @GetMapping("/task/my-todo")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<Map<String, Object>> getMyTodoTask() {
        return CommonResult.success(Map.of());
    }

    @PutMapping({"/task/approve", "/task/reject", "/task/return", "/task/delegate", "/task/transfer",
            "/task/create-sign", "/task/copy"})
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> updateTaskAction() {
        return CommonResult.success(true);
    }

    @DeleteMapping("/task/delete-sign")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> deleteTaskSign() {
        return CommonResult.success(true);
    }

    @PutMapping("/task/withdraw")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> withdrawTask() {
        return CommonResult.success(true);
    }

    @GetMapping("/oa/leave/page")
    @PreAuthorize("@ss.hasPermission('bpm:oa-leave:query')")
    public CommonResult<PageResult<Map<String, Object>>> getLeavePage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(LEAVE, params));
    }

    @GetMapping("/oa/leave/get")
    @PreAuthorize("@ss.hasPermission('bpm:oa-leave:query')")
    public CommonResult<Map<String, Object>> getLeave(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(LEAVE, id));
    }

    @PostMapping("/oa/leave/create")
    @PreAuthorize("@ss.hasPermission('bpm:oa-leave:create')")
    public CommonResult<Long> createLeave(@RequestBody Map<String, Object> reqVO) {
        return CommonResult.success(dataService.create(LEAVE, reqVO));
    }

    private List<Map<String, Object>> withModelDefaults(List<Map<String, Object>> models) {
        models.forEach(this::withModelDefault);
        return models;
    }

    private Map<String, Object> withModelDefault(Map<String, Object> model) {
        model.putIfAbsent("processDefinition", null);
        return model;
    }

    private void updateSorts(String tableName, String ids) {
        if (ids == null || ids.isBlank()) {
            return;
        }
        int sort = 1;
        for (String id : ids.split(",")) {
            Long value = dataService.longValue(id.trim());
            if (value != null) {
                dataService.updateColumns(tableName, value, Map.of("sort", sort++));
            }
        }
    }

    private PageResult<Map<String, Object>> emptyPage() {
        return new PageResult<>(List.of(), 0L);
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
