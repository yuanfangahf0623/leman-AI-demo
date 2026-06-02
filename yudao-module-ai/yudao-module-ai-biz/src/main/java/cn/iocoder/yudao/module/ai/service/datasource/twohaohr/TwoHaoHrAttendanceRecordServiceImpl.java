package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiTwoHaoHrAttendanceRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiTwoHaoHrAttendanceRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * 2hao HR attendance detail record service implementation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwoHaoHrAttendanceRecordServiceImpl implements TwoHaoHrAttendanceRecordService {

    private static final int DEFAULT_STATUS = 0;
    private static final int EXTERNAL_ID_MAX_LENGTH = 255;

    private final AiTwoHaoHrAttendanceRecordMapper attendanceRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecords(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String recordType,
                            List<JsonNode> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (JsonNode record : records) {
            saveOne(syncJob, dataSource, recordType, record);
        }
        log.info("2hao HR attendance records saved, tenantId={}, knowledgeBaseId={}, dataSourceId={}, recordType={}, count={}",
                syncJob.getTenantId(), syncJob.getKnowledgeBaseId(), dataSource.getId(), recordType, records.size());
    }

    private void saveOne(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String recordType, JsonNode payload) {
        String payloadJson = toJson(payload);
        String payloadHash = sha256Hex(payloadJson);
        String externalId = resolveExternalId(payload, recordType, payloadHash);
        AiTwoHaoHrAttendanceRecordDO oldRecord = attendanceRecordMapper.selectByUniqueKey(syncJob.getTenantId(),
                dataSource.getId(), recordType, externalId);
        AiTwoHaoHrAttendanceRecordDO record = AiTwoHaoHrAttendanceRecordDO.builder()
                .id(oldRecord == null ? null : oldRecord.getId())
                .tenantId(syncJob.getTenantId())
                .knowledgeBaseId(syncJob.getKnowledgeBaseId())
                .dataSourceId(dataSource.getId())
                .syncJobId(syncJob.getId())
                .provider(TwoHaoHrDataSourceConfig.PROVIDER)
                .recordType(recordType)
                .externalId(externalId)
                .employeeId(text(payload, "emp_id", "employee_id", "staff_id"))
                .employeeOaCode(text(payload, "emp_oa_code", "emp_no", "employee_no", "oa_code", "job_number"))
                .employeeName(text(payload, "emp_name", "employee_name", "name", "staff_name", "target_name"))
                .departmentId(text(payload, "department_id", "dept_id", "dep_id"))
                .departmentName(text(payload, "department_name", "dept_name", "dep_name"))
                .attendanceDate(parseDate(text(payload, "attendance_date", "attend_date", "work_date", "dt",
                        "query_dt", "start_dt", "begin_dt", "card_dt", "add_dt")))
                .startTime(parseDateTime(text(payload, "start_time", "start_dt", "begin_time", "begin_dt",
                        "card_time", "clock_time", "checkin_time", "ot_start_dt")))
                .endTime(parseDateTime(text(payload, "end_time", "end_dt", "finish_time", "finish_dt",
                        "checkout_time", "ot_end_dt")))
                .recordStatus(text(payload, "status_name", "status", "result_name", "result", "type_name", "type"))
                .payloadJson(payloadJson)
                .payloadHash(payloadHash)
                .recordTime(LocalDateTime.now())
                .status(DEFAULT_STATUS)
                .build();
        if (oldRecord == null) {
            attendanceRecordMapper.insert(record);
        } else {
            attendanceRecordMapper.updateById(record);
        }
    }

    private String resolveExternalId(JsonNode payload, String recordType, String payloadHash) {
        String externalId = text(payload, "id", "record_id", "attendance_id", "apply_id", "order_id", "uuid",
                "source_id");
        if (externalId.isBlank()) {
            String employeeKey = text(payload, "emp_id", "employee_id", "emp_oa_code", "emp_no");
            String timeKey = text(payload, "start_time", "start_dt", "begin_time", "card_time", "dt", "add_dt");
            externalId = recordType + ":" + employeeKey + ":" + timeKey + ":" + payloadHash;
        }
        if (externalId.length() <= EXTERNAL_ID_MAX_LENGTH) {
            return externalId;
        }
        return externalId.substring(0, EXTERNAL_ID_MAX_LENGTH);
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

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDateTimeText(value);
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDateTimeText(value);
        if (normalized.length() == 10) {
            normalized = normalized + " 00:00:00";
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }
        return null;
    }

    private String normalizeDateTimeText(String value) {
        return value.trim()
                .replace('T', ' ')
                .replace("/", "-")
                .replaceAll("\\.\\d+$", "")
                .replaceAll("Z$", "")
                .trim();
    }

    private String toJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

}
