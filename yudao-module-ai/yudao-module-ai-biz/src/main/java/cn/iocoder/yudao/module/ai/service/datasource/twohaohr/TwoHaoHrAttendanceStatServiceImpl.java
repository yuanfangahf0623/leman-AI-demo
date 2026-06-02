package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiTwoHaoHrAttendanceRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiTwoHaoHrAttendanceRecordMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED;

/**
 * 2hao HR attendance statistics service.
 */
@Service
@RequiredArgsConstructor
public class TwoHaoHrAttendanceStatServiceImpl implements TwoHaoHrAttendanceStatService {

    private static final String MATCH_ALL = "ALL";
    private static final String MATCH_ID = "DEPARTMENT_ID";
    private static final String MATCH_NAME_CONTAINS = "DEPARTMENT_NAME_CONTAINS";
    private static final String EMPLOYEE_MATCH_ID = "EMPLOYEE_ID";
    private static final String EMPLOYEE_MATCH_NAME_CONTAINS = "EMPLOYEE_NAME_CONTAINS";
    private static final String BIZ_DATE_EXPR = "COALESCE(attendance_date, DATE(start_time), DATE(end_time))";
    private static final Pattern DEPARTMENT_HINT_PATTERN = Pattern.compile(
            "([\\p{L}\\p{N}]{2,40}?(?:事业部|部门|中心|车间|班组|小组|科|课|处|办|部|组))");
    private static final List<String> DEPARTMENT_HINT_PREFIXES = List.of(
            "帮我统计一下", "帮忙统计一下", "统计一下", "查询一下", "查看一下", "汇总一下",
            "帮我统计", "帮忙统计", "统计下", "查询下", "查看下", "汇总下",
            "统计", "查询", "查看", "汇总", "帮我", "帮忙", "请", "一下", "下");

    private static final List<String> DEPARTMENT_HINT_SUFFIXES = List.of(
            "\u5404\u90e8\u95e8", "\u4e0b\u7ea7\u90e8\u95e8", "\u4e0b\u5c5e\u90e8\u95e8", "\u5b50\u90e8\u95e8",
            "\u90e8\u95e8\u660e\u7ec6", "\u90e8\u95e8\u5217\u8868", "\u7684");

