package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncFieldMappingVO;
import cn.iocoder.yudao.module.dataplatform.enums.DataSourceTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;

@Component
@RequiredArgsConstructor
public class SyncFieldMappingSqlBuilder {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$");
    private static final TypeReference<List<SyncFieldMappingVO>> MAPPING_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public String serialize(List<SyncFieldMappingVO> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }
        validate(mappings);
        try {
            return objectMapper.writeValueAsString(mappings);
        } catch (Exception ex) {
            throw invalid("字段映射保存失败");
        }
    }

    public List<SyncFieldMappingVO> deserialize(String mappingConfig) {
        if (!StringUtils.hasText(mappingConfig)) {
            return List.of();
        }
        try {
            List<SyncFieldMappingVO> mappings = objectMapper.readValue(mappingConfig, MAPPING_TYPE);
            if (mappings == null) {
                return List.of();
            }
            validate(mappings);
            return mappings;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("字段映射配置格式错误");
        }
    }

    public String buildSourceSql(String sourceSql, DataSourceTypeEnum sourceType, String mappingConfig) {
        List<SyncFieldMappingVO> mappings = enabled(deserialize(mappingConfig));
        if (mappings.isEmpty()) {
            return sourceSql;
        }
        if (sourceType == DataSourceTypeEnum.SQLSERVER
                && sourceSql.stripLeading().toUpperCase(Locale.ROOT).startsWith("WITH ")) {
            throw invalid("SQL Server 的 CTE 查询暂不支持字段映射，请改为普通 SELECT 或视图");
        }
        String fields = mappings.stream().map(item -> sourceExpression(item, sourceType))
                .reduce((left, right) -> left + ", " + right).orElseThrow();
        return "SELECT " + fields + " FROM (" + sourceSql.strip() + ") dp_source_mapping";
    }

    public String buildSinkSql(String database, String table, String mappingConfig, String legacySinkSql) {
        List<SyncFieldMappingVO> mappings = enabled(deserialize(mappingConfig));
        if (mappings.isEmpty()) {
            return legacySinkSql;
        }
        validateIdentifier(database, "目标库名非法");
        validateIdentifier(table, "目标表名非法");
        String fields = mappings.stream().map(item -> quote(item.getTargetField(), DataSourceTypeEnum.DORIS))
                .reduce((left, right) -> left + ", " + right).orElseThrow();
        String placeholders = String.join(", ", mappings.stream().map(item -> "?").toList());
        return "INSERT INTO `" + database + "`.`" + table + "` (" + fields + ") VALUES (" + placeholders + ")";
    }

    public void validate(List<SyncFieldMappingVO> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        if (mappings.size() > 500) {
            throw invalid("字段映射不能超过 500 个字段");
        }
        Set<String> targetFields = new HashSet<>();
        int enabledCount = 0;
        for (SyncFieldMappingVO mapping : mappings) {
            if (!isEnabled(mapping)) {
                continue;
            }
            enabledCount++;
            validateIdentifier(mapping.getSourceField(), "启用的源字段不能为空或包含非法字符");
            validateIdentifier(mapping.getTargetField(), "启用的目标字段不能为空或包含非法字符");
            String normalizedTarget = mapping.getTargetField().toLowerCase(Locale.ROOT);
            if (!targetFields.add(normalizedTarget)) {
                throw invalid("目标字段不能重复映射：" + mapping.getTargetField());
            }
            String transform = StringUtils.hasText(mapping.getTransform()) ? mapping.getTransform() : "NONE";
            if (!Set.of("NONE", "TRIM", "HEX").contains(transform)) {
                throw invalid("不支持的字段转换：" + transform);
            }
        }
        if (enabledCount == 0) {
            throw invalid("字段映射至少启用一个字段");
        }
    }

    private String sourceExpression(SyncFieldMappingVO mapping, DataSourceTypeEnum sourceType) {
        String quotedField = quote(mapping.getSourceField(), sourceType);
        String expression = quotedField;
        boolean transformed = false;
        if ("TRIM".equals(mapping.getTransform())) {
            expression = "TRIM(" + expression + ")";
            transformed = true;
        } else if ("HEX".equals(mapping.getTransform())) {
            if (sourceType == DataSourceTypeEnum.SQLSERVER) {
                expression = "CONVERT(varchar(max), CONVERT(varbinary(max), " + expression + "), 2)";
            } else if (sourceType == DataSourceTypeEnum.MYSQL) {
                expression = "HEX(" + expression + ")";
            } else {
                throw invalid("当前数据源不支持二进制 HEX 转换");
            }
            transformed = true;
        }
        if (StringUtils.hasText(mapping.getDefaultValue())) {
            expression = "COALESCE(" + expression + ", '" + mapping.getDefaultValue().replace("'", "''") + "')";
            transformed = true;
        }
        return transformed ? expression + " AS " + quotedField : expression;
    }

    private String quote(String identifier, DataSourceTypeEnum type) {
        validateIdentifier(identifier, "字段名非法");
        return (type == DataSourceTypeEnum.MYSQL || type == DataSourceTypeEnum.DORIS)
                ? "`" + identifier + "`" : "\"" + identifier + "\"";
    }

    private void validateIdentifier(String value, String message) {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value).matches()) {
            throw invalid(message);
        }
    }

    private List<SyncFieldMappingVO> enabled(List<SyncFieldMappingVO> mappings) {
        return mappings.stream().filter(this::isEnabled).toList();
    }

    private boolean isEnabled(SyncFieldMappingVO mapping) {
        return mapping != null && !Boolean.FALSE.equals(mapping.getEnabled());
    }

    private ServiceException invalid(String message) {
        return new ServiceException(SYNC_JOB_CONFIG_INVALID, message);
    }
}
