package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;
import java.util.Locale;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;

/**
 * 2hao HR API data source configuration.
 *
 * <p>Only environment variable names are stored in data source config. Real credentials must stay in the
 * runtime environment or configuration center.</p>
 */
@Data
public class TwoHaoHrDataSourceConfig {

    public static final String PROVIDER = "two-hao-hr";
    public static final String PROVIDER_ALIAS = "2haohr";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 10;

    private String provider = PROVIDER;
    private String baseUrl = "https://openapi.2haohr.com";
    private String accessTokenEnv = "TWO_HAO_HR_ACCESS_TOKEN";
    private String corpIdEnv = "TWO_HAO_HR_CORP_ID";
    private String appIdEnv = "TWO_HAO_HR_APP_ID";
    private String appSecretEnv = "TWO_HAO_HR_APP_SECRET";
    private String departmentId;
    private Boolean fetchChild = true;
    private Boolean includeLeftEmployee = false;
    private Boolean employeeDetail = true;
    private Boolean includeSensitiveFields = false;
    private String attendanceQueryDate;
    private String attendanceStartDate;
    private String attendanceEndDate;
    private Integer attendanceYear;
    private Integer attendanceMonth;
    private Integer attendanceDailyType = 0;
    private Boolean attendanceIncludeEmployeeMonthly = false;
    private String approvalAddStartDate;
    private String approvalAddEndDate;
    private Integer approvalStatus;
    private List<Integer> approvalTypeList;
    private Boolean approvalIncludeDetails = false;
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    private Integer maxPages = DEFAULT_MAX_PAGES;
    private List<String> syncObjects = List.of("departments", "employees");

    public static TwoHaoHrDataSourceConfig parse(String configJson, ObjectMapper objectMapper) {
        if (configJson == null || configJson.isBlank()) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部 API 数据源配置不能为空");
        }
        try {
            TwoHaoHrDataSourceConfig config = objectMapper.readValue(configJson, TwoHaoHrDataSourceConfig.class);
            config.normalize();
            return config;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部 API 数据源配置 JSON 非法");
        }
    }

    public static boolean isSupportedProvider(String provider) {
        if (provider == null) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return PROVIDER.equals(normalized) || PROVIDER_ALIAS.equals(normalized);
    }

    public boolean shouldSync(String objectName) {
        if (syncObjects == null || syncObjects.isEmpty()) {
            return true;
        }
        String normalizedObjectName = objectName == null ? "" : objectName.trim().toLowerCase(Locale.ROOT);
        for (String syncObject : syncObjects) {
            String normalizedSyncObject = syncObject == null ? "" : syncObject.trim().toLowerCase(Locale.ROOT);
            if ("all".equals(normalizedSyncObject) || normalizedObjectName.equals(normalizedSyncObject)) {
                return true;
            }
        }
        return false;
    }

    private void normalize() {
        if (!isSupportedProvider(provider)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "API 数据源 provider 不是 2号人事部");
        }
        provider = PROVIDER;
        baseUrl = normalizeBaseUrl(baseUrl);
        accessTokenEnv = normalizeEnvName(accessTokenEnv, "TWO_HAO_HR_ACCESS_TOKEN");
        corpIdEnv = normalizeEnvName(corpIdEnv, "TWO_HAO_HR_CORP_ID");
        appIdEnv = normalizeEnvName(appIdEnv, "TWO_HAO_HR_APP_ID");
        appSecretEnv = normalizeEnvName(appSecretEnv, "TWO_HAO_HR_APP_SECRET");
        pageSize = clamp(pageSize, 1, 100, DEFAULT_PAGE_SIZE);
        maxPages = clamp(maxPages, 1, 100, DEFAULT_MAX_PAGES);
        fetchChild = fetchChild == null || fetchChild;
        includeLeftEmployee = includeLeftEmployee != null && includeLeftEmployee;
        employeeDetail = employeeDetail == null || employeeDetail;
        includeSensitiveFields = includeSensitiveFields != null && includeSensitiveFields;
        LocalDate today = LocalDate.now();
        attendanceQueryDate = normalizeDate(attendanceQueryDate, today.toString());
        attendanceEndDate = normalizeDate(attendanceEndDate, today.toString());
        attendanceStartDate = normalizeDate(attendanceStartDate, today.withDayOfMonth(1).toString());
        attendanceYear = attendanceYear == null ? today.getYear() : attendanceYear;
        attendanceMonth = clamp(attendanceMonth, 1, 12, today.getMonthValue());
        attendanceDailyType = clamp(attendanceDailyType, 0, 1, 0);
        attendanceIncludeEmployeeMonthly = includeSensitiveFields
                && attendanceIncludeEmployeeMonthly != null && attendanceIncludeEmployeeMonthly;
        approvalAddEndDate = normalizeDate(approvalAddEndDate, today.toString());
        approvalAddStartDate = normalizeDate(approvalAddStartDate, today.minusDays(30).toString());
        approvalIncludeDetails = includeSensitiveFields
                && approvalIncludeDetails != null && approvalIncludeDetails;
        if (syncObjects == null || syncObjects.isEmpty()) {
            syncObjects = List.of("departments", "employees");
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "https://openapi.2haohr.com" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeEnvName(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String normalizeDate(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Integer clamp(Integer value, int min, int max, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

}
