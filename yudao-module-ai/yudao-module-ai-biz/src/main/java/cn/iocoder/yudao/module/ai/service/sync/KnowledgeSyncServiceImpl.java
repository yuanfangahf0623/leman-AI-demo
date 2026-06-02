package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncRecordMapper;
import cn.iocoder.yudao.module.ai.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentEmbeddingStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentParseStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncJobStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncRecordActionTypeEnum;
import cn.iocoder.yudao.module.ai.enums.SyncRecordStatusEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceRawRecordService;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrAttendanceRecordService;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrDataSourceConfig;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrOpenApiClient;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_MISMATCH;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_EXECUTE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_RUNNING;

/**
 * AI 知识库同步 Service 实现。
 *
 * <p>第一阶段只执行 FILE 数据源同步：从配置读取文件列表或目录，按内容 hash 判断新增、更新或跳过。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeSyncServiceImpl implements KnowledgeSyncService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "doc", "docx", "wps",
            "xls", "xlsx", "xlsb", "ppt", "pptx", "pptm");
    private static final Integer DEFAULT_COUNT = 0;
    private static final String DEFAULT_DOCUMENT_VERSION = "v1";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final String CALLBACK_TRIGGER_PREFIX = "CALLBACK:";
    private static final int TWO_HAO_HR_MAX_PAGE_SIZE = 50;
    private static final int TWO_HAO_HR_ATTENDANCE_BATCH_SIZE = 50;
    private static final int TWO_HAO_HR_KNOWLEDGE_RECORD_SAMPLE_LIMIT = 200;
    private static final int TWO_HAO_HR_KNOWLEDGE_RECORD_JSON_MAX_CHARS = 4000;
    private static final String TWO_HAO_HR_SALARY_PLAN_LIST_PATH = "/api/smart_salary/biz_sub/plan_list/";
    private static final String TWO_HAO_HR_SALARY_ITEM_LIST_PATH = "/api/smart_salary/biz_sub/item_list/";
    private static final String TWO_HAO_HR_SALARY_ITEM_LIST_TYPE = "smart_salary_item_list";

    private final AiSyncJobMapper syncJobMapper;
    private final AiSyncRecordMapper syncRecordMapper;
    private final AiDocumentMapper documentMapper;
    private final AiKnowledgeService knowledgeService;
    private final AiDataSourceService dataSourceService;
    private final AiDataSourceRawRecordService rawRecordService;
    private final TwoHaoHrAttendanceRecordService attendanceRecordService;
    private final AiDocumentService documentService;
    private final FileStorageService fileStorageService;
    private final TwoHaoHrOpenApiClient twoHaoHrOpenApiClient;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Override
    public void executeSyncJob(Long jobId) {
        long startNanos = System.nanoTime();
        Long tenantId = AiTenantContextHolder.getTenantId();
        AiSyncJobDO syncJob = validateSyncJobExists(jobId, tenantId);
        validateJobNotRunning(syncJob);

        SyncStats stats = new SyncStats();
        syncJobMapper.updateRunningByIdAndTenantId(jobId, tenantId, SyncJobStatusEnum.RUNNING.getCode(),
                LocalDateTime.now());
        try {
            validateKnowledgeExists(syncJob.getKnowledgeBaseId());
            AiDataSourceDO dataSource = validateDataSourceExists(syncJob.getDataSourceId());
            validateDataSource(syncJob, dataSource);

            if (DataSourceTypeEnum.FILE.getCode().equals(dataSource.getType())) {
                List<Path> files = resolveSyncFiles(dataSource.getConfigJson());
                for (Path file : files) {
                    syncOneFile(syncJob, dataSource, file, stats);
                }
            } else if (DataSourceTypeEnum.API.getCode().equals(dataSource.getType())) {
                syncApiDataSource(syncJob, dataSource, stats);
            } else {
                throw new ServiceException(SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED, "数据源类型暂不支持同步");
            }
            Integer finalStatus = stats.failCount > 0 ? SyncJobStatusEnum.FAILED.getCode()
                    : SyncJobStatusEnum.SUCCESS.getCode();
            String errorMessage = stats.failCount > 0 ? "部分数据同步失败" : null;
            syncJobMapper.updateResultByIdAndTenantId(jobId, tenantId, stats.totalCount, stats.successCount,
                    stats.failCount, finalStatus, LocalDateTime.now(), errorMessage);
            log.info("AI 数据源同步完成, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, sourceType={}, total={}, success={}, fail={}, elapsedMs={}",
                    syncJob.getId(), tenantId, syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(), dataSource.getType(),
                    stats.totalCount, stats.successCount, stats.failCount, elapsedMillis(startNanos));
        } catch (Exception ex) {
            String errorMessage = toSafeErrorMessage(ex, null);
            syncJobMapper.updateResultByIdAndTenantId(jobId, tenantId, stats.totalCount, stats.successCount,
                    stats.failCount, SyncJobStatusEnum.FAILED.getCode(), LocalDateTime.now(), errorMessage);
            log.warn("AI 数据源同步失败, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, total={}, success={}, fail={}, elapsedMs={}, reason={}",
                    syncJob.getId(), tenantId, syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    stats.totalCount, stats.successCount, stats.failCount, elapsedMillis(startNanos), errorMessage);
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "同步任务执行失败");
        }
    }

    private void syncApiDataSource(AiSyncJobDO syncJob, AiDataSourceDO dataSource, SyncStats stats) {
        TwoHaoHrDataSourceConfig config = TwoHaoHrDataSourceConfig.parse(dataSource.getConfigJson(), objectMapper);
        Set<String> scopedSyncObjects = resolveScopedSyncObjects(syncJob);
        boolean hasSyncObject = false;
        if (shouldSyncObject(config, scopedSyncObjects, "departments")) {
            hasSyncObject = true;
            syncTwoHaoHrDepartments(syncJob, dataSource, config, stats);
        }
        if (shouldSyncObject(config, scopedSyncObjects, "employees")) {
            hasSyncObject = true;
            syncTwoHaoHrEmployees(syncJob, dataSource, config, stats);
        }
        if (shouldSyncObject(config, scopedSyncObjects, "attendance")) {
            hasSyncObject = true;
            syncTwoHaoHrAttendance(syncJob, dataSource, config, stats);
        }
        if (shouldSyncObject(config, scopedSyncObjects, "approvals")
                || shouldSyncObject(config, scopedSyncObjects, "approval")) {
            hasSyncObject = true;
            syncTwoHaoHrApprovals(syncJob, dataSource, config, stats);
        }
        if (syncTwoHaoHrGenericReadEndpoints(syncJob, dataSource, config, scopedSyncObjects, stats)) {
            hasSyncObject = true;
        }
        if (!hasSyncObject) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部 API 数据源未配置同步对象");
        }
    }

    private Set<String> resolveScopedSyncObjects(AiSyncJobDO syncJob) {
        String triggerType = syncJob.getTriggerType();
        if (triggerType == null || !triggerType.startsWith(CALLBACK_TRIGGER_PREFIX)) {
            return Set.of();
        }
        String syncObjects = triggerType.substring(CALLBACK_TRIGGER_PREFIX.length());
        if (syncObjects.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(syncObjects.split(","))
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .forEach(value -> addScopedSyncObject(result, value));
        return result;
    }

    private void addScopedSyncObject(Set<String> result, String value) {
        result.add(value);
        switch (value) {
            case "card", "card_record" -> result.add("attendance_card_record");
            case "card_result", "result" -> result.add("attendance_card_result");
            case "leave" -> result.add("attendance_leave_record");
            case "ot", "overtime" -> result.add("attendance_overtime_record");
            case "outing", "out" -> result.add("attendance_outing_record");
            case "emp_details" -> result.add("employee_details");
            case "group_company" -> result.add("group_company_list");
            case "leaving" -> result.add("leaving_employee_list");
            case "transfer" -> result.add("employee_transfer");
            case "salary_fields" -> result.add("smart_salary_attendance_fields");
            case "interview" -> result.add("recruitment_interview");
            case "security" -> result.add("security_overview");
            case "entry_info" -> result.add("entry_info_list");
            case "room_booking" -> result.add("room_booking_list");
            default -> {
            }
        }
    }

    private boolean shouldSyncObject(TwoHaoHrDataSourceConfig config, Set<String> scopedSyncObjects,
                                     String objectName) {
        if (scopedSyncObjects.isEmpty()) {
            return config.shouldSync(objectName);
        }
        String normalized = objectName == null ? "" : objectName.trim().toLowerCase(Locale.ROOT);
        return (scopedSyncObjects.contains(normalized) || scopedSyncObjects.contains("all")) && config.shouldSync(objectName);
    }

    private void syncTwoHaoHrDepartments(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                         TwoHaoHrDataSourceConfig config, SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, "departments", "2号人事部组织架构",
                buildTwoHaoHrSourceUri("departments", config),
                () -> {
                    JsonNode departments = twoHaoHrOpenApiClient.fetchDepartments(config);
                    saveRawRecords(syncJob, dataSource, "organization", "departments",
                            buildTwoHaoHrSourceUri("departments", config), flattenRecords(departments));
                    return new ApiDocumentContent(buildTwoHaoHrDepartmentsMarkdown(departments),
                            countDepartments(departments));
                }, stats);
    }

    private void syncTwoHaoHrEmployees(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                       TwoHaoHrDataSourceConfig config, SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, "employees", "2号人事部员工基础信息",
                buildTwoHaoHrSourceUri("employees", config),
                () -> {
                    List<JsonNode> employees = fetchTwoHaoHrEmployees(config);
                    saveRawRecords(syncJob, dataSource, "hr", "employees",
                            buildTwoHaoHrSourceUri("employees", config), employees);
                    return new ApiDocumentContent(buildTwoHaoHrEmployeesMarkdown(employees, config),
                            employees.size());
                }, stats);
    }

    private void syncTwoHaoHrAttendance(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                        TwoHaoHrDataSourceConfig config, SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, "attendance", "2号人事部考勤概况",
                buildTwoHaoHrSourceUri("attendance", config),
                () -> {
                    JsonNode dailyOverview = twoHaoHrOpenApiClient.fetchAttendanceDailyOverview(config);
                    JsonNode monthlyOverview = twoHaoHrOpenApiClient.fetchAttendanceMonthlyOverview(config);
                    saveRawRecords(syncJob, dataSource, "attendance", "attendance_daily_overview",
                            buildTwoHaoHrSourceUri("attendance-daily-overview", config), recordsOf(dailyOverview));
                    saveRawRecords(syncJob, dataSource, "attendance", "attendance_monthly_overview",
                            buildTwoHaoHrSourceUri("attendance-monthly-overview", config), recordsOf(monthlyOverview));
                    List<JsonNode> employeeMonthResults = List.of();
                    if (Boolean.TRUE.equals(config.getAttendanceIncludeEmployeeMonthly())) {
                        List<String> employeeIds = fetchTwoHaoHrEmployeeIds(config);
                        employeeMonthResults = twoHaoHrOpenApiClient.fetchEmployeeMonthOverview(config, employeeIds);
                        saveRawRecords(syncJob, dataSource, "attendance", "attendance_employee_month_overview",
                                buildTwoHaoHrSourceUri("attendance-employee-month-overview", config),
                                employeeMonthResults);
                    }
                    return new ApiDocumentContent(buildTwoHaoHrAttendanceMarkdown(dailyOverview, monthlyOverview,
                            employeeMonthResults, config), 2 + employeeMonthResults.size());
                }, stats);
    }

    private void syncTwoHaoHrApprovals(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                       TwoHaoHrDataSourceConfig config, SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, "approvals", "2号人事部审批记录",
                buildTwoHaoHrSourceUri("approvals", config),
                () -> {
                    List<JsonNode> templates = twoHaoHrOpenApiClient.fetchApprovalTemplates(config);
                    List<JsonNode> records = twoHaoHrOpenApiClient.fetchApprovalRecords(config);
                    saveRawRecords(syncJob, dataSource, "approvals", "approval_templates",
                            buildTwoHaoHrSourceUri("approval-templates", config), templates);
                    saveRawRecords(syncJob, dataSource, "approvals", "approval_records",
                            buildTwoHaoHrSourceUri("approval-records", config), records);
                    return new ApiDocumentContent(buildTwoHaoHrApprovalsMarkdown(templates, records, config),
                            templates.size() + records.size());
                }, stats);
    }

    private void syncTwoHaoHrObject(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String objectType, String title,
                                    String sourceUri, ApiDocumentContentSupplier contentSupplier, SyncStats stats) {
        stats.totalCount++;
        LocalDateTime recordStartTime = LocalDateTime.now();
        Long documentId = null;
        String actionType = SyncRecordActionTypeEnum.ERROR.getCode();
        try {
            ApiDocumentContent content = contentSupplier.get();
            AiDataSourceIngestReqVO reqVO = new AiDataSourceIngestReqVO();
            reqVO.setDataSourceId(dataSource.getId());
            reqVO.setExternalId("twohaohr-" + objectType);
            reqVO.setSourceUri(sourceUri);
            reqVO.setTitle(title);
            reqVO.setContent(content.content());
            reqVO.setTags(List.of("2号人事部", "API", objectType));
            reqVO.setMetadata(Map.of(
                    "provider", TwoHaoHrDataSourceConfig.PROVIDER,
                    "objectType", objectType,
                    "recordCount", content.recordCount()
            ));
            AiDataSourceIngestRespVO response = documentService.createDocumentFromDataSource(dataSource, reqVO);
            documentId = response.getDocumentId();
            actionType = normalizeActionType(response.getAction());
            stats.successCount++;
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.SUCCESS.getCode(),
                    recordStartTime, null);
        } catch (Exception ex) {
            stats.failCount++;
            String errorMessage = toSafeErrorMessage(ex, null);
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.FAILED.getCode(),
                    recordStartTime, errorMessage);
            log.warn("2号人事部 API 同步对象失败, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, objectType={}, reason={}",
                    syncJob.getId(), syncJob.getTenantId(), syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    objectType, errorMessage);
        }
    }

    private boolean syncTwoHaoHrGenericReadEndpoints(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                                     TwoHaoHrDataSourceConfig config,
                                                     Set<String> scopedSyncObjects, SyncStats stats) {
        boolean hasSyncObject = false;
        for (TwoHaoHrReadEndpoint endpoint : buildTwoHaoHrReadEndpoints(config)) {
            if (isTwoHaoHrAttendanceEmployeeScopedObject(endpoint.objectType())
                    || !shouldSyncEndpoint(config, scopedSyncObjects, endpoint)) {
                continue;
            }
            hasSyncObject = true;
            syncTwoHaoHrReadEndpoint(syncJob, dataSource, config, endpoint, stats);
        }
        if (syncTwoHaoHrAttendanceEmployeeScopedEndpoints(syncJob, dataSource, config, scopedSyncObjects, stats)) {
            hasSyncObject = true;
        }
        if (shouldSyncAll(config, scopedSyncObjects)
                || shouldSyncObject(config, scopedSyncObjects, "employee_details")) {
            hasSyncObject = true;
            syncTwoHaoHrEmployeeDetailReadEndpoints(syncJob, dataSource, config, stats);
        }
        return hasSyncObject;
    }

    private boolean shouldSyncEndpoint(TwoHaoHrDataSourceConfig config, Set<String> scopedSyncObjects,
                                       TwoHaoHrReadEndpoint endpoint) {
        return shouldSyncAll(config, scopedSyncObjects) || shouldSyncObject(config, scopedSyncObjects, endpoint.objectType());
    }

    private boolean syncTwoHaoHrAttendanceEmployeeScopedEndpoints(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                                                  TwoHaoHrDataSourceConfig config,
                                                                  Set<String> scopedSyncObjects,
                                                                  SyncStats stats) {
        boolean hasSyncObject = false;
        for (TwoHaoHrReadEndpoint endpoint : buildTwoHaoHrAttendanceEmployeeScopedEndpoints(config)) {
            if (!shouldSyncEndpoint(config, scopedSyncObjects, endpoint)) {
                continue;
            }
            hasSyncObject = true;
            syncTwoHaoHrAttendanceEmployeeScopedEndpoint(syncJob, dataSource, config, endpoint, stats);
        }
        return hasSyncObject;
    }

    private void syncTwoHaoHrAttendanceEmployeeScopedEndpoint(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                                              TwoHaoHrDataSourceConfig config,
                                                              TwoHaoHrReadEndpoint endpoint, SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, endpoint.objectType(), endpoint.title(), endpoint.sourceUri(),
                () -> {
                    List<JsonNode> records = fetchTwoHaoHrAttendanceEmployeeScopedRecords(config, endpoint);
                    saveRawRecords(syncJob, dataSource, endpoint.moduleName(), endpoint.objectType(),
                            endpoint.sourceUri(), records);
                    attendanceRecordService.saveRecords(syncJob, dataSource, endpoint.objectType(), records);
                    return new ApiDocumentContent(buildTwoHaoHrRawRecordsMarkdown(endpoint, records), records.size());
                }, stats);
    }

    private List<JsonNode> fetchTwoHaoHrAttendanceEmployeeScopedRecords(TwoHaoHrDataSourceConfig config,
                                                                       TwoHaoHrReadEndpoint endpoint) {
        List<String> employeeIds = fetchTwoHaoHrEmployeeIds(config);
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        List<JsonNode> records = new ArrayList<>();
        for (int from = 0; from < employeeIds.size(); from += TWO_HAO_HR_ATTENDANCE_BATCH_SIZE) {
            int to = Math.min(from + TWO_HAO_HR_ATTENDANCE_BATCH_SIZE, employeeIds.size());
            Map<String, Object> payload = new LinkedHashMap<>(endpoint.payloadParams());
            payload.put("emp_ids", employeeIds.subList(from, to));
            payload.put("emp_oa_codes", List.of());
            payload.put("limit", TWO_HAO_HR_ATTENDANCE_BATCH_SIZE);
            records.addAll(twoHaoHrOpenApiClient.fetchPagedObjectsByPost(config, endpoint.path(), payload));
        }
        return records;
    }

    private List<String> fetchTwoHaoHrEmployeeIds(TwoHaoHrDataSourceConfig config) {
        return fetchTwoHaoHrEmployees(config).stream()
                .map(employee -> text(employee, "id"))
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private List<JsonNode> fetchTwoHaoHrEmployees(TwoHaoHrDataSourceConfig config) {
        if (hasDepartmentId(config)) {
            return twoHaoHrOpenApiClient.fetchEmployees(config);
        }
        List<String> departmentIds = fetchTwoHaoHrDepartmentIds(config);
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> employeesById = new LinkedHashMap<>();
        List<JsonNode> employeesWithoutId = new ArrayList<>();
        for (String departmentId : departmentIds) {
            TwoHaoHrDataSourceConfig scopedConfig = copyTwoHaoHrConfigForDepartment(config, departmentId, false);
            for (JsonNode employee : twoHaoHrOpenApiClient.fetchEmployees(scopedConfig)) {
                String employeeId = text(employee, "id");
                if (employeeId.isBlank()) {
                    employeesWithoutId.add(employee);
                    continue;
                }
                employeesById.putIfAbsent(employeeId, employee);
            }
        }
        List<JsonNode> employees = new ArrayList<>(employeesById.values());
        employees.addAll(employeesWithoutId);
        return employees;
    }

    private List<String> fetchTwoHaoHrDepartmentIds(TwoHaoHrDataSourceConfig config) {
        return flattenRecords(twoHaoHrOpenApiClient.fetchDepartments(config)).stream()
                .map(department -> text(department, "id"))
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private TwoHaoHrDataSourceConfig copyTwoHaoHrConfigForDepartment(TwoHaoHrDataSourceConfig config,
                                                                     String departmentId, boolean fetchChild) {
        TwoHaoHrDataSourceConfig scopedConfig = objectMapper.convertValue(config, TwoHaoHrDataSourceConfig.class);
        scopedConfig.setDepartmentId(departmentId);
        scopedConfig.setFetchChild(fetchChild);
        return scopedConfig;
    }

    private boolean hasDepartmentId(TwoHaoHrDataSourceConfig config) {
        return config.getDepartmentId() != null && !config.getDepartmentId().isBlank();
    }

    private List<TwoHaoHrReadEndpoint> buildTwoHaoHrAttendanceEmployeeScopedEndpoints(TwoHaoHrDataSourceConfig config) {
        Map<String, Object> attendanceDateRangePayload = Map.of(
                "start_dt", config.getAttendanceStartDate(),
                "end_dt", config.getAttendanceEndDate()
        );
        return List.of(
                postEndpoint("attendance", "attendance_card_record", "2号人事部打卡记录",
                        "/api/attendance/card_record/", attendanceDateRangePayload, true),
                postEndpoint("attendance", "attendance_card_result", "2号人事部打卡结果",
                        "/api/attendance/card_result/", attendanceDateRangePayload, true),
                postEndpoint("attendance", "attendance_leave_record", "2号人事部请假记录",
                        "/api/attendance/leave_record/", attendanceDateRangePayload, true),
                postEndpoint("attendance", "attendance_overtime_record", "2号人事部加班记录",
                        "/api/attendance/ot_record/", attendanceDateRangePayload, true),
                postEndpoint("attendance", "attendance_outing_record", "2号人事部外勤记录",
                        "/api/attendance/outing_record/", attendanceDateRangePayload, true)
        );
    }

    private boolean isTwoHaoHrAttendanceEmployeeScopedObject(String objectType) {
        return "attendance_card_record".equals(objectType)
                || "attendance_card_result".equals(objectType)
                || "attendance_leave_record".equals(objectType)
                || "attendance_overtime_record".equals(objectType)
                || "attendance_outing_record".equals(objectType);
    }

    private boolean shouldSyncAll(TwoHaoHrDataSourceConfig config, Set<String> scopedSyncObjects) {
        if (scopedSyncObjects.isEmpty()) {
            return config.shouldSync("all");
        }
        return scopedSyncObjects.contains("all") && config.shouldSync("all");
    }

    private List<TwoHaoHrReadEndpoint> buildTwoHaoHrReadEndpoints(TwoHaoHrDataSourceConfig config) {
        Map<String, Object> dateRangePayload = Map.of(
                "start_dt", config.getApprovalAddStartDate(),
                "end_dt", config.getApprovalAddEndDate(),
                "add_dt_min", config.getApprovalAddStartDate(),
                "add_dt_max", config.getApprovalAddEndDate()
        );
        Map<String, Object> yearMonthPayload = Map.of(
                "year", config.getAttendanceYear(),
                "month", config.getAttendanceMonth()
        );
        Map<String, String> yearMonthQuery = Map.of(
                "year", String.valueOf(config.getAttendanceYear()),
                "month", String.valueOf(config.getAttendanceMonth())
        );
        List<TwoHaoHrReadEndpoint> endpoints = new ArrayList<>();
        endpoints.add(getEndpoint("basic", "company_info", "2号人事部企业信息",
                "/api/company/info/", Map.of(), false));
        endpoints.add(getEndpoint("basic", "group_company_list", "2号人事部集团企业列表",
                "/api/group/company_list/", Map.of(), false));
        endpoints.add(getEndpoint("basic", "contract_company_list", "2号人事部合同公司列表",
                "/api/company/contract_company/list/", Map.of(), false));
        endpoints.add(getEndpoint("organization", "job_position_search", "2号人事部岗位查询",
                "/api/job_positions/job_title/paged_list/", Map.of(), true));
        endpoints.add(getEndpoint("organization", "job_group_list", "2号人事部岗位类别列表",
                "/api/job_positions/job_group/list/", Map.of(), false));
        endpoints.add(getEndpoint("organization", "job_positions", "2号人事部岗位列表",
                "/api/job_positions/", Map.of(), true));
        endpoints.add(getEndpoint("organization", "job_title_list", "2号人事部职务列表",
                "/api/job_positions/job_title/list/", Map.of(), true));
        endpoints.add(getEndpoint("organization", "job_levels", "2号人事部岗位职级",
                "/api/job_levels/", Map.of(), false));
        endpoints.add(getEndpoint("organization", "job_level_list", "2号人事部职级列表",
                "/api/job_level/list/", Map.of(), false));
        endpoints.add(getEndpoint("organization", "work_place_list", "2号人事部工作地点",
                "/api/work_place/list/", Map.of(), false));
        endpoints.add(getEndpoint("hr", "employees_search", "2号人事部员工搜索",
                "/api/employees/search/", Map.of(), true));
        endpoints.add(getEndpoint("hr", "intention_employee_search", "2号人事部待入职员工",
                "/api/intention_employee/search/", Map.of(), true));
        endpoints.add(getEndpoint("hr", "leave_employee_list", "2号人事部离职员工列表",
                "/api/employees/leave_list/", Map.of(), true));
        endpoints.add(getEndpoint("hr", "leaving_employee_list", "2号人事部待离职员工列表",
                "/api/employees/leaving_list/", Map.of(), true));
        endpoints.add(getEndpoint("hr", "employee_custom_fields", "2号人事部员工自定义字段",
                "/api/employees/custom/", Map.of(), false));
        endpoints.add(getEndpoint("hr", "employee_transfer", "2号人事部人事异动",
                "/api/emp_transfer/", Map.of(), true));
        endpoints.add(postEndpoint("attendance", "attendance_card_record", "2号人事部打卡记录",
                "/api/attendance/card_record/", dateRangePayload, true));
        endpoints.add(postEndpoint("attendance", "attendance_card_result", "2号人事部打卡结果",
                "/api/attendance/card_result/", dateRangePayload, true));
        endpoints.add(postEndpoint("attendance", "attendance_leave_record", "2号人事部请假记录",
                "/api/attendance/leave_record/", dateRangePayload, true));
        endpoints.add(postEndpoint("attendance", "attendance_overtime_record", "2号人事部加班记录",
                "/api/attendance/ot_record/", dateRangePayload, true));
        endpoints.add(postEndpoint("attendance", "attendance_outing_record", "2号人事部外勤记录",
                "/api/attendance/outing_record/", dateRangePayload, true));
        endpoints.add(postEndpoint("attendance", "attendance_shifts", "2号人事部班次列表",
                "/api/attendance/shifts/list/", Map.of("limit", TWO_HAO_HR_ATTENDANCE_BATCH_SIZE), true));
        endpoints.add(postEndpoint("attendance", "attendance_vacation_overview", "2号人事部假期概况",
                "/api/attendance/vacation_overview/", yearMonthPayload, false));
        endpoints.add(postEndpoint("smart_salary", "smart_salary_month_detail", "2号人事部智能薪酬月明细",
                "/api/smart_salary/accounting/month_detail_list/", yearMonthPayload, true));
        endpoints.add(postEndpoint("smart_salary", "smart_salary_month_total", "2号人事部智能薪酬月汇总",
                "/api/smart_salary/accounting/month_total_list/", yearMonthPayload, true));
        endpoints.add(postEndpoint("smart_salary", "smart_salary_plan_list", "2号人事部智能薪酬方案",
                "/api/smart_salary/biz_sub/plan_list/", Map.of("limit", TWO_HAO_HR_MAX_PAGE_SIZE), true));
        endpoints.add(getEndpoint("smart_salary", "smart_salary_item_list", "2号人事部智能薪酬项目",
                "/api/smart_salary/biz_sub/item_list/", Map.of(), false));
        endpoints.add(getEndpoint("smart_salary", "smart_salary_attendance_fields", "2号人事部薪酬考勤字段",
                "/api/smart_salary/attendance_stat/attendance_fields/", Map.of(), false));
        endpoints.add(getEndpoint("salary", "payslip_info", "2号人事部电子工资条",
                "/api/payslip/payslip_info/", yearMonthQuery, true));
        endpoints.add(postEndpoint("performance", "performance_plan_list", "2号人事部绩效计划",
                "/api/performance/plan/list/", Map.of(), true));
        endpoints.add(postEndpoint("performance", "performance_history_plan_list", "2号人事部绩效历史计划",
                "/api/performance/plan/history/list/", Map.of(), true));
        endpoints.add(postEndpoint("performance", "performance_examine_list", "2号人事部绩效考核列表",
                "/api/performance/plan/examine/list/", Map.of(), true));
        endpoints.add(getEndpoint("recruitment", "recruitment_interview", "2号人事部招聘面试",
                "/api/recruitment/interview/", Map.of(), true));
        endpoints.add(getEndpoint("social_security", "security_overview", "2号人事部社保信息",
                "/api/security/", Map.of(), false));
        endpoints.add(getEndpoint("econtract", "econtract_sign_balance", "2号人事部电子合同签署额度",
                "/api/econtract/sign_balance/", Map.of(), false));
        endpoints.add(getEndpoint("econtract", "econtract_category_list", "2号人事部电子合同分类",
                "/api/econtract/contract_category/list/", Map.of(), false));
        endpoints.add(getEndpoint("econtract", "econtract_package_list", "2号人事部电子合同包列表",
                "/api/econtract/package/list/", Map.of(), true));
        endpoints.add(postEndpoint("econtract", "econtract_search_list", "2号人事部电子合同列表",
                "/api/econtract/contract/search_list/", Map.of(), true));
        endpoints.add(getEndpoint("training", "entry_form_list", "2号人事部入职登记表模板",
                "/api/employee/emp_entry_sign/form_list/", Map.of(), false));
        endpoints.add(getEndpoint("training", "entry_info_list", "2号人事部入职登记信息",
                "/api/employee/emp_entry_sign/get_entry_info/", Map.of(), true));
        endpoints.add(getEndpoint("admin", "meeting_room_list", "2号人事部会议室列表",
                "/api/meeting_room/meeting_room_list/", Map.of(), false));
        endpoints.add(postEndpoint("admin", "room_booking_list", "2号人事部会议室预定",
                "/api/meeting_room/room_booking_list/", dateRangePayload, true));
        endpoints.add(getEndpoint("settings", "settings_contract_companies", "2号人事部设置-合同公司",
                "/api/contract_companies/", Map.of(), false));
        endpoints.add(getEndpoint("settings", "settings_work_places", "2号人事部设置-工作地点",
                "/api/work_places/", Map.of(), false));
        endpoints.add(getEndpoint("settings", "settings_leave_reasons", "2号人事部设置-离职原因",
                "/api/employee/leave_reason_list/", Map.of(), false));
        endpoints.add(getEndpoint("settings", "settings_transfer_reasons", "2号人事部设置-异动原因",
                "/api/employee/transfer_reason_list/", Map.of(), false));
        endpoints.add(getEndpoint("settings", "settings_contract_types", "2号人事部设置-合同类型",
                "/api/employees/contract_list/type/list/", Map.of("is_used", "1"), false));
        return endpoints;
    }

    private void syncTwoHaoHrEmployeeDetailReadEndpoints(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                                         TwoHaoHrDataSourceConfig config, SyncStats stats) {
        List<String> employeeIds = fetchTwoHaoHrEmployeeIds(config);
        if (employeeIds.isEmpty()) {
            return;
        }
        for (int from = 0; from < employeeIds.size(); from += 50) {
            String ids = String.join(",", employeeIds.subList(from, Math.min(from + 50, employeeIds.size())));
            for (TwoHaoHrReadEndpoint endpoint : buildTwoHaoHrEmployeeDetailEndpoints(ids)) {
                syncTwoHaoHrReadEndpoint(syncJob, dataSource, config, endpoint, stats);
            }
        }
    }

    private List<TwoHaoHrReadEndpoint> buildTwoHaoHrEmployeeDetailEndpoints(String ids) {
        Map<String, String> idQuery = Map.of("ids", ids);
        return List.of(
                getEndpoint("hr", "employee_base_info", "2号人事部员工基础信息明细",
                        "/api/employees/base_info/", idQuery, false),
                getEndpoint("hr", "employee_private_info", "2号人事部员工隐私信息",
                        "/api/employees/pravte_info/", idQuery, false),
                getEndpoint("hr", "employee_personal_info", "2号人事部员工个人信息",
                        "/api/employees/personal_info/", idQuery, false),
                getEndpoint("hr", "employee_professional_titles", "2号人事部员工职称",
                        "/api/employees/professional_title_list/", idQuery, false),
                getEndpoint("hr", "employee_certificates", "2号人事部员工证书",
                        "/api/employees/certificate_list/", idQuery, false),
                getEndpoint("hr", "employee_work_experience", "2号人事部员工工作经历",
                        "/api/employees/work_experience_list/", idQuery, false),
                getEndpoint("hr", "employee_education", "2号人事部员工教育经历",
                        "/api/employees/education_list/", idQuery, false),
                getEndpoint("hr", "employee_training_experience", "2号人事部员工培训经历",
                        "/api/employees/training_experience_list/", idQuery, false),
                getEndpoint("hr", "employee_contracts", "2号人事部员工合同",
                        "/api/employees/contract_list/", idQuery, false),
                getEndpoint("hr", "employee_contacts", "2号人事部员工紧急联系人",
                        "/api/employees/contact_list/", idQuery, false),
                getEndpoint("hr", "employee_languages", "2号人事部员工语言能力",
                        "/api/employees/language_list/", idQuery, false),
                getEndpoint("hr", "employee_family", "2号人事部员工家庭成员",
                        "/api/employees/family_list/", idQuery, false),
                getEndpoint("hr", "employee_skills", "2号人事部员工技能",
                        "/api/employees/work_skill_list/", idQuery, false),
                getEndpoint("hr", "employee_careers", "2号人事部员工职务经历",
                        "/api/employees/career_list/", idQuery, false),
                getEndpoint("hr", "employee_awards", "2号人事部员工奖惩记录",
                        "/api/employees/award_list/", idQuery, false)
        );
    }

    private void syncTwoHaoHrReadEndpoint(AiSyncJobDO syncJob, AiDataSourceDO dataSource,
                                          TwoHaoHrDataSourceConfig config, TwoHaoHrReadEndpoint endpoint,
                                          SyncStats stats) {
        syncTwoHaoHrObject(syncJob, dataSource, endpoint.objectType(), endpoint.title(), endpoint.sourceUri(),
                () -> {
                    List<JsonNode> records = fetchTwoHaoHrEndpointRecords(config, endpoint);
                    saveRawRecords(syncJob, dataSource, endpoint.moduleName(), endpoint.objectType(),
                            endpoint.sourceUri(), records);
                    if (shouldPersistTwoHaoHrAttendanceRecord(endpoint.objectType())) {
                        attendanceRecordService.saveRecords(syncJob, dataSource, endpoint.objectType(), records);
                    }
                    return new ApiDocumentContent(buildTwoHaoHrRawRecordsMarkdown(endpoint, records), records.size());
                }, stats);
    }

    private boolean shouldPersistTwoHaoHrAttendanceRecord(String objectType) {
        return isTwoHaoHrAttendanceEmployeeScopedObject(objectType)
                || "attendance_shifts".equals(objectType);
    }

    private List<JsonNode> fetchTwoHaoHrEndpointRecords(TwoHaoHrDataSourceConfig config,
                                                       TwoHaoHrReadEndpoint endpoint) {
        if (TWO_HAO_HR_SALARY_ITEM_LIST_TYPE.equals(endpoint.objectType())) {
            return fetchTwoHaoHrSalaryItemRecords(config);
        }
        if ("POST".equals(endpoint.method())) {
            if (endpoint.paged()) {
                return twoHaoHrOpenApiClient.fetchPagedObjectsByPost(config, endpoint.path(), endpoint.payloadParams());
            }
            return recordsOf(twoHaoHrOpenApiClient.fetchRawDataByPost(config, endpoint.path(), endpoint.payloadParams()));
        }
        if (endpoint.paged()) {
            return twoHaoHrOpenApiClient.fetchPagedObjectsByGet(config, endpoint.path(), endpoint.queryParams());
        }
        return recordsOf(twoHaoHrOpenApiClient.fetchRawDataByGet(config, endpoint.path(), endpoint.queryParams()));
    }

    private List<JsonNode> fetchTwoHaoHrSalaryItemRecords(TwoHaoHrDataSourceConfig config) {
        List<JsonNode> plans = twoHaoHrOpenApiClient.fetchPagedObjectsByPost(config, TWO_HAO_HR_SALARY_PLAN_LIST_PATH,
                Map.of("limit", TWO_HAO_HR_MAX_PAGE_SIZE));
        if (plans.isEmpty()) {
            return List.of();
        }
        List<JsonNode> records = new ArrayList<>();
        for (JsonNode plan : plans) {
            String planId = text(plan, "id");
            if (planId.isBlank()) {
                log.warn("2hao HR salary plan missing id, skip item sync");
                continue;
            }
            List<JsonNode> items = recordsOfResponseData(twoHaoHrOpenApiClient.fetchRawDataByGet(config,
                    TWO_HAO_HR_SALARY_ITEM_LIST_PATH, Map.of("sub_plan_id", planId)));
            for (JsonNode item : items) {
                records.add(enrichTwoHaoHrSalaryItem(item, plan));
            }
        }
        return records;
    }

    private JsonNode enrichTwoHaoHrSalaryItem(JsonNode item, JsonNode plan) {
        if (!item.isObject()) {
            return item;
        }
        ObjectNode enriched = item.deepCopy();
        String planId = text(plan, "id");
        if (!planId.isBlank()) {
            enriched.put("sub_plan_id", planId);
        }
        String planName = text(plan, "sub_name");
        if (!planName.isBlank()) {
            enriched.put("sub_plan_name", planName);
        }
        JsonNode planType = plan.path("sub_type");
        if (!planType.isMissingNode() && !planType.isNull()) {
            enriched.set("sub_plan_type", planType);
        }
        return enriched;
    }

    private String buildTwoHaoHrRawRecordsMarkdown(TwoHaoHrReadEndpoint endpoint, List<JsonNode> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(endpoint.title()).append("\n\n");
        builder.append("本内容由 2号人事部 API 同步生成。数据库保存真实返回值，知识库内容只写入脱敏后的 JSON 摘要。\n\n");
        builder.append("- 模块：").append(endpoint.moduleName()).append("\n");
        builder.append("- 对象：").append(endpoint.objectType()).append("\n");
        builder.append("- 记录数：").append(records.size()).append("\n\n");
        int sampleCount = Math.min(records.size(), TWO_HAO_HR_KNOWLEDGE_RECORD_SAMPLE_LIMIT);
        builder.append("- Knowledge document sample count: ").append(sampleCount).append("\n");
        if (records.size() > sampleCount) {
            builder.append("- Note: full raw records are stored in the database; structured statistics should use raw/structured tables.\n");
        }
        builder.append("\n");
        for (int i = 0; i < sampleCount; i++) {
            builder.append("## 记录 ").append(i + 1).append("\n\n");
            builder.append("```json\n")
                    .append(truncateKnowledgeRecordJson(rawRecordService.toMaskedJson(records.get(i))))
                    .append("\n```\n\n");
        }
        return builder.toString();
    }

    private String truncateKnowledgeRecordJson(String json) {
        if (json == null || json.length() <= TWO_HAO_HR_KNOWLEDGE_RECORD_JSON_MAX_CHARS) {
            return json == null ? "" : json;
        }
        return json.substring(0, TWO_HAO_HR_KNOWLEDGE_RECORD_JSON_MAX_CHARS)
                + "\n... truncated for knowledge document ...";
    }

    private void saveRawRecords(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String moduleName,
                                String objectType, String sourceUri, List<JsonNode> records) {
        rawRecordService.saveRecords(syncJob, dataSource, TwoHaoHrDataSourceConfig.PROVIDER, moduleName, objectType,
                sourceUri, records);
    }

    private List<JsonNode> flattenRecords(JsonNode node) {
        List<JsonNode> records = new ArrayList<>();
        flattenAny(node, records);
        return records;
    }

    private void flattenAny(JsonNode node, List<JsonNode> records) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                flattenAny(item, records);
            }
            return;
        }
        records.add(node);
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                flattenAny(child, records);
            }
        }
    }

    private List<JsonNode> recordsOf(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return List.of();
        }
        JsonNode objects = data.path("objects");
        if (objects.isArray()) {
            List<JsonNode> records = new ArrayList<>();
            objects.forEach(records::add);
            return records;
        }
        if (data.isArray()) {
            List<JsonNode> records = new ArrayList<>();
            data.forEach(records::add);
            return records;
        }
        return List.of(data);
    }

    private List<JsonNode> recordsOfResponseData(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        JsonNode data = root.path("data");
        if (!data.isMissingNode() && !data.isNull()) {
            return recordsOf(data);
        }
        return recordsOf(root);
    }

    private TwoHaoHrReadEndpoint getEndpoint(String moduleName, String objectType, String title, String path,
                                             Map<String, String> queryParams, boolean paged) {
        return new TwoHaoHrReadEndpoint(moduleName, objectType, title, "GET", path, queryParams, Map.of(), paged);
    }

    private TwoHaoHrReadEndpoint postEndpoint(String moduleName, String objectType, String title, String path,
                                              Map<String, Object> payloadParams, boolean paged) {
        return new TwoHaoHrReadEndpoint(moduleName, objectType, title, "POST", path, Map.of(), payloadParams, paged);
    }

    private void syncOneFile(AiSyncJobDO syncJob, AiDataSourceDO dataSource, Path file, SyncStats stats) {
        stats.totalCount++;
        LocalDateTime recordStartTime = LocalDateTime.now();
        String sourceUri = toSourceUri(file);
        String actionType = SyncRecordActionTypeEnum.ERROR.getCode();
        Long documentId = null;
        try {
            validateRegularReadableFile(file);
            String fileName = sanitizeFileName(file.getFileName().toString());
            String extension = getSupportedExtension(fileName);
            long fileSize = Files.size(file);
            validateFileSize(fileSize);
            byte[] content = Files.readAllBytes(file);
            String contentHash = sha256Hex(content);

            AiDocumentDO oldDocument = documentMapper.selectBySourceUri(syncJob.getTenantId(),
                    syncJob.getKnowledgeBaseId(), dataSource.getId(), sourceUri);
            if (oldDocument != null && contentHash.equals(oldDocument.getContentHash())) {
                actionType = SyncRecordActionTypeEnum.SKIP.getCode();
                documentId = oldDocument.getId();
                stats.successCount++;
                insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.SUCCESS.getCode(),
                        recordStartTime, null);
                return;
            }

            FileStorageResult storageResult = fileStorageService.store(buildObjectKey(syncJob, extension), content);
            if (oldDocument == null) {
                actionType = SyncRecordActionTypeEnum.CREATE.getCode();
                documentId = createDocument(syncJob, dataSource, fileName, extension, fileSize, sourceUri, contentHash,
                        storageResult);
            } else {
                actionType = SyncRecordActionTypeEnum.UPDATE.getCode();
                documentId = updateDocument(syncJob, oldDocument, fileName, extension, fileSize, sourceUri, contentHash,
                        storageResult);
            }
            // 只有新增或更新的文档才自动进入解析和向量化；未变化文件保持 SKIP，不重复处理。
            parseAndEmbedDocument(documentId);
            stats.successCount++;
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.SUCCESS.getCode(),
                    recordStartTime, null);
        } catch (Exception ex) {
            stats.failCount++;
            String errorMessage = toSafeErrorMessage(ex, file);
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.FAILED.getCode(),
                    recordStartTime, errorMessage);
            log.warn("FILE 数据源单文件同步失败, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, actionType={}, reason={}",
                    syncJob.getId(), syncJob.getTenantId(), syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    actionType, errorMessage);
        }
    }

    private Long createDocument(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String fileName, String extension,
                                long fileSize, String sourceUri, String contentHash,
                                FileStorageResult storageResult) {
        AiDocumentDO document = buildDocument(syncJob, dataSource, fileName, extension, fileSize, sourceUri,
                contentHash, storageResult);
        documentMapper.insert(document);
        return document.getId();
    }

    private Long updateDocument(AiSyncJobDO syncJob, AiDocumentDO oldDocument, String fileName, String extension,
                                long fileSize, String sourceUri, String contentHash,
                                FileStorageResult storageResult) {
        AiDocumentDO updateObj = buildDocument(syncJob, null, fileName, extension, fileSize, sourceUri, contentHash,
                storageResult);
        updateObj.setId(oldDocument.getId());
        updateObj.setDataSourceId(oldDocument.getDataSourceId());
        updateObj.setDirectoryId(oldDocument.getDirectoryId());
        updateObj.setDocumentVersion(nextDocumentVersion(oldDocument.getDocumentVersion()));
        documentMapper.updateSyncDocumentByIdAndTenantId(updateObj, syncJob.getTenantId());
        return oldDocument.getId();
    }

    private void parseAndEmbedDocument(Long documentId) {
        if (documentId == null) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "同步文档编号为空");
        }
        documentService.parseDocument(documentId);
        documentService.embedDocument(documentId);
    }

    private AiDocumentDO buildDocument(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String fileName, String extension,
                                       long fileSize, String sourceUri, String contentHash,
                                       FileStorageResult storageResult) {
        AiDocumentDO document = new AiDocumentDO();
        document.setTenantId(syncJob.getTenantId());
        document.setKnowledgeBaseId(syncJob.getKnowledgeBaseId());
        document.setDataSourceId(dataSource != null ? dataSource.getId() : syncJob.getDataSourceId());
        document.setDocumentVersion(DEFAULT_DOCUMENT_VERSION);
        document.setTitle(removeExtension(fileName));
        document.setFileName(fileName);
        document.setFileType(extension);
        document.setFileSize(fileSize);
        document.setObjectKey(storageResult.getObjectKey());
        document.setSourceUri(sourceUri);
        document.setContentHash(contentHash);
        document.setParseStatus(DocumentParseStatusEnum.PENDING.getCode());
        document.setEmbeddingStatus(DocumentEmbeddingStatusEnum.PENDING.getCode());
        document.setChunkCount(DEFAULT_COUNT);
        document.setTokenCount(DEFAULT_COUNT);
        document.setErrorMessage(null);
        return document;
    }

    private String nextDocumentVersion(String oldVersion) {
        if (oldVersion == null || oldVersion.isBlank()) {
            return DEFAULT_DOCUMENT_VERSION;
        }
        String normalized = oldVersion.trim();
        if (normalized.length() > 1 && normalized.charAt(0) == 'v') {
            try {
                int number = Integer.parseInt(normalized.substring(1));
                return "v" + (number + 1);
            } catch (NumberFormatException ignored) {
                // 非 vN 格式的版本号保留旧值，避免同步任务误改业务定义的版本。
            }
        }
        return normalized;
    }

    private void insertSyncRecord(AiSyncJobDO syncJob, Long documentId, String sourceUri, String actionType,
                                  Integer status, LocalDateTime startTime, String errorMessage) {
        AiSyncRecordDO record = new AiSyncRecordDO();
        record.setTenantId(syncJob.getTenantId());
        record.setSyncJobId(syncJob.getId());
        record.setKnowledgeBaseId(syncJob.getKnowledgeBaseId());
        record.setDataSourceId(syncJob.getDataSourceId());
        record.setDocumentId(documentId);
        record.setSourceUri(sourceUri);
        record.setActionType(actionType);
        record.setStatus(status);
        record.setStartTime(startTime);
        record.setEndTime(LocalDateTime.now());
        record.setErrorMessage(errorMessage);
        syncRecordMapper.insert(record);
    }

    private AiSyncJobDO validateSyncJobExists(Long jobId, Long tenantId) {
        AiSyncJobDO syncJob = syncJobMapper.selectByIdAndTenantId(jobId, tenantId);
        if (syncJob == null) {
            throw new ServiceException(SYNC_JOB_NOT_EXISTS, "同步任务不存在");
        }
        return syncJob;
    }

    private void validateJobNotRunning(AiSyncJobDO syncJob) {
        if (SyncJobStatusEnum.RUNNING.getCode().equals(syncJob.getStatus())) {
            throw new ServiceException(SYNC_JOB_RUNNING, "同步任务正在执行");
        }
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
        if (knowledgeService.getKnowledge(knowledgeBaseId) == null) {
            throw new ServiceException(SYNC_JOB_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
    }

    private AiDataSourceDO validateDataSourceExists(Long dataSourceId) {
        AiDataSourceDO dataSource = dataSourceService.getDataSource(dataSourceId);
        if (dataSource == null) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    private void validateDataSource(AiSyncJobDO syncJob, AiDataSourceDO dataSource) {
        if (!syncJob.getKnowledgeBaseId().equals(dataSource.getKnowledgeBaseId())) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_MISMATCH, "数据源不属于当前知识库");
        }
        if (!DataSourceTypeEnum.FILE.getCode().equals(dataSource.getType())
                && !DataSourceTypeEnum.API.getCode().equals(dataSource.getType())) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED, "数据源类型暂不支持同步");
        }
    }

    private String buildTwoHaoHrDepartmentsMarkdown(JsonNode departments) {
        List<JsonNode> flattened = new ArrayList<>();
        flattenDepartments(departments, flattened);
        StringBuilder builder = new StringBuilder();
        builder.append("# 2号人事部组织架构\n\n");
        builder.append("本内容由 2号人事部 API 数据源同步生成。\n\n");
        builder.append("| 组织ID | 组织名称 | 组织编码 | 上级组织ID | 负责人 | 状态 | 组织职能 |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (JsonNode department : flattened) {
            builder.append("| ")
                    .append(markdownCell(text(department, "id"))).append(" | ")
                    .append(markdownCell(text(department, "name"))).append(" | ")
                    .append(markdownCell(text(department, "serial_no", "dept_oa_code"))).append(" | ")
                    .append(markdownCell(text(department, "parentid", "sup_department_id"))).append(" | ")
                    .append(markdownCell(text(department, "leader_name"))).append(" | ")
                    .append(markdownCell(text(department, "status"))).append(" | ")
                    .append(markdownCell(text(department, "ability", "description"))).append(" |\n");
        }
        return builder.toString();
    }

    private String buildTwoHaoHrEmployeesMarkdown(List<JsonNode> employees, TwoHaoHrDataSourceConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 2号人事部员工基础信息\n\n");
        builder.append("本内容由 2号人事部 API 数据源同步生成。默认只写入员工基础字段，手机号、邮箱、证件号、生日等个人敏感字段不进入知识库。\n\n");
        builder.append("| 员工ID | 姓名 | 工号 | 部门ID | 岗位ID | 职级ID | 员工状态 | 入职日期 | 合同公司ID");
        if (Boolean.TRUE.equals(config.getIncludeSensitiveFields())) {
            builder.append(" | 手机号 | 邮箱 | 证件号 | 生日");
        }
        builder.append(" |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | ---");
        if (Boolean.TRUE.equals(config.getIncludeSensitiveFields())) {
            builder.append(" | --- | --- | --- | ---");
        }
        builder.append(" |\n");
        for (JsonNode employee : employees) {
            builder.append("| ")
                    .append(markdownCell(text(employee, "id"))).append(" | ")
                    .append(markdownCell(text(employee, "name"))).append(" | ")
                    .append(markdownCell(text(employee, "emp_no"))).append(" | ")
                    .append(markdownCell(text(employee, "department_id"))).append(" | ")
                    .append(markdownCell(text(employee, "job_position_id"))).append(" | ")
                    .append(markdownCell(text(employee, "job_level_id"))).append(" | ")
                    .append(markdownCell(text(employee, "work_status"))).append(" | ")
                    .append(markdownCell(text(employee, "hire_date"))).append(" | ")
                    .append(markdownCell(text(employee, "contract_company_id")));
            if (Boolean.TRUE.equals(config.getIncludeSensitiveFields())) {
                builder.append(" | ").append(markdownCell(text(employee, "mobile")))
                        .append(" | ").append(markdownCell(text(employee, "email", "work_email")))
                        .append(" | ").append(markdownCell(text(employee, "credentials_no")))
                        .append(" | ").append(markdownCell(text(employee, "birthday")));
            }
            builder.append(" |\n");
        }
        return builder.toString();
    }

    private String buildTwoHaoHrAttendanceMarkdown(JsonNode dailyOverview, JsonNode monthlyOverview,
                                                   List<JsonNode> employeeMonthResults,
                                                   TwoHaoHrDataSourceConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 2号人事部考勤概况\n\n");
        builder.append("本内容由 2号人事部 API 数据源同步生成。默认只写入部门或全公司的考勤汇总，不写入个人考勤明细。\n\n");
        builder.append("- 查询日期：").append(markdownCell(config.getAttendanceQueryDate())).append("\n");
        builder.append("- 统计年月：").append(config.getAttendanceYear()).append("-")
                .append(config.getAttendanceMonth()).append("\n");
        builder.append("- 统计范围：").append(config.getDepartmentId() == null || config.getDepartmentId().isBlank()
                ? "全公司" : "部门 " + config.getDepartmentId()).append("\n\n");
        appendAttendanceOverview(builder, "每日考勤概况", dailyOverview);
        appendAttendanceOverview(builder, "每月考勤概况", monthlyOverview);
        if (!employeeMonthResults.isEmpty()) {
            builder.append("## 员工月度考勤结果\n\n");
            builder.append("> 该段只有在 includeSensitiveFields 和 attendanceIncludeEmployeeMonthly 均开启时才会生成。\n\n");
            builder.append("| 员工ID | 员工姓名 | 工号 | 部门 | 应出勤天数 | 应出勤小时 | 实际工时 | 请假小时 | 加班小时 | 迟到次数 | 早退次数 | 缺卡次数 | 旷工次数 |\n");
            builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (JsonNode item : employeeMonthResults) {
                builder.append("| ")
                        .append(markdownCell(text(item, "emp_id"))).append(" | ")
                        .append(markdownCell(text(item, "emp_name"))).append(" | ")
                        .append(markdownCell(text(item, "emp_no", "emp_oa_code"))).append(" | ")
                        .append(markdownCell(text(item, "dep_name", "dept_id"))).append(" | ")
                        .append(markdownCell(text(item, "expected_attend_day"))).append(" | ")
                        .append(markdownCell(text(item, "expected_attend_hour"))).append(" | ")
                        .append(markdownCell(text(item, "work_hour"))).append(" | ")
                        .append(markdownCell(text(item, "leave_hour_count"))).append(" | ")
                        .append(markdownCell(sumText(item, "weekday_overtime_hours", "weekend_overtime_hours",
                                "holiday_overtime_hours"))).append(" | ")
                        .append(markdownCell(text(item, "late_count"))).append(" | ")
                        .append(markdownCell(text(item, "early_leave_count"))).append(" | ")
                        .append(markdownCell(text(item, "missing_clock_count"))).append(" | ")
                        .append(markdownCell(text(item, "absenteeism_count"))).append(" |\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private void appendAttendanceOverview(StringBuilder builder, String title, JsonNode overview) {
        JsonNode department = overview.path("department_data");
        JsonNode attendance = overview.path("attendance_data");
        builder.append("## ").append(title).append("\n\n");
        builder.append("| 部门ID | 部门名称 | 总人数 | 正常 | 异常 | 迟到 | 早退 | 缺卡 | 旷工 | 请假 | 加班 | 外勤 | 出差 | 未排班 |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        builder.append("| ")
                .append(markdownCell(text(department, "department_id"))).append(" | ")
                .append(markdownCell(text(department, "department_name"))).append(" | ")
                .append(markdownCell(text(attendance, "all"))).append(" | ")
                .append(markdownCell(text(attendance, "normal", "full_time"))).append(" | ")
                .append(markdownCell(text(attendance, "abnormal", "not_full_time"))).append(" | ")
                .append(markdownCell(text(attendance, "late"))).append(" | ")
                .append(markdownCell(text(attendance, "early"))).append(" | ")
                .append(markdownCell(text(attendance, "absent"))).append(" | ")
                .append(markdownCell(text(attendance, "absenteeism"))).append(" | ")
                .append(markdownCell(text(attendance, "leave"))).append(" | ")
                .append(markdownCell(text(attendance, "overtime"))).append(" | ")
                .append(markdownCell(text(attendance, "outing_work", "outing_hours"))).append(" | ")
                .append(markdownCell(text(attendance, "business_trip"))).append(" | ")
                .append(markdownCell(text(attendance, "not_scheduler"))).append(" |\n\n");
    }

    private String buildTwoHaoHrApprovalsMarkdown(List<JsonNode> templates, List<JsonNode> records,
                                                  TwoHaoHrDataSourceConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 2号人事部审批记录\n\n");
        builder.append("本内容由 2号人事部 API 数据源同步生成。默认只写入审批模板和审批记录摘要，不写入审批详情、附件或表单明细。\n\n");
        builder.append("- 创建时间范围：").append(markdownCell(config.getApprovalAddStartDate()))
                .append(" 至 ").append(markdownCell(config.getApprovalAddEndDate())).append("\n\n");
        builder.append("## 审批模板\n\n");
        builder.append("| 模板ID | 模板名称 | 模板类型 | 是否启用 | 描述 |\n");
        builder.append("| --- | --- | --- | --- | --- |\n");
        for (JsonNode template : templates) {
            builder.append("| ")
                    .append(markdownCell(text(template, "id"))).append(" | ")
                    .append(markdownCell(text(template, "title"))).append(" | ")
                    .append(markdownCell(text(template, "type"))).append(" | ")
                    .append(markdownCell(text(template, "is_enable"))).append(" | ")
                    .append(markdownCell(text(template, "desc"))).append(" |\n");
        }
        builder.append("\n## 审批记录\n\n");
        builder.append("| 审批ID | 单号 | 标题 | 类型 | 状态 | 申请人 | 申请部门 | 发起人 | 当前审批人 | 创建时间 | 更新时间 | 摘要 |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (JsonNode record : records) {
            builder.append("| ")
                    .append(markdownCell(text(record, "id"))).append(" | ")
                    .append(markdownCell(text(record, "no"))).append(" | ")
                    .append(markdownCell(text(record, "title"))).append(" | ")
                    .append(markdownCell(text(record, "type_name", "type"))).append(" | ")
                    .append(markdownCell(approvalStatusText(record.path("status").asInt(0)))).append(" | ")
                    .append(markdownCell(text(record, "target_name"))).append(" | ")
                    .append(markdownCell(text(record, "submit_dep_name", "dep_name"))).append(" | ")
                    .append(markdownCell(text(record, "emp_name"))).append(" | ")
                    .append(markdownCell(text(record, "approver"))).append(" | ")
                    .append(markdownCell(text(record, "add_dt"))).append(" | ")
                    .append(markdownCell(text(record, "update_dt"))).append(" | ")
                    .append(markdownCell(approvalAbstract(record))).append(" |\n");
        }
        return builder.toString();
    }

    private void flattenDepartments(JsonNode departments, List<JsonNode> output) {
        if (departments == null || !departments.isArray()) {
            return;
        }
        for (JsonNode department : departments) {
            output.add(department);
            flattenDepartments(department.path("children"), output);
        }
    }

    private int countDepartments(JsonNode departments) {
        List<JsonNode> flattened = new ArrayList<>();
        flattenDepartments(departments, flattened);
        return flattened.size();
    }

    private String buildTwoHaoHrSourceUri(String objectType, TwoHaoHrDataSourceConfig config) {
        StringBuilder builder = new StringBuilder("twohaohr://").append(objectType);
        if (config.getDepartmentId() != null && !config.getDepartmentId().isBlank()) {
            builder.append("?departmentId=").append(config.getDepartmentId().trim());
        }
        return builder.toString();
    }

    private String normalizeActionType(String action) {
        if (action == null || action.isBlank()) {
            return SyncRecordActionTypeEnum.UPDATE.getCode();
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "CREATE" -> SyncRecordActionTypeEnum.CREATE.getCode();
            case "SKIP" -> SyncRecordActionTypeEnum.SKIP.getCode();
            default -> SyncRecordActionTypeEnum.UPDATE.getCode();
        };
    }

    private String approvalStatusText(int status) {
        return switch (status) {
            case 1 -> "进行中";
            case 2 -> "审批通过";
            case 3 -> "审批拒绝";
            case 4 -> "审批撤销";
            default -> status == 0 ? "" : String.valueOf(status);
        };
    }

    private String approvalAbstract(JsonNode record) {
        JsonNode abstracts = record.path("abstract");
        if (!abstracts.isArray() || abstracts.isEmpty()) {
            return text(record, "desc");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : abstracts) {
            String title = text(item, "title");
            String value = text(item, "value");
            if (!title.isBlank() || !value.isBlank()) {
                values.add(title + "：" + value);
            }
        }
        return String.join("；", values);
    }

    private String sumText(JsonNode node, String... fieldNames) {
        double sum = 0D;
        boolean hasValue = false;
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                sum += value.asDouble();
                hasValue = true;
            } else if (value.isTextual() && !value.asText().isBlank()) {
                try {
                    sum += Double.parseDouble(value.asText());
                    hasValue = true;
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric external fields.
                }
            }
        }
        if (!hasValue) {
            return "";
        }
        return sum == Math.rint(sum) ? String.valueOf((long) sum) : String.valueOf(sum);
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.isTextual() ? value.asText() : value.asText("");
            }
        }
        return "";
    }

    private String markdownCell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private List<Path> resolveSyncFiles(String configJson) {
        JsonNode config = parseConfig(configJson);
        LinkedHashMap<String, Path> files = new LinkedHashMap<>();
        addConfiguredFiles(config, files);
        addDirectoryFiles(config, files);
        if (files.isEmpty()) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源未配置可同步文件");
        }
        return new ArrayList<>(files.values());
    }

    private JsonNode parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置不能为空");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (config == null || !config.isObject()) {
                throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置格式非法");
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置 JSON 非法");
        }
    }

    private void addConfiguredFiles(JsonNode config, LinkedHashMap<String, Path> files) {
        for (String key : List.of("files", "filePaths", "fileList")) {
            JsonNode node = config.get(key);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isTextual()) {
                addPath(files, node.asText());
                continue;
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    addPath(files, extractFilePath(item));
                }
            }
        }
    }

    private String extractFilePath(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isTextual()) {
            return item.asText();
        }
        if (item.isObject()) {
            JsonNode pathNode = item.get("path");
            if (pathNode == null || pathNode.isNull()) {
                pathNode = item.get("filePath");
            }
            return pathNode != null && pathNode.isTextual() ? pathNode.asText() : null;
        }
        return null;
    }

    private void addDirectoryFiles(JsonNode config, LinkedHashMap<String, Path> files) {
        String directory = firstText(config, "directory", "directoryPath", "rootPath");
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path rootPath = Paths.get(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步目录不存在或不可访问");
        }
        boolean recursive = config.path("recursive").asBoolean(false);
        Set<String> includeExtensions = resolveIncludeExtensions(config);
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(rootPath, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> includeExtensions.contains(getExtension(path.getFileName().toString())))
                    .forEach(path -> addPath(files, path.toString()));
        } catch (IOException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "读取同步目录失败");
        }
    }

    private String firstText(JsonNode config, String... keys) {
        for (String key : keys) {
            JsonNode node = config.get(key);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private Set<String> resolveIncludeExtensions(JsonNode config) {
        JsonNode node = config.get("includeExtensions");
        if (node == null || node.isNull()) {
            return SUPPORTED_EXTENSIONS;
        }
        Set<String> extensions = new LinkedHashSet<>();
        if (node.isTextual()) {
            extensions.add(normalizeExtension(node.asText()));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    extensions.add(normalizeExtension(item.asText()));
                }
            }
        }
        extensions.remove("");
        return extensions.isEmpty() ? SUPPORTED_EXTENSIONS : extensions;
    }

    private void addPath(LinkedHashMap<String, Path> files, String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }
        Path path = Paths.get(pathValue).toAbsolutePath().normalize();
        files.put(path.toString(), path);
    }

    private void validateRegularReadableFile(Path file) {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件不存在或不可读取");
        }
    }

    private void validateFileSize(long fileSize) {
        Integer maxFileSizeMb = aiProperties.getDocument().getMaxFileSizeMb();
        long maxFileSizeBytes = maxFileSizeMb.longValue() * 1024L * 1024L;
        if (fileSize > maxFileSizeBytes) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件大小超过限制");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.contains("..")
                || hasControlChar(fileName)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件名非法");
        }
        return fileName;
    }

    private boolean hasControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String getSupportedExtension(String fileName) {
        String extension = getExtension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件类型不支持");
        }
        return extension;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return normalizeExtension(fileName.substring(dotIndex + 1));
    }

    private String normalizeExtension(String extension) {
        return extension == null ? "" : extension.trim().replace(".", "").toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(AiSyncJobDO syncJob, String extension) {
        return "ai/sync/" + syncJob.getTenantId() + "/" + syncJob.getKnowledgeBaseId() + "/"
                + syncJob.getDataSourceId() + "/" + UUID.randomUUID() + "." + extension;
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String toSourceUri(Path path) {
        return path.toAbsolutePath().normalize().toUri().toString();
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "计算文件哈希失败");
        }
    }

    private String toSafeErrorMessage(Exception ex, Path sourcePath) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        if (sourcePath != null) {
            String normalizedPath = sourcePath.toAbsolutePath().normalize().toString();
            message = message.replace(normalizedPath, "[file]");
            message = message.replace(sourcePath.toAbsolutePath().normalize().toUri().toString(), "[file]");
        }
        return abbreviate(message, ERROR_MESSAGE_MAX_LENGTH);
    }

    private String abbreviate(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    @FunctionalInterface
    private interface ApiDocumentContentSupplier {

        ApiDocumentContent get();

    }

    private record ApiDocumentContent(String content, int recordCount) {
    }

    private record TwoHaoHrReadEndpoint(String moduleName, String objectType, String title, String method, String path,
                                        Map<String, String> queryParams, Map<String, Object> payloadParams,
                                        boolean paged) {

        private String sourceUri() {
            return "twohaohr://" + objectType;
        }

    }

    private static final class SyncStats {

        private int totalCount;
        private int successCount;
        private int failCount;

    }

}
