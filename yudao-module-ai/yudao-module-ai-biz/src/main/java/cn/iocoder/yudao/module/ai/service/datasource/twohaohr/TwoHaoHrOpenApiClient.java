package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_EXECUTE_FAILED;

/**
 * 2hao HR OpenAPI client.
 */
@Slf4j
@Component
public class TwoHaoHrOpenApiClient {

    private static final String TOKEN_PATH = "/api/home/get_token/";
    private static final String DEPARTMENTS_PATH = "/api/departments/";
    private static final String EMPLOYEE_DETAIL_LIST_PATH = "/api/employees/dept_list/";
    private static final String EMPLOYEE_SIMPLE_LIST_PATH = "/api/employees/dept_simple_list/";
    private static final String ATTENDANCE_DAILY_OVERVIEW_PATH = "/api/attendance/statistics_querier/daily_data/";
    private static final String ATTENDANCE_MONTHLY_OVERVIEW_PATH = "/api/attendance/statistics_querier/monthly_data/";
    private static final String ATTENDANCE_EMPLOYEE_MONTH_OVERVIEW_PATH = "/api/attendance/month_overview/";
    private static final String APPROVAL_TEMPLATE_LIST_PATH = "/api/approvals/template_list/";
    private static final String APPROVAL_SUBMIT_LIST_PATH = "/api/approvals/submit_list/";
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int TOKEN_REFRESH_SKEW_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Function<String, String> envResolver;
    private final Map<String, TokenCache> tokenCaches = new ConcurrentHashMap<>();

    @Autowired
    public TwoHaoHrOpenApiClient(ObjectMapper objectMapper) {
        this(objectMapper, System::getenv, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build());
    }

    TwoHaoHrOpenApiClient(ObjectMapper objectMapper, Function<String, String> envResolver, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.envResolver = envResolver;
        this.httpClient = httpClient;
    }

