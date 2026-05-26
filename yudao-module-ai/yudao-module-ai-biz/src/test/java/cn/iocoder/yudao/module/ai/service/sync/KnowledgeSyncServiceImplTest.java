package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceIngestRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncRecordMapper;
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
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrOpenApiClient;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSyncServiceImplTest {

    @TempDir
    private Path tempDir;

    @Mock
    private AiSyncJobMapper syncJobMapper;
    @Mock
    private AiSyncRecordMapper syncRecordMapper;
    @Mock
    private AiDocumentMapper documentMapper;
    @Mock
    private AiKnowledgeService knowledgeService;
    @Mock
    private AiDataSourceService dataSourceService;
    @Mock
    private AiDataSourceRawRecordService rawRecordService;
    @Mock
    private TwoHaoHrAttendanceRecordService attendanceRecordService;
    @Mock
    private AiDocumentService documentService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TwoHaoHrOpenApiClient twoHaoHrOpenApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KnowledgeSyncServiceImpl knowledgeSyncService;

    @BeforeEach
    void setUp() {
        AiTenantContextHolder.setTenantId(1L);
        AiProperties aiProperties = new AiProperties();
        aiProperties.getDocument().setMaxFileSizeMb(10);
        knowledgeSyncService = new KnowledgeSyncServiceImpl(syncJobMapper, syncRecordMapper, documentMapper,
                knowledgeService, dataSourceService, rawRecordService, attendanceRecordService, documentService, fileStorageService,
                twoHaoHrOpenApiClient, objectMapper, aiProperties);
    }

    @AfterEach
    void tearDown() {
        AiTenantContextHolder.clear();
    }

    @Test
    void executeSyncJobShouldCreateDocumentForNewFile() throws Exception {
        Path file = writeFile("new-doc.txt", "hello file sync");
        mockJobAndDataSource(fileConfig(file), "FILE");
        when(documentMapper.selectBySourceUri(1L, 10L, 20L, sourceUri(file))).thenReturn(null);
        when(fileStorageService.store(anyString(), any(byte[].class)))
                .thenReturn(new FileStorageResult("stored-key", "storage://stored-key"));
        doAnswer(invocation -> {
            AiDocumentDO document = invocation.getArgument(0);
            document.setId(100L);
            return 1;
        }).when(documentMapper).insert(any(AiDocumentDO.class));

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiDocumentDO> documentCaptor = ArgumentCaptor.forClass(AiDocumentDO.class);
        verify(documentMapper).insert(documentCaptor.capture());
        AiDocumentDO document = documentCaptor.getValue();
        assertEquals(1L, document.getTenantId());
        assertEquals(10L, document.getKnowledgeBaseId());
        assertEquals(20L, document.getDataSourceId());
        assertEquals("new-doc", document.getTitle());
        assertEquals("txt", document.getFileType());
        assertEquals(DocumentParseStatusEnum.PENDING.getCode(), document.getParseStatus());
        assertEquals(DocumentEmbeddingStatusEnum.PENDING.getCode(), document.getEmbeddingStatus());

        AiSyncRecordDO record = captureOnlyRecord();
        assertEquals(100L, record.getDocumentId());
        assertEquals(SyncRecordActionTypeEnum.CREATE.getCode(), record.getActionType());
        assertEquals(SyncRecordStatusEnum.SUCCESS.getCode(), record.getStatus());
        verify(documentService).parseDocument(100L);
        verify(documentService).embedDocument(100L);
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(1), eq(1), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldSkipWhenHashNotChanged() throws Exception {
        Path file = writeFile("same.md", "# same content");
        String sourceUri = sourceUri(file);
        String contentHash = sha256Hex(Files.readAllBytes(file));
        mockJobAndDataSource(fileConfig(file), "FILE");
        when(documentMapper.selectBySourceUri(1L, 10L, 20L, sourceUri)).thenReturn(AiDocumentDO.builder()
                .id(101L).contentHash(contentHash).build());

        knowledgeSyncService.executeSyncJob(3001L);

        verify(fileStorageService, never()).store(anyString(), any(byte[].class));
        verify(documentMapper, never()).insert(any(AiDocumentDO.class));
        verify(documentService, never()).parseDocument(101L);
        verify(documentService, never()).embedDocument(101L);
        AiSyncRecordDO record = captureOnlyRecord();
        assertEquals(101L, record.getDocumentId());
        assertEquals(SyncRecordActionTypeEnum.SKIP.getCode(), record.getActionType());
        assertEquals(SyncRecordStatusEnum.SUCCESS.getCode(), record.getStatus());
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(1), eq(1), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldUpdateDocumentWhenHashChanged() throws Exception {
        Path file = writeFile("changed.txt", "new content");
        mockJobAndDataSource(fileConfig(file), "FILE");
        when(documentMapper.selectBySourceUri(1L, 10L, 20L, sourceUri(file))).thenReturn(AiDocumentDO.builder()
                .id(102L).dataSourceId(20L).contentHash("old-hash").build());
        when(fileStorageService.store(anyString(), any(byte[].class)))
                .thenReturn(new FileStorageResult("updated-key", "storage://updated-key"));

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiDocumentDO> documentCaptor = ArgumentCaptor.forClass(AiDocumentDO.class);
        verify(documentMapper).updateSyncDocumentByIdAndTenantId(documentCaptor.capture(), eq(1L));
        AiDocumentDO updateObj = documentCaptor.getValue();
        assertEquals(102L, updateObj.getId());
        assertEquals("changed", updateObj.getTitle());
        assertEquals("updated-key", updateObj.getObjectKey());
        assertEquals(DocumentParseStatusEnum.PENDING.getCode(), updateObj.getParseStatus());
        assertEquals(DocumentEmbeddingStatusEnum.PENDING.getCode(), updateObj.getEmbeddingStatus());

        AiSyncRecordDO record = captureOnlyRecord();
        assertEquals(102L, record.getDocumentId());
        assertEquals(SyncRecordActionTypeEnum.UPDATE.getCode(), record.getActionType());
        assertEquals(SyncRecordStatusEnum.SUCCESS.getCode(), record.getStatus());
        verify(documentService).parseDocument(102L);
        verify(documentService).embedDocument(102L);
    }

    @Test
    void executeSyncJobShouldRecordFailureAndContinueWhenParseFailed() throws Exception {
        Path failedFile = writeFile("failed.txt", "parse failed");
        Path successFile = writeFile("success.txt", "parse success");
        mockJobAndDataSource(filesConfig(List.of(failedFile, successFile)), "FILE");
        when(documentMapper.selectBySourceUri(1L, 10L, 20L, sourceUri(failedFile))).thenReturn(null);
        when(documentMapper.selectBySourceUri(1L, 10L, 20L, sourceUri(successFile))).thenReturn(null);
        when(fileStorageService.store(anyString(), any(byte[].class)))
                .thenReturn(new FileStorageResult("failed-key", "storage://failed-key"))
                .thenReturn(new FileStorageResult("success-key", "storage://success-key"));
        doAnswer(invocation -> {
            AiDocumentDO document = invocation.getArgument(0);
            document.setId(document.getFileName().startsWith("failed") ? 201L : 202L);
            return 1;
        }).when(documentMapper).insert(any(AiDocumentDO.class));
        doThrow(new ServiceException(9001, "解析失败")).when(documentService).parseDocument(201L);

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiSyncRecordDO> recordCaptor = ArgumentCaptor.forClass(AiSyncRecordDO.class);
        verify(syncRecordMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
        List<AiSyncRecordDO> records = recordCaptor.getAllValues();
        assertEquals(201L, records.get(0).getDocumentId());
        assertEquals(SyncRecordStatusEnum.FAILED.getCode(), records.get(0).getStatus());
        assertEquals("解析失败", records.get(0).getErrorMessage());
        assertEquals(202L, records.get(1).getDocumentId());
        assertEquals(SyncRecordStatusEnum.SUCCESS.getCode(), records.get(1).getStatus());
        verify(documentService).parseDocument(201L);
        verify(documentService, never()).embedDocument(201L);
        verify(documentService).parseDocument(202L);
        verify(documentService).embedDocument(202L);
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(2), eq(1), eq(1),
                eq(SyncJobStatusEnum.FAILED.getCode()), any(), eq("部分数据同步失败"));
    }

    @Test
    void executeSyncJobShouldSyncTwoHaoHrApiDataSource() throws Exception {
        String configJson = objectMapper.writeValueAsString(Map.of(
                "provider", "two-hao-hr",
                "syncObjects", List.of("departments", "employees"),
                "maxPages", 1
        ));
        mockJobAndDataSource(configJson, "API");
        JsonNode departments = objectMapper.readTree("""
                [{"id":"d1","name":"信息化部","serial_no":"D001","leader_name":"张三",
                  "children":[{"id":"d2","name":"网络组","parentid":"d1"}]}]
                """);
        JsonNode employee = objectMapper.readTree("""
                {"id":"e1","name":"李四","emp_no":"1001","department_id":"d2",
                 "mobile":"13800000000","credentials_no":"370000000000000000"}
                """);
        when(twoHaoHrOpenApiClient.fetchDepartments(any())).thenReturn(departments);
        when(twoHaoHrOpenApiClient.fetchEmployees(any())).thenReturn(List.of(employee));
        when(documentService.createDocumentFromDataSource(any(AiDataSourceDO.class), any(AiDataSourceIngestReqVO.class)))
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(301L).action("CREATE").build())
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(302L).action("UPDATE").build());

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiDataSourceIngestReqVO> ingestCaptor = ArgumentCaptor.forClass(AiDataSourceIngestReqVO.class);
        verify(documentService, times(2)).createDocumentFromDataSource(any(AiDataSourceDO.class), ingestCaptor.capture());
        List<AiDataSourceIngestReqVO> ingests = ingestCaptor.getAllValues();
        assertEquals("2号人事部组织架构", ingests.get(0).getTitle());
        assertEquals("2号人事部员工基础信息", ingests.get(1).getTitle());
        org.junit.jupiter.api.Assertions.assertFalse(ingests.get(1).getContent().contains("13800000000"));
        org.junit.jupiter.api.Assertions.assertFalse(ingests.get(1).getContent().contains("370000000000000000"));
        verify(rawRecordService, times(2)).saveRecords(any(AiSyncJobDO.class), any(AiDataSourceDO.class),
                eq("two-hao-hr"), anyString(), anyString(), anyString(), any());
        verify(syncRecordMapper, times(2)).insert(any(AiSyncRecordDO.class));
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(2), eq(2), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldPersistTwoHaoHrGenericRawRecords() throws Exception {
        String configJson = objectMapper.writeValueAsString(Map.of(
                "provider", "two-hao-hr",
                "syncObjects", List.of("company_info"),
                "maxPages", 1
        ));
        mockJobAndDataSource(configJson, "API");
        JsonNode companyInfo = objectMapper.readTree("""
                {"id":"c1","company_name":"理文科技","tax_no":"91370100742406421F"}
                """);
        when(twoHaoHrOpenApiClient.fetchRawDataByGet(any(), eq("/api/company/info/"), any()))
                .thenReturn(companyInfo);
        when(rawRecordService.toMaskedJson(any())).thenReturn("{\"company_name\":\"理文科技\"}");
        when(documentService.createDocumentFromDataSource(any(AiDataSourceDO.class), any(AiDataSourceIngestReqVO.class)))
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(501L).action("CREATE").build());

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiDataSourceIngestReqVO> ingestCaptor = ArgumentCaptor.forClass(AiDataSourceIngestReqVO.class);
        verify(documentService).createDocumentFromDataSource(any(AiDataSourceDO.class), ingestCaptor.capture());
        assertEquals("2号人事部企业信息", ingestCaptor.getValue().getTitle());
        org.junit.jupiter.api.Assertions.assertTrue(ingestCaptor.getValue().getContent().contains("company_name"));
        verify(rawRecordService).saveRecords(any(AiSyncJobDO.class), any(AiDataSourceDO.class),
                eq("two-hao-hr"), eq("basic"), eq("company_info"), eq("twohaohr://company_info"), any());
        verify(syncRecordMapper).insert(any(AiSyncRecordDO.class));
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(1), eq(1), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldSyncTwoHaoHrAttendanceByEmployeeBatches() throws Exception {
        String configJson = objectMapper.writeValueAsString(Map.of(
                "provider", "two-hao-hr",
                "syncObjects", List.of("attendance_overtime_record"),
                "attendanceStartDate", "2026-05-01",
                "attendanceEndDate", "2026-05-25",
                "maxPages", 1
        ));
        mockJobAndDataSource(configJson, "API");
        List<JsonNode> employees = new java.util.ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            employees.add(objectMapper.readTree("{\"id\":\"e" + i + "\"}"));
        }
        JsonNode overtime = objectMapper.readTree("{\"id\":\"ot1\",\"emp_id\":\"e1\"}");
        when(twoHaoHrOpenApiClient.fetchEmployees(any())).thenReturn(employees);
        when(twoHaoHrOpenApiClient.fetchPagedObjectsByPost(any(), eq("/api/attendance/ot_record/"), any()))
                .thenReturn(List.of(overtime))
                .thenReturn(List.of());
        when(rawRecordService.toMaskedJson(any())).thenReturn("{\"id\":\"ot1\"}");
        when(documentService.createDocumentFromDataSource(any(AiDataSourceDO.class), any(AiDataSourceIngestReqVO.class)))
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(601L).action("CREATE").build());

        knowledgeSyncService.executeSyncJob(3001L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(twoHaoHrOpenApiClient, times(2))
                .fetchPagedObjectsByPost(any(), eq("/api/attendance/ot_record/"), payloadCaptor.capture());
        List<Map<String, Object>> payloads = payloadCaptor.getAllValues();
        assertEquals(50, ((List<?>) payloads.get(0).get("emp_ids")).size());
        assertEquals(1, ((List<?>) payloads.get(1).get("emp_ids")).size());
        assertEquals("2026-05-01", payloads.get(0).get("start_dt"));
        assertEquals("2026-05-25", payloads.get(0).get("end_dt"));
        assertEquals(50, payloads.get(0).get("limit"));
        verify(rawRecordService).saveRecords(any(AiSyncJobDO.class), any(AiDataSourceDO.class),
                eq("two-hao-hr"), eq("attendance"), eq("attendance_overtime_record"),
                eq("twohaohr://attendance_overtime_record"), any());
        verify(attendanceRecordService).saveRecords(any(AiSyncJobDO.class), any(AiDataSourceDO.class),
                eq("attendance_overtime_record"), any());
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(1), eq(1), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldSyncTwoHaoHrAttendanceAndApprovals() throws Exception {
        String configJson = objectMapper.writeValueAsString(Map.of(
                "provider", "two-hao-hr",
                "syncObjects", List.of("attendance", "approvals"),
                "attendanceQueryDate", "2026-05-25",
                "approvalAddStartDate", "2026-05-01",
                "approvalAddEndDate", "2026-05-25",
                "maxPages", 1
        ));
        mockJobAndDataSource(configJson, "API");
        JsonNode attendance = objectMapper.readTree("""
                {"department_data":{"department_id":"d1","department_name":"信息化部"},
                 "attendance_data":{"all":10,"normal":8,"abnormal":2,"late":1,"early":0,
                 "absent":1,"absenteeism":0,"leave":1,"overtime":2,"outing_work":1,
                 "business_trip":0,"not_scheduler":0}}
                """);
        JsonNode template = objectMapper.readTree("""
                {"id":"t1","title":"请假审批","type":9,"is_enable":true,"desc":"员工请假"}
                """);
        JsonNode approval = objectMapper.readTree("""
                {"id":"a1","no":"SP001","title":"李四的请假审批","type_name":"请假",
                 "status":2,"target_name":"李四","dep_name":"信息化部","emp_name":"李四",
                 "approver":"张三","add_dt":"2026-05-20","update_dt":"2026-05-21",
                 "abstract":[{"title":"请假类型","value":"事假"}]}
                """);
        when(twoHaoHrOpenApiClient.fetchAttendanceDailyOverview(any())).thenReturn(attendance);
        when(twoHaoHrOpenApiClient.fetchAttendanceMonthlyOverview(any())).thenReturn(attendance);
        when(twoHaoHrOpenApiClient.fetchApprovalTemplates(any())).thenReturn(List.of(template));
        when(twoHaoHrOpenApiClient.fetchApprovalRecords(any())).thenReturn(List.of(approval));
        when(documentService.createDocumentFromDataSource(any(AiDataSourceDO.class), any(AiDataSourceIngestReqVO.class)))
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(401L).action("CREATE").build())
                .thenReturn(AiDataSourceIngestRespVO.builder().documentId(402L).action("CREATE").build());

        knowledgeSyncService.executeSyncJob(3001L);

        ArgumentCaptor<AiDataSourceIngestReqVO> ingestCaptor = ArgumentCaptor.forClass(AiDataSourceIngestReqVO.class);
        verify(documentService, times(2)).createDocumentFromDataSource(any(AiDataSourceDO.class), ingestCaptor.capture());
        List<AiDataSourceIngestReqVO> ingests = ingestCaptor.getAllValues();
        assertEquals("2号人事部考勤概况", ingests.get(0).getTitle());
        assertEquals("2号人事部审批记录", ingests.get(1).getTitle());
        org.junit.jupiter.api.Assertions.assertTrue(ingests.get(0).getContent().contains("每日考勤概况"));
        org.junit.jupiter.api.Assertions.assertTrue(ingests.get(1).getContent().contains("审批通过"));
        verify(twoHaoHrOpenApiClient, never()).fetchEmployees(any());
        verify(rawRecordService, times(4)).saveRecords(any(AiSyncJobDO.class), any(AiDataSourceDO.class),
                eq("two-hao-hr"), anyString(), anyString(), anyString(), any());
        verify(syncRecordMapper, times(2)).insert(any(AiSyncRecordDO.class));
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(2), eq(2), eq(0),
                eq(SyncJobStatusEnum.SUCCESS.getCode()), any(), eq(null));
    }

    @Test
    void executeSyncJobShouldRejectNonFileDataSourceAndMarkJobFailed() throws Exception {
        mockJobAndDataSource("{\"provider\":\"unknown\"}", "DATABASE");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> knowledgeSyncService.executeSyncJob(3001L));

        assertEquals(SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED, exception.getCode());
        verify(syncJobMapper).updateResultByIdAndTenantId(eq(3001L), eq(1L), eq(0), eq(0), eq(0),
                eq(SyncJobStatusEnum.FAILED.getCode()), any(), eq("数据源类型暂不支持同步"));
    }

    private void mockJobAndDataSource(String configJson, String sourceType) {
        when(syncJobMapper.selectByIdAndTenantId(3001L, 1L)).thenReturn(AiSyncJobDO.builder()
                .id(3001L)
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .dataSourceId(20L)
                .status(SyncJobStatusEnum.PENDING.getCode())
                .build());
        when(knowledgeService.getKnowledge(10L)).thenReturn(AiKnowledgeBaseDO.builder().id(10L).tenantId(1L).build());
        when(dataSourceService.getDataSource(20L)).thenReturn(AiDataSourceDO.builder()
                .id(20L)
                .knowledgeBaseId(10L)
                .type(sourceType)
                .configJson(configJson)
                .build());
    }

    private AiSyncRecordDO captureOnlyRecord() {
        ArgumentCaptor<AiSyncRecordDO> recordCaptor = ArgumentCaptor.forClass(AiSyncRecordDO.class);
        verify(syncRecordMapper).insert(recordCaptor.capture());
        return recordCaptor.getValue();
    }

    private Path writeFile(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String fileConfig(Path file) throws JsonProcessingException {
        return filesConfig(List.of(file));
    }

    private String filesConfig(List<Path> files) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("files", files.stream().map(Path::toString).toList()));
    }

    private String sourceUri(Path file) {
        return file.toAbsolutePath().normalize().toUri().toString();
    }

    private String sha256Hex(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

}