    private final AiTwoHaoHrAttendanceRecordMapper attendanceRecordMapper;
    private final AiDataSourceMapper dataSourceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public TwoHaoHrAttendanceStatRespVO getDepartmentStat(TwoHaoHrAttendanceStatReqVO reqVO) {
        if (reqVO == null) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部考勤统计参数不能为空");
        }
        if (reqVO.getStartDate() != null && reqVO.getEndDate() != null
                && reqVO.getStartDate().isAfter(reqVO.getEndDate())) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "开始日期不能晚于结束日期");
        }
        Long tenantId = AiUserContextHolder.getTenantId();
        AiDataSourceDO dataSource = resolveDataSource(tenantId, reqVO);
        DepartmentScope departmentScope = resolveDepartmentScope(tenantId, dataSource.getId(), reqVO);
        EmployeeScope employeeScope = resolveEmployeeScope(reqVO);
        StatScope statScope = new StatScope(tenantId, dataSource, reqVO.getStartDate(), reqVO.getEndDate(),
                departmentScope, employeeScope);

        Map<String, Object> totalMap = firstMap(selectTotal(statScope));
        List<TwoHaoHrAttendanceStatRespVO.TypeStat> typeStats = buildTypeStats(selectTypeStats(statScope));
        List<TwoHaoHrAttendanceStatRespVO.DailyStat> dailyStats = buildDailyStats(selectDailyTypeStats(statScope));
        List<TwoHaoHrAttendanceStatRespVO.StatusStat> statusStats = buildStatusStats(selectStatusStats(statScope));
        List<TwoHaoHrAttendanceStatRespVO.DepartmentStat> departmentStats = buildDepartmentStats(
                selectDepartmentStats(statScope));

        return TwoHaoHrAttendanceStatRespVO.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(dataSource.getKnowledgeBaseId())
                .dataSourceId(dataSource.getId())
                .departmentId(departmentScope.departmentId())
                .departmentName(departmentScope.departmentName())
                .departmentKeyword(reqVO.getDepartmentKeyword())
                .departmentMatchType(departmentScope.matchType())
                .matchedDepartmentCount(departmentStats.size())
                .employeeId(employeeScope.employeeId())
                .employeeName(employeeScope.employeeName())
                .employeeKeyword(reqVO.getEmployeeKeyword())
                .employeeMatchType(employeeScope.matchType())
                .startDate(reqVO.getStartDate())
                .endDate(reqVO.getEndDate())
                .minAttendanceDate(toLocalDate(value(totalMap, "min_attendance_date")))
                .maxAttendanceDate(toLocalDate(value(totalMap, "max_attendance_date")))
                .totalRecords(toLong(value(totalMap, "total_records")))
                .employeeCount(toLong(value(totalMap, "employee_count")))
                .typeStats(typeStats)
                .dailyStats(dailyStats)
                .statusStats(statusStats)
                .departmentStats(departmentStats)
                .build();
    }

    @Override
    public AiDataSourceDO findTwoHaoHrDataSource(Long tenantId, List<Long> knowledgeBaseIds) {
        List<AiDataSourceDO> candidates = dataSourceMapper.selectListByTenantIdAndKnowledgeBaseIds(tenantId,
                knowledgeBaseIds);
        return candidates.stream()
                .filter(this::isTwoHaoHrDataSource)
                .findFirst()
                .orElse(null);
    }

    private AiDataSourceDO resolveDataSource(Long tenantId, TwoHaoHrAttendanceStatReqVO reqVO) {
        AiDataSourceDO dataSource;
        if (reqVO.getDataSourceId() != null) {
            dataSource = dataSourceMapper.selectByIdAndTenantId(reqVO.getDataSourceId(), tenantId);
        } else {
            dataSource = findTwoHaoHrDataSource(tenantId, List.of(reqVO.getKnowledgeBaseId()));
        }
        if (dataSource == null) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_NOT_EXISTS, "2号人事部数据源不存在");
        }
        if (reqVO.getKnowledgeBaseId() != null && !Objects.equals(reqVO.getKnowledgeBaseId(),
                dataSource.getKnowledgeBaseId())) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "数据源不属于指定知识库");
        }
        if (!isTwoHaoHrDataSource(dataSource)) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED, "数据源不是 2号人事部 API");
        }
        return dataSource;
    }

    private boolean isTwoHaoHrDataSource(AiDataSourceDO dataSource) {
        if (dataSource == null || dataSource.getConfigJson() == null || dataSource.getConfigJson().isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(dataSource.getConfigJson());
            return TwoHaoHrDataSourceConfig.isSupportedProvider(root.path("provider").asText(null));
        } catch (Exception ignored) {
            return false;
        }
    }

    private DepartmentScope resolveDepartmentScope(Long tenantId, Long dataSourceId,
                                                   TwoHaoHrAttendanceStatReqVO reqVO) {
        if (hasText(reqVO.getDepartmentId())) {
            return new DepartmentScope(reqVO.getDepartmentId().trim(), null, MATCH_ID);
        }
        if (hasText(reqVO.getDepartmentName())) {
            return new DepartmentScope(null, reqVO.getDepartmentName().trim(), MATCH_NAME_CONTAINS);
        }
        DepartmentScope matchedScope = matchDepartmentScope(tenantId, dataSourceId, reqVO.getDepartmentKeyword());
        if (matchedScope != null) {
            return matchedScope;
        }
        return new DepartmentScope(null, null, MATCH_ALL);
    }

    private EmployeeScope resolveEmployeeScope(TwoHaoHrAttendanceStatReqVO reqVO) {
        if (hasText(reqVO.getEmployeeId())) {
            return new EmployeeScope(reqVO.getEmployeeId().trim(), null, EMPLOYEE_MATCH_ID);
        }
        if (hasText(reqVO.getEmployeeName())) {
            return new EmployeeScope(null, reqVO.getEmployeeName().trim(), EMPLOYEE_MATCH_NAME_CONTAINS);
        }
        return new EmployeeScope(null, null, MATCH_ALL);
    }

    private DepartmentScope matchDepartmentScope(Long tenantId, Long dataSourceId, String keyword) {
        if (!hasText(keyword)) {
            return null;
        }
        String normalizedKeyword = normalizeForMatch(keyword);
        if (normalizedKeyword.isEmpty()) {
            return null;
        }
        List<String> departmentHints = extractDepartmentHints(keyword);
        String best = null;
        int bestLength = 0;
        for (Map<String, Object> row : selectDepartmentOptions(tenantId, dataSourceId)) {
            String departmentId = stringValue(value(row, "department_id"));
            if (hasText(departmentId) && matchesDepartmentId(normalizedKeyword, departmentId, departmentHints)) {
                return new DepartmentScope(departmentId, stringValue(value(row, "department_name")), MATCH_ID);
            }
            String departmentName = stringValue(value(row, "department_name"));
            if (!hasText(departmentName)) {
                continue;
            }
            String normalizedName = normalizeForMatch(departmentName);
            if (normalizedKeyword.contains(normalizedName) && normalizedName.length() > bestLength) {
                best = departmentName;
                bestLength = normalizedName.length();
            }
            for (String segment : splitDepartmentName(departmentName)) {
                String normalizedSegment = normalizeForMatch(segment);
                if (normalizedSegment.length() >= 2 && normalizedKeyword.contains(normalizedSegment)
                        && normalizedSegment.length() > bestLength) {
                    best = segment;
                    bestLength = normalizedSegment.length();
                }
            }
            for (String hint : departmentHints) {
                String normalizedHint = normalizeForMatch(hint);
                if (normalizedHint.length() < 2) {
                    continue;
                }
                if (normalizedName.contains(normalizedHint) && normalizedHint.length() > bestLength) {
                    best = hint;
                    bestLength = normalizedHint.length();
                }
                for (String segment : splitDepartmentName(departmentName)) {
                    String normalizedSegment = normalizeForMatch(segment);
                    if (normalizedSegment.contains(normalizedHint) && normalizedHint.length() > bestLength) {
                        best = hint;
                        bestLength = normalizedHint.length();
                    }
                }
            }
        }
        if (hasText(best)) {
            return new DepartmentScope(null, best, MATCH_NAME_CONTAINS);
        }
        return departmentHints.isEmpty() ? null : new DepartmentScope(null, departmentHints.get(0),
                MATCH_NAME_CONTAINS);
    }

    private boolean matchesDepartmentId(String normalizedKeyword, String departmentId, List<String> departmentHints) {
        String normalizedDepartmentId = normalizeForMatch(departmentId);
        if (normalizedKeyword.contains(normalizedDepartmentId)) {
            return true;
        }
        return departmentHints.stream()
                .map(this::normalizeForMatch)
                .anyMatch(hint -> hint.equals(normalizedDepartmentId));
    }

    private List<String> extractDepartmentHints(String keyword) {
        if (!hasText(keyword)) {
            return List.of();
        }
        Set<String> hints = new LinkedHashSet<>();
        Matcher matcher = DEPARTMENT_HINT_PATTERN.matcher(keyword.replaceAll("[，。！？；：,.!?;:（）()【】\\[\\]《》、]", " "));
        while (matcher.find()) {
            String hint = cleanDepartmentHint(matcher.group(1));
            if (hasText(hint)) {
                hints.add(hint);
            }
        }
        return new ArrayList<>(hints);
    }

    private String cleanDepartmentHint(String hint) {
        String result = hint == null ? "" : hint.trim();
        boolean changed;
        do {
            changed = false;
            for (String prefix : DEPARTMENT_HINT_PREFIXES) {
                if (result.startsWith(prefix) && result.length() > prefix.length() + 1) {
                    result = result.substring(prefix.length()).trim();
                    changed = true;
                }
            }
            for (String suffix : DEPARTMENT_HINT_SUFFIXES) {
                if (result.endsWith(suffix) && result.length() > suffix.length() + 1) {
                    result = result.substring(0, result.length() - suffix.length()).trim();
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private List<String> splitDepartmentName(String departmentName) {
        if (!hasText(departmentName)) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : departmentName.split("[/\\\\>｜|\\s-]+")) {
            if (hasText(segment)) {
                segments.add(segment.trim());
            }
        }
        segments.sort(Comparator.comparingInt(String::length).reversed());
        return segments;
    }

    private List<Map<String, Object>> selectDepartmentOptions(Long tenantId, Long dataSourceId) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = new QueryWrapper<>();
        query.select("department_id", "department_name", "COUNT(*) AS record_count")
                .eq("tenant_id", tenantId)
                .eq("data_source_id", dataSourceId)
                .eq("deleted", 0)
                .and(wrapper -> wrapper.isNotNull("department_id").or().isNotNull("department_name"))
                .groupBy("department_id", "department_name");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private List<Map<String, Object>> selectTotal(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = baseQuery(scope);
        query.select("COUNT(*) AS total_records",
                "COUNT(DISTINCT COALESCE(NULLIF(employee_id,''), NULLIF(employee_oa_code,''), NULLIF(employee_name,''))) AS employee_count",
                "MIN(" + BIZ_DATE_EXPR + ") AS min_attendance_date",
                "MAX(" + BIZ_DATE_EXPR + ") AS max_attendance_date");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private List<Map<String, Object>> selectTypeStats(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = baseQuery(scope);
        query.select("record_type", "COUNT(*) AS record_count",
                        "COUNT(DISTINCT COALESCE(NULLIF(employee_id,''), NULLIF(employee_oa_code,''), NULLIF(employee_name,''))) AS employee_count")
                .groupBy("record_type")
                .orderByDesc("record_count");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private List<Map<String, Object>> selectDailyTypeStats(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = baseQuery(scope);
        query.select(BIZ_DATE_EXPR + " AS stat_date", "record_type", "COUNT(*) AS record_count",
                        "COUNT(DISTINCT COALESCE(NULLIF(employee_id,''), NULLIF(employee_oa_code,''), NULLIF(employee_name,''))) AS employee_count")
                .apply(BIZ_DATE_EXPR + " IS NOT NULL")
                .groupBy(BIZ_DATE_EXPR, "record_type")
                .orderByAsc("stat_date");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private List<Map<String, Object>> selectStatusStats(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = baseQuery(scope);
        String statusExpr = "COALESCE(NULLIF(record_status,''),'未知')";
        query.select("record_type", statusExpr + " AS record_status", "COUNT(*) AS record_count")
                .groupBy("record_type", statusExpr)
                .orderByDesc("record_count");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private List<Map<String, Object>> selectDepartmentStats(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = baseQuery(scope);
        query.select("department_id", "department_name", "COUNT(*) AS record_count",
                        "COUNT(DISTINCT COALESCE(NULLIF(employee_id,''), NULLIF(employee_oa_code,''), NULLIF(employee_name,''))) AS employee_count",
                        "SUM(CASE WHEN record_type = 'attendance_card_record' THEN 1 ELSE 0 END) AS card_record_count",
                        "SUM(CASE WHEN record_type = 'attendance_card_result' THEN 1 ELSE 0 END) AS card_result_count",
                        "SUM(CASE WHEN record_type = 'attendance_leave_record' THEN 1 ELSE 0 END) AS leave_count",
                        "SUM(CASE WHEN record_type = 'attendance_overtime_record' THEN 1 ELSE 0 END) AS overtime_count",
                        "SUM(CASE WHEN record_type = 'attendance_outing_record' THEN 1 ELSE 0 END) AS outing_count",
                        "SUM(CASE WHEN record_type = 'attendance_shifts' THEN 1 ELSE 0 END) AS shift_count")
                .groupBy("department_id", "department_name")
                .orderByDesc("record_count");
        return attendanceRecordMapper.selectStatMaps(query);
    }

    private QueryWrapper<AiTwoHaoHrAttendanceRecordDO> baseQuery(StatScope scope) {
        QueryWrapper<AiTwoHaoHrAttendanceRecordDO> query = new QueryWrapper<>();
        query.eq("tenant_id", scope.tenantId())
                .eq("data_source_id", scope.dataSource().getId())
                .eq("deleted", 0);
        if (scope.startDate() != null) {
            query.apply(BIZ_DATE_EXPR + " >= {0}", scope.startDate());
        }
        if (scope.endDate() != null) {
            query.apply(BIZ_DATE_EXPR + " <= {0}", scope.endDate());
        }
        DepartmentScope department = scope.department();
        if (hasText(department.departmentId())) {
            query.eq("department_id", department.departmentId());
        } else if (hasText(department.departmentName())) {
            query.like("department_name", department.departmentName());
        }
        EmployeeScope employee = scope.employee();
        if (employee != null) {
            if (hasText(employee.employeeId())) {
                query.and(wrapper -> wrapper.eq("employee_id", employee.employeeId())
                        .or()
                        .eq("employee_oa_code", employee.employeeId()));
            } else if (hasText(employee.employeeName())) {
                query.like("employee_name", employee.employeeName());
            }
        }
        return query;
    }

    private List<TwoHaoHrAttendanceStatRespVO.TypeStat> buildTypeStats(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    String recordType = stringValue(value(row, "record_type"));
                    return TwoHaoHrAttendanceStatRespVO.TypeStat.builder()
                            .recordType(recordType)
                            .recordTypeName(recordTypeName(recordType))
                            .recordCount(toLong(value(row, "record_count")))
                            .employeeCount(toLong(value(row, "employee_count")))
                            .build();
                })
                .toList();
    }

    private List<TwoHaoHrAttendanceStatRespVO.DailyStat> buildDailyStats(List<Map<String, Object>> rows) {
        Map<LocalDate, TwoHaoHrAttendanceStatRespVO.DailyStat> dailyMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate statDate = toLocalDate(value(row, "stat_date"));
            if (statDate == null) {
                continue;
            }
            TwoHaoHrAttendanceStatRespVO.DailyStat daily = dailyMap.computeIfAbsent(statDate, date ->
                    TwoHaoHrAttendanceStatRespVO.DailyStat.builder()
                            .attendanceDate(date)
                            .totalRecords(0L)
                            .employeeCount(0L)
                            .cardRecordCount(0L)
                            .cardResultCount(0L)
                            .leaveCount(0L)
                            .overtimeCount(0L)
                            .outingCount(0L)
                            .shiftCount(0L)
                            .build());
            long count = toLong(value(row, "record_count"));
            long employeeCount = toLong(value(row, "employee_count"));
            daily.setTotalRecords(daily.getTotalRecords() + count);
            daily.setEmployeeCount(Math.max(daily.getEmployeeCount(), employeeCount));
            applyDailyTypeCount(daily, stringValue(value(row, "record_type")), count);
        }
        return new ArrayList<>(dailyMap.values());
    }

    private void applyDailyTypeCount(TwoHaoHrAttendanceStatRespVO.DailyStat daily, String recordType, long count) {
        switch (recordType) {
            case "attendance_card_record" -> daily.setCardRecordCount(count);
            case "attendance_card_result" -> daily.setCardResultCount(count);
            case "attendance_leave_record" -> daily.setLeaveCount(count);
            case "attendance_overtime_record" -> daily.setOvertimeCount(count);
            case "attendance_outing_record" -> daily.setOutingCount(count);
            case "attendance_shifts" -> daily.setShiftCount(count);
            default -> {
            }
        }
    }

    private List<TwoHaoHrAttendanceStatRespVO.StatusStat> buildStatusStats(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    String recordType = stringValue(value(row, "record_type"));
                    return TwoHaoHrAttendanceStatRespVO.StatusStat.builder()
                            .recordType(recordType)
                            .recordTypeName(recordTypeName(recordType))
                            .recordStatus(stringValue(value(row, "record_status")))
                            .recordCount(toLong(value(row, "record_count")))
                            .build();
                })
                .toList();
    }

    private List<TwoHaoHrAttendanceStatRespVO.DepartmentStat> buildDepartmentStats(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> TwoHaoHrAttendanceStatRespVO.DepartmentStat.builder()
                        .departmentId(stringValue(value(row, "department_id")))
                        .departmentName(stringValue(value(row, "department_name")))
                        .recordCount(toLong(value(row, "record_count")))
                        .employeeCount(toLong(value(row, "employee_count")))
                        .cardRecordCount(toLong(value(row, "card_record_count")))
                        .cardResultCount(toLong(value(row, "card_result_count")))
                        .leaveCount(toLong(value(row, "leave_count")))
                        .overtimeCount(toLong(value(row, "overtime_count")))
                        .outingCount(toLong(value(row, "outing_count")))
                        .shiftCount(toLong(value(row, "shift_count")))
                        .build())
                .toList();
    }

    private Map<String, Object> firstMap(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String camelKey = toCamelCase(key);
        if (row.containsKey(camelKey)) {
            return row.get(camelKey);
        }
        String upperKey = key.toUpperCase(Locale.ROOT);
        if (row.containsKey(upperKey)) {
            return row.get(upperKey);
        }
        return null;
    }

    private String toCamelCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        String text = String.valueOf(value);
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String recordTypeName(String recordType) {
        return switch (recordType == null ? "" : recordType) {
            case "attendance_card_record" -> "打卡记录";
            case "attendance_card_result" -> "打卡结果";
            case "attendance_leave_record" -> "请假记录";
            case "attendance_overtime_record" -> "加班记录";
            case "attendance_outing_record" -> "外勤记录";
            case "attendance_shifts" -> "班次";
            case "attendance_daily_overview" -> "日考勤概况";
            case "attendance_monthly_overview" -> "月考勤概况";
            default -> recordType == null ? "" : recordType;
        };
    }

    private String normalizeForMatch(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record StatScope(Long tenantId, AiDataSourceDO dataSource, LocalDate startDate, LocalDate endDate,
                             DepartmentScope department, EmployeeScope employee) {
    }

    private record DepartmentScope(String departmentId, String departmentName, String matchType) {
    }

    private record EmployeeScope(String employeeId, String employeeName, String matchType) {
    }

}
