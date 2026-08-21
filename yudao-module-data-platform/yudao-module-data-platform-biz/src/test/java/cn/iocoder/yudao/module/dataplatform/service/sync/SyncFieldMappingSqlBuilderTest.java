package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncFieldMappingVO;
import cn.iocoder.yudao.module.dataplatform.enums.DataSourceTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncFieldMappingSqlBuilderTest {

    private final SyncFieldMappingSqlBuilder builder = new SyncFieldMappingSqlBuilder(new ObjectMapper());

    @Test
    void shouldGenerateMappedSourceAndSinkSql() {
        SyncFieldMappingVO employeeId = mapping("employee_id", "id");
        SyncFieldMappingVO employeeName = mapping("employee_name", "name");
        employeeName.setTransform("TRIM");
        employeeName.setDefaultValue("未知");
        String config = builder.serialize(List.of(employeeId, employeeName));

        String sourceSql = builder.buildSourceSql("SELECT employee_id, employee_name FROM employee",
                DataSourceTypeEnum.MYSQL, config);
        String sinkSql = builder.buildSinkSql("ods", "ods_employee", config, null);

        assertEquals("SELECT `employee_id`, COALESCE(TRIM(`employee_name`), '未知') AS `employee_name` "
                + "FROM (SELECT employee_id, employee_name FROM employee) dp_source_mapping", sourceSql);
        assertEquals("INSERT INTO `ods`.`ods_employee` (`id`, `name`) VALUES (?, ?)", sinkSql);
    }

    @Test
    void shouldIgnoreDisabledMappings() {
        SyncFieldMappingVO enabled = mapping("employee_id", "id");
        SyncFieldMappingVO disabled = mapping("employee_name", "name");
        disabled.setEnabled(false);
        String config = builder.serialize(List.of(enabled, disabled));

        assertEquals("INSERT INTO `ods`.`employee` (`id`) VALUES (?)",
                builder.buildSinkSql("ods", "employee", config, null));
    }

    @Test
    void shouldKeepLegacySqlWhenMappingIsEmpty() {
        assertEquals("SELECT * FROM employee",
                builder.buildSourceSql("SELECT * FROM employee", DataSourceTypeEnum.MYSQL, null));
        assertEquals("INSERT INTO employee VALUES (?)",
                builder.buildSinkSql("ods", "employee", null, "INSERT INTO employee VALUES (?)"));
    }

    @Test
    void shouldRejectDuplicateTargetFields() {
        SyncFieldMappingVO first = mapping("employee_id", "id");
        SyncFieldMappingVO second = mapping("legacy_id", "ID");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> builder.serialize(List.of(first, second)));
        assertTrue(exception.getMessage().contains("目标字段不能重复映射"));
    }

    @Test
    void shouldGenerateSqlServerHexTransformAndAllowDigitLeadingColumn() {
        SyncFieldMappingVO binary = mapping("Datas", "Datas");
        binary.setTransform("HEX");
        SyncFieldMappingVO digitLeading = mapping("318Update", "318Update");
        String config = builder.serialize(List.of(binary, digitLeading));

        assertEquals("SELECT CONVERT(varchar(max), CONVERT(varbinary(max), \"Datas\"), 2) AS \"Datas\", \"318Update\" "
                        + "FROM (SELECT * FROM dbo.[comComTail]) dp_source_mapping",
                builder.buildSourceSql("SELECT * FROM dbo.[comComTail]", DataSourceTypeEnum.SQLSERVER, config));
    }

    private SyncFieldMappingVO mapping(String sourceField, String targetField) {
        SyncFieldMappingVO mapping = new SyncFieldMappingVO();
        mapping.setSourceField(sourceField);
        mapping.setTargetField(targetField);
        mapping.setEnabled(true);
        mapping.setTransform("NONE");
        return mapping;
    }
}
