package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceRawRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceRawRecordMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoHaoHrLeaveEmployeeListServiceImpl implements TwoHaoHrLeaveEmployeeListService {

    private final AiDataSourceRawRecordMapper rawRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public LeaveEmployeeListResult listEmployees(Long tenantId, AiDataSourceDO dataSource, String objectType,
                                                 LocalDate startDate, LocalDate endDate) {
        if (tenantId == null || dataSource == null || dataSource.getId() == null || !hasText(objectType)) {
            return new LeaveEmployeeListResult(objectType, startDate, endDate, 0L, List.of());
        }
        List<AiDataSourceRawRecordDO> rawRecords = rawRecordMapper.selectList(Wrappers.lambdaQuery(AiDataSourceRawRecordDO.class)
                .eq(AiDataSourceRawRecordDO::getTenantId, tenantId)
                .eq(AiDataSourceRawRecordDO::getKnowledgeBaseId, dataSource.getKnowledgeBaseId())
                .eq(AiDataSourceRawRecordDO::getDataSourceId, dataSource.getId())
                .eq(AiDataSourceRawRecordDO::getProvider, TwoHaoHrDataSourceConfig.PROVIDER)
                .eq(AiDataSourceRawRecordDO::getObjectType, objectType)
                .orderByAsc(AiDataSourceRawRecordDO::getId));
        rawRecords = rawRecords == null ? List.of() : rawRecords;
        List<LeaveEmployee> employees = rawRecords.stream()
                .map(this::toLeaveEmployee)
                .filter(Objects::nonNull)
                .filter(employee -> inDateRange(employee.leaveDate(), startDate, endDate))
                .sorted(Comparator.comparing(LeaveEmployee::leaveDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LeaveEmployee::employeeNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LeaveEmployee::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new LeaveEmployeeListResult(objectType, startDate, endDate, rawRecords.size(), employees);
    }

    private LeaveEmployee toLeaveEmployee(AiDataSourceRawRecordDO rawRecord) {
        if (rawRecord == null || rawRecord.getPayloadJson() == null || rawRecord.getPayloadJson().isBlank()) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(rawRecord.getPayloadJson());
            return new LeaveEmployee(
                    text(payload, "id", "employee_id", "emp_id", "staff_id", "user_id"),
                    text(payload, "emp_no", "employee_no", "job_number", "work_no", "oa_code"),
                    text(payload, "name", "employee_name", "emp_name", "staff_name"),
                    parseDate(text(payload, "leave_date", "leaving_date", "resign_date", "dimission_date")),
                    text(payload, "leave_type_name", "leave_type", "leaving_type_name"),
                    text(payload, "leave_reason", "reason", "leave_reason_name"),
                    text(payload, "department_id", "dept_id", "department_code")
            );
        } catch (Exception ex) {
            log.warn("Skip invalid 2hao HR leave employee raw record, tenantId={}, dataSourceId={}, rawRecordId={}, errorType={}",
                    rawRecord.getTenantId(), rawRecord.getDataSourceId(), rawRecord.getId(),
                    ex.getClass().getSimpleName());
            return null;
        }
    }

    private boolean inDateRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        return (startDate == null || !date.isBefore(startDate))
                && (endDate == null || !date.isAfter(endDate));
    }

    private LocalDate parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return LocalDate.parse(text);
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                String text = value.asText("");
                if (hasText(text)) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