    public JsonNode fetchDepartments(TwoHaoHrDataSourceConfig config) {
        Map<String, String> params = new LinkedHashMap<>();
        if (hasText(config.getDepartmentId())) {
            params.put("id", config.getDepartmentId().trim());
        }
        JsonNode root = get(config, DEPARTMENTS_PATH, params);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部组织接口响应格式非法");
        }
        return data;
    }

    public List<JsonNode> fetchEmployees(TwoHaoHrDataSourceConfig config) {
        List<JsonNode> employees = new ArrayList<>();
        String path = Boolean.TRUE.equals(config.getEmployeeDetail()) ? EMPLOYEE_DETAIL_LIST_PATH : EMPLOYEE_SIMPLE_LIST_PATH;
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= config.getMaxPages()) {
            Map<String, String> params = new LinkedHashMap<>();
            if (hasText(config.getDepartmentId())) {
                params.put("department_id", config.getDepartmentId().trim());
            }
            params.put("fetch_child", Boolean.TRUE.equals(config.getFetchChild()) ? "1" : "0");
            params.put("get_left_emp", Boolean.TRUE.equals(config.getIncludeLeftEmployee()) ? "1" : "0");
            params.put("limit", String.valueOf(config.getPageSize()));
            params.put("p", String.valueOf(page));
            JsonNode root = get(config, path, params);
            JsonNode data = root.path("data");
            JsonNode objects = data.path("objects");
            if (!objects.isArray()) {
                throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部员工接口响应格式非法");
            }
            objects.forEach(employees::add);
            totalPage = Math.max(1, data.path("totalpage").asInt(1));
            page++;
        }
        return employees;
    }

    public JsonNode fetchAttendanceDailyOverview(TwoHaoHrDataSourceConfig config) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query_dt", config.getAttendanceQueryDate());
        putDepartmentQuery(params, config);
        params.put("type", String.valueOf(config.getAttendanceDailyType()));
        return get(config, ATTENDANCE_DAILY_OVERVIEW_PATH, params).path("data");
    }

    public JsonNode fetchAttendanceMonthlyOverview(TwoHaoHrDataSourceConfig config) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query_dt", config.getAttendanceQueryDate());
        putDepartmentQuery(params, config);
        params.put("type", String.valueOf(config.getAttendanceDailyType()));
        return get(config, ATTENDANCE_MONTHLY_OVERVIEW_PATH, params).path("data");
    }

    public List<JsonNode> fetchEmployeeMonthOverview(TwoHaoHrDataSourceConfig config, List<String> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        List<JsonNode> results = new ArrayList<>();
        for (int from = 0; from < employeeIds.size(); from += 50) {
            int to = Math.min(from + 50, employeeIds.size());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("year", config.getAttendanceYear());
            payload.put("month", config.getAttendanceMonth());
            payload.put("emp_ids", employeeIds.subList(from, to));
            payload.put("emp_oa_codes", List.of());
            JsonNode data = post(config, ATTENDANCE_EMPLOYEE_MONTH_OVERVIEW_PATH, payload).path("data");
            if (data.isArray()) {
                data.forEach(results::add);
            }
        }
        return results;
    }

    public List<JsonNode> fetchApprovalTemplates(TwoHaoHrDataSourceConfig config) {
        List<JsonNode> templates = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= config.getMaxPages()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("p", page);
            payload.put("limit", config.getPageSize());
            JsonNode pageData = unwrapPageData(post(config, APPROVAL_TEMPLATE_LIST_PATH, payload));
            JsonNode objects = pageData.path("objects");
            if (!objects.isArray()) {
                throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部审批模板接口响应格式非法");
            }
            objects.forEach(templates::add);
            totalPage = Math.max(1, pageData.path("totalpage").asInt(1));
            page++;
        }
        return templates;
    }

    public List<JsonNode> fetchApprovalRecords(TwoHaoHrDataSourceConfig config) {
        List<JsonNode> records = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= config.getMaxPages()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("add_dt_min", config.getApprovalAddStartDate());
            payload.put("add_dt_max", config.getApprovalAddEndDate());
            payload.put("p", page);
            payload.put("limit", config.getPageSize());
            if (config.getApprovalStatus() != null) {
                payload.put("status", config.getApprovalStatus());
            }
            if (config.getApprovalTypeList() != null && !config.getApprovalTypeList().isEmpty()) {
                payload.put("type_list", config.getApprovalTypeList());
            }
            JsonNode pageData = unwrapPageData(post(config, APPROVAL_SUBMIT_LIST_PATH, payload));
            JsonNode objects = pageData.path("objects");
            if (!objects.isArray()) {
                throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部审批记录接口响应格式非法");
            }
            objects.forEach(records::add);
            totalPage = Math.max(1, pageData.path("totalpage").asInt(1));
            page++;
        }
        return records;
    }

    public JsonNode fetchRawDataByGet(TwoHaoHrDataSourceConfig config, String path, Map<String, String> params) {
        return get(config, path, params == null ? Map.of() : params).path("data");
    }

    public JsonNode fetchRawDataByPost(TwoHaoHrDataSourceConfig config, String path, Map<String, Object> payload) {
        return post(config, path, payload == null ? Map.of() : payload).path("data");
    }

    public List<JsonNode> fetchPagedObjectsByGet(TwoHaoHrDataSourceConfig config, String path,
                                                 Map<String, String> params) {
        List<JsonNode> records = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= config.getMaxPages()) {
            Map<String, String> pageParams = new LinkedHashMap<>();
            if (params != null) {
                pageParams.putAll(params);
            }
            pageParams.put("p", String.valueOf(page));
            pageParams.putIfAbsent("limit", String.valueOf(config.getPageSize()));
            JsonNode pageData = unwrapPageData(get(config, path, pageParams));
            JsonNode objects = pageData.path("objects");
            if (objects.isArray()) {
                objects.forEach(records::add);
            } else if (pageData.isArray()) {
                pageData.forEach(records::add);
                break;
            } else if (pageData.isObject()) {
                records.add(pageData);
                break;
            }
            totalPage = Math.max(1, pageData.path("totalpage").asInt(1));
            page++;
        }
        return records;
    }

    public List<JsonNode> fetchPagedObjectsByPost(TwoHaoHrDataSourceConfig config, String path,
                                                  Map<String, Object> payload) {
        List<JsonNode> records = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= config.getMaxPages()) {
            Map<String, Object> pagePayload = new LinkedHashMap<>();
            if (payload != null) {
                pagePayload.putAll(payload);
            }
            pagePayload.put("p", page);
            pagePayload.putIfAbsent("limit", config.getPageSize());
            JsonNode pageData = unwrapPageData(post(config, path, pagePayload));
            JsonNode objects = pageData.path("objects");
            if (objects.isArray()) {
                objects.forEach(records::add);
            } else if (pageData.isArray()) {
                pageData.forEach(records::add);
                break;
            } else if (pageData.isObject()) {
                records.add(pageData);
                break;
            }
            totalPage = Math.max(1, pageData.path("totalpage").asInt(1));
            page++;
        }
        return records;
    }

    private JsonNode get(TwoHaoHrDataSourceConfig config, String path, Map<String, String> params) {
        String accessToken = resolveAccessToken(config);
        Map<String, String> queryParams = new LinkedHashMap<>(params);
        queryParams.put("access_token", accessToken);
        URI uri = buildUri(config.getBaseUrl(), path, queryParams);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("access_token", accessToken)
                .GET()
                .build();
        return send(request, uri);
    }

    private JsonNode post(TwoHaoHrDataSourceConfig config, String path, Map<String, Object> payload) {
        String accessToken = resolveAccessToken(config);
        URI uri = buildUri(config.getBaseUrl(), path, Map.of("access_token", accessToken));
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部请求构造失败");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("access_token", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return send(request, uri);
    }

    private void putDepartmentQuery(Map<String, String> params, TwoHaoHrDataSourceConfig config) {
        if (hasText(config.getDepartmentId())) {
            params.put("department_id", config.getDepartmentId().trim());
            params.put("ignore_department", "false");
        } else {
            params.put("ignore_department", "true");
        }
    }

    private JsonNode unwrapPageData(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isObject() && data.has("objects")) {
            return data;
        }
        if (data.isArray() || data.isObject()) {
            return data;
        }
        return root;
    }

    private String resolveAccessToken(TwoHaoHrDataSourceConfig config) {
        String envAccessToken = readEnv(config.getAccessTokenEnv());
        if (hasText(envAccessToken)) {
            return envAccessToken.trim();
        }
        String corpId = requiredEnv(config.getCorpIdEnv(), "corp_id");
        String appId = requiredEnv(config.getAppIdEnv(), "app_id");
        String appSecret = requiredEnv(config.getAppSecretEnv(), "app_secret");
        String cacheKey = config.getBaseUrl() + "|" + corpId + "|" + appId;
        TokenCache cache = tokenCaches.get(cacheKey);
        if (cache != null && cache.expiresAt().isAfter(Instant.now().plusSeconds(TOKEN_REFRESH_SKEW_SECONDS))) {
            return cache.accessToken();
        }
        String accessToken = requestAccessToken(config, corpId, appId, appSecret);
        return accessToken;
    }

    private String requestAccessToken(TwoHaoHrDataSourceConfig config, String corpId, String appId, String appSecret) {
        URI uri = buildUri(config.getBaseUrl(), TOKEN_PATH, Map.of());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("corp_id", corpId);
        payload.put("app_id", appId);
        payload.put("app_secret", appSecret);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部 token 请求构造失败");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        JsonNode root = send(request, uri);
        JsonNode data = root.path("data");
        String accessToken = data.path("access_token").asText(null);
        if (!hasText(accessToken)) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部 token 响应缺少 access_token");
        }
        int expiresIn = Math.max(300, data.path("expires_in").asInt(3600));
        tokenCaches.put(config.getBaseUrl() + "|" + corpId + "|" + appId,
                new TokenCache(accessToken, Instant.now().plusSeconds(expiresIn)));
        return accessToken;
    }

    private JsonNode send(HttpRequest request, URI uri) {
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() > HTTP_SUCCESS_MAX) {
                log.warn("2hao HR API request failed, endpoint={}, status={}, elapsedMs={}",
                        sanitizeEndpoint(uri), response.statusCode(), elapsedMs);
                throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部接口调用失败");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errcodeNode = root.path("errcode");
            if (errcodeNode.isNumber() && errcodeNode.asInt() != 0) {
                log.warn("2hao HR API returned error, endpoint={}, errcode={}, elapsedMs={}",
                        sanitizeEndpoint(uri), errcodeNode.asInt(), elapsedMs);
                throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部接口返回错误：" + errcodeNode.asInt());
            }
            log.info("2hao HR API request success, endpoint={}, elapsedMs={}", sanitizeEndpoint(uri), elapsedMs);
            return root;
        } catch (HttpTimeoutException ex) {
            log.warn("2hao HR API request timeout, endpoint={}", sanitizeEndpoint(uri));
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部接口调用超时");
        } catch (IOException ex) {
            log.warn("2hao HR API request failed, endpoint={}, errorType={}",
                    sanitizeEndpoint(uri), ex.getClass().getSimpleName());
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部接口调用失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("2hao HR API request interrupted, endpoint={}", sanitizeEndpoint(uri));
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "2号人事部接口调用被中断");
        }
    }

    private URI buildUri(String baseUrl, String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (!params.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!hasText(entry.getValue())) {
                    continue;
                }
                if (!first) {
                    url.append('&');
                }
                url.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                first = false;
            }
        }
        try {
            URI uri = URI.create(url.toString());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("missing scheme or host");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部接口地址非法");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requiredEnv(String envName, String fieldName) {
        String value = readEnv(envName);
        if (!hasText(value)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "2号人事部缺少环境变量：" + envName + "（" + fieldName + "）");
        }
        return value.trim();
    }

    private String readEnv(String envName) {
        return hasText(envName) ? envResolver.apply(envName.trim()) : null;
    }

    private String sanitizeEndpoint(URI uri) {
        String raw = uri.toString();
        return raw.replaceAll("access_token=[^&]+", "access_token=***");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record TokenCache(String accessToken, Instant expiresAt) {
    }

}
