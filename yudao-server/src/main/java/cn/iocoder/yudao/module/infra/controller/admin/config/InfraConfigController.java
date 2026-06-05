package cn.iocoder.yudao.module.infra.controller.admin.config;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.server.framework.crud.SimpleAdminDataService;
import cn.iocoder.yudao.server.framework.crud.SimpleAdminDataService.TableDef;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Admin system config APIs used by the local yudao-ui integration.
 */
@RestController
@RequestMapping("/admin-api/infra/config")
@RequiredArgsConstructor
public class InfraConfigController {

    private static final String RAG_ENGINE_KEY = "ai.rag.engine";
    private static final String RAG_ENGINE_FASTGPT = "fastgpt";
    private static final String RAG_ENGINE_LOCAL = "local";
    private static final Set<String> SUPPORTED_RAG_ENGINES = Set.of(RAG_ENGINE_FASTGPT, RAG_ENGINE_LOCAL);
    private static final String AI_DOCUMENT_STORAGE_TYPE_KEY = "AI_DOCUMENT_STORAGE_TYPE";
    private static final String AI_DOCUMENT_STORAGE_TYPE_MINIO = "minio";
    private static final String AI_DOCUMENT_STORAGE_TYPE_LOCAL = "local";
    private static final Set<String> SUPPORTED_AI_DOCUMENT_STORAGE_TYPES = Set.of(
            AI_DOCUMENT_STORAGE_TYPE_MINIO, AI_DOCUMENT_STORAGE_TYPE_LOCAL);
    private static final TableDef CONFIG = def("infra_config",
            cols("id", "category", "name", "key", "value", "type", "visible", "remark"),
            cols("category", "name", "key", "type", "visible"), "id DESC");

    private final SimpleAdminDataService dataService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('infra:config:query')")
    public CommonResult<PageResult<Map<String, Object>>> getConfigPage(@RequestParam Map<String, Object> params) {
        return CommonResult.success(dataService.page(CONFIG, params));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('infra:config:query')")
    public CommonResult<Map<String, Object>> getConfig(@RequestParam("id") Long id) {
        return CommonResult.success(dataService.get(CONFIG, id));
    }

    @GetMapping("/get-value-by-key")
    @PreAuthorize("@ss.hasPermission('infra:config:query')")
    public CommonResult<String> getConfigValueByKey(@RequestParam("key") String key) {
        if (!StringUtils.hasText(key)) {
            throw new ServiceException(400, "config key is required");
        }
        try {
            String value = jdbcTemplate.queryForObject("""
                    SELECT `value`
                    FROM `infra_config`
                    WHERE `key` = ? AND `deleted` = 0
                    LIMIT 1
                    """, String.class, key);
            return CommonResult.success(value);
        } catch (EmptyResultDataAccessException ex) {
            return CommonResult.success(null);
        }
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('infra:config:create')")
    public CommonResult<Long> createConfig(@RequestBody Map<String, Object> reqVO) {
        Map<String, Object> data = normalizeForWrite(reqVO);
        validateConfig(data);
        try {
            return CommonResult.success(dataService.create(CONFIG, data));
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(400, "config key already exists");
        }
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('infra:config:update')")
    public CommonResult<Boolean> updateConfig(@RequestBody Map<String, Object> reqVO) {
        Map<String, Object> data = normalizeForWrite(reqVO);
        validateConfig(data);
        try {
            dataService.update(CONFIG, data);
            return CommonResult.success(true);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(400, "config key already exists");
        }
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('infra:config:delete')")
    public CommonResult<Boolean> deleteConfig(@RequestParam("id") Long id) {
        dataService.delete(CONFIG, id);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('infra:config:delete')")
    public CommonResult<Boolean> deleteConfigList(@RequestParam("ids") String ids) {
        dataService.deleteList(CONFIG, ids);
        return CommonResult.success(true);
    }

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('infra:config:export')")
    public ResponseEntity<byte[]> exportConfig(@RequestParam Map<String, Object> params) {
        return excel("infra-config.xlsx", dataService.exportExcel(CONFIG, params, columns(
                "id", "ID",
                "category", "Category",
                "name", "Name",
                "key", "Key",
                "value", "Value",
                "type", "Type",
                "visible", "Visible",
                "remark", "Remark",
                "createTime", "Create Time"), "Config"));
    }

    private Map<String, Object> normalizeForWrite(Map<String, Object> reqVO) {
        Map<String, Object> data = reqVO == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reqVO);
        data.putIfAbsent("type", 0);
        data.putIfAbsent("visible", true);
        Object key = data.get("key");
        if (RAG_ENGINE_KEY.equals(key) && data.containsKey("value")) {
            data.put("value", normalizeRagEngineValue(data.get("value")));
        }
        if (AI_DOCUMENT_STORAGE_TYPE_KEY.equals(key) && data.containsKey("value")) {
            data.put("value", normalizeAiDocumentStorageTypeValue(data.get("value")));
        }
        return data;
    }

    private void validateConfig(Map<String, Object> data) {
        if (!StringUtils.hasText(stringValue(data.get("category")))
                || !StringUtils.hasText(stringValue(data.get("name")))
                || !StringUtils.hasText(stringValue(data.get("key")))
                || !StringUtils.hasText(stringValue(data.get("value")))) {
            throw new ServiceException(400, "category, name, key and value are required");
        }
        if (RAG_ENGINE_KEY.equals(data.get("key"))) {
            String engine = normalizeRagEngineValue(data.get("value"));
            if (!SUPPORTED_RAG_ENGINES.contains(engine)) {
                throw new ServiceException(400, "ai.rag.engine only supports fastgpt or local");
            }
            data.put("value", engine);
        }
        if (AI_DOCUMENT_STORAGE_TYPE_KEY.equals(data.get("key"))) {
            String storageType = normalizeAiDocumentStorageTypeValue(data.get("value"));
            if (!SUPPORTED_AI_DOCUMENT_STORAGE_TYPES.contains(storageType)) {
                throw new ServiceException(400, "AI_DOCUMENT_STORAGE_TYPE only supports minio or local");
            }
            data.put("value", storageType);
        }
    }

    private String normalizeRagEngineValue(Object value) {
        String engine = stringValue(value);
        if (!StringUtils.hasText(engine)) {
            return "";
        }
        String normalized = engine.trim().toLowerCase(Locale.ROOT);
        if ("fast".equals(normalized)) {
            return RAG_ENGINE_FASTGPT;
        }
        return normalized;
    }

    private String normalizeAiDocumentStorageTypeValue(Object value) {
        String storageType = stringValue(value);
        if (!StringUtils.hasText(storageType)) {
            return "";
        }
        String normalized = storageType.trim().toLowerCase(Locale.ROOT);
        if ("synology".equals(normalized) || "synology-minio".equals(normalized)
                || "nas".equals(normalized) || "remote".equals(normalized)) {
            return AI_DOCUMENT_STORAGE_TYPE_MINIO;
        }
        if ("local-file".equals(normalized) || "filesystem".equals(normalized)) {
            return AI_DOCUMENT_STORAGE_TYPE_LOCAL;
        }
        return normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static TableDef def(String tableName, Set<String> columns, Set<String> searchColumns, String orderBy) {
        return new TableDef(tableName, columns, searchColumns, orderBy);
    }

    private static Set<String> cols(String... columns) {
        return new LinkedHashSet<>(Set.of(columns));
    }

    private static LinkedHashMap<String, String> columns(String... values) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            columns.put(values[i], values[i + 1]);
        }
        return columns;
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] content) {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (DataAccessException ex) {
            throw new ServiceException(500, "export failed");
        }
    }
}
