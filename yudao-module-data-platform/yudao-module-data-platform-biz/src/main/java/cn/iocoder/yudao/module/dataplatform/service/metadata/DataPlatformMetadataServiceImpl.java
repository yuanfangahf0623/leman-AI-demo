package cn.iocoder.yudao.module.dataplatform.service.metadata;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.*;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformMetadataFieldDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformMetadataTableDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformDataSourceMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformMetadataFieldMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformMetadataTableMapper;
import cn.iocoder.yudao.module.dataplatform.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataPlatformMetadataServiceImpl implements DataPlatformMetadataService {

    private static final int BATCH_SIZE = 1000;
    private static final Set<String> FACTORY_DAREN_TECHNICAL_LOGS = Set.of(
            "comAccountLog", "comChangeLog", "comCloudCheckLog", "comCloudKeyLog", "comCloudLog",
            "comDingTalkCallBackLog", "comDingTalkChangeLog", "comDingTalkLog", "comDSISyncLog",
            "comLogTimeClear", "comMCKBillCheckLog", "comMCKSendReadLog", "comSystemLog", "comSystemOptionLog");

    private final DataPlatformMetadataTableMapper tableMapper;
    private final DataPlatformMetadataFieldMapper fieldMapper;
    private final DataPlatformDataSourceMapper dataSourceMapper;
    private final DataPlatformDataSourceService dataSourceService;
    private final BusinessNameSuggester suggester;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<MetadataTableRespVO> pageTables(MetadataTablePageReqVO reqVO) {
        PageResult<DataPlatformMetadataTableDO> page = tableMapper.selectPage(reqVO);
        Map<Long, Long> confirmedCounts = confirmedFieldCounts(page.getList());
        Map<Long, String> dataSourceNames = page.getList().stream().map(DataPlatformMetadataTableDO::getDataSourceId)
                .distinct().map(dataSourceMapper::selectById).filter(Objects::nonNull)
                .collect(Collectors.toMap(DataPlatformDataSourceDO::getId, DataPlatformDataSourceDO::getName));
        List<MetadataTableRespVO> list = page.getList().stream()
                .map(item -> toTableResp(item, dataSourceNames.get(item.getDataSourceId()),
                        confirmedCounts.getOrDefault(item.getId(), 0L))).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public PageResult<MetadataFieldRespVO> pageFields(MetadataFieldPageReqVO reqVO) {
        requireTable(reqVO.getMetadataTableId());
        PageResult<DataPlatformMetadataFieldDO> page = fieldMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toFieldResp).toList(), page.getTotal());
    }

    @Override
    public Map<String, Object> summary(Long dataSourceId) {
        dataSourceService.requireDataSource(dataSourceId);
        Map<String, Object> tableStats = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) table_count, COALESCE(SUM(field_count),0) field_count, " +
                        "COALESCE(SUM(commented_field_count),0) source_comment_count " +
                        "FROM dp_metadata_table WHERE data_source_id=? AND status=0 AND deleted=b'0'", dataSourceId);
        Map<String, Object> fieldStats = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(CASE WHEN f.business_name IS NOT NULL AND f.business_name<>'' THEN 1 ELSE 0 END),0) named_count, " +
                        "COALESCE(SUM(CASE WHEN f.description IS NOT NULL AND f.description<>'' THEN 1 ELSE 0 END),0) described_count, " +
                        "COALESCE(SUM(CASE WHEN f.definition_status='CONFIRMED' THEN 1 ELSE 0 END),0) confirmed_count, " +
                        "COALESCE(SUM(CASE WHEN f.sensitivity_level IN ('SENSITIVE','RESTRICTED') THEN 1 ELSE 0 END),0) sensitive_count " +
                        "FROM dp_metadata_field f JOIN dp_metadata_table t ON t.id=f.metadata_table_id " +
                        "WHERE t.data_source_id=? AND t.status=0 AND t.deleted=b'0' AND f.status=0 AND f.deleted=b'0'",
                dataSourceId);
        long fields = number(tableStats.get("field_count"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableCount", number(tableStats.get("table_count")));
        result.put("fieldCount", fields);
        result.put("sourceCommentCount", number(tableStats.get("source_comment_count")));
        result.put("namedCount", number(fieldStats.get("named_count")));
        result.put("describedCount", number(fieldStats.get("described_count")));
        result.put("confirmedCount", number(fieldStats.get("confirmed_count")));
        result.put("sensitiveCount", number(fieldStats.get("sensitive_count")));
        result.put("confirmedCoverage", fields == 0 ? 0D : number(fieldStats.get("confirmed_count")) * 100D / fields);
        result.put("descriptionCoverage", fields == 0 ? 0D : number(fieldStats.get("described_count")) * 100D / fields);
        return result;
    }

    @Override
    public MetadataRefreshRespVO refresh(Long dataSourceId) {
        DataPlatformDataSourceDO dataSource = dataSourceService.requireDataSource(dataSourceId);
        DataSourceTypeEnum type = DataSourceTypeEnum.of(dataSource.getType());
        if (type == null || !type.isJdbc()) {
            throw new ServiceException(DATA_SOURCE_TYPE_UNSUPPORTED, "当前仅支持扫描数据库类型的数据源");
        }
        LocalDateTime scanTime = LocalDateTime.now();
        try {
            Class.forName(type.getDriverClassName());
            Properties props = new Properties();
            props.setProperty("user", dataSource.getUsername());
            props.setProperty("password", dataSourceService.decryptPassword(dataSource));
            List<DiscoveredTable> discovered;
            try (Connection connection = DriverManager.getConnection(dataSourceService.buildJdbcUrl(dataSource), props)) {
                discovered = type == DataSourceTypeEnum.SQLSERVER
                        ? scanSqlServer(connection, isFactoryDaren(dataSource))
                        : scanGeneric(connection);
            }
            RefreshCounters counters = persist(dataSource, discovered, scanTime);
            log.info("数据字典扫描完成, dataSourceId={}, code={}, tables={}, fields={}, sourceComments={}",
                    dataSourceId, dataSource.getCode(), counters.tableCount, counters.fieldCount,
                    counters.sourceCommentCount);
            return new MetadataRefreshRespVO(counters.tableCount, counters.fieldCount,
                    counters.sourceCommentCount, counters.generatedNameCount);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("数据字典扫描失败, dataSourceId={}, code={}, type={}, errorType={}", dataSourceId,
                    dataSource.getCode(), dataSource.getType(), ex.getClass().getSimpleName());
            throw new ServiceException(METADATA_SCAN_FAILED, "数据字典扫描失败，请检查数据源连接和元数据权限");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTable(MetadataTableUpdateReqVO reqVO) {
        requireTable(reqVO.getId());
        DataPlatformMetadataTableDO update = new DataPlatformMetadataTableDO();
        update.setId(reqVO.getId());
        update.setBusinessName(trimToNull(reqVO.getBusinessName()));
        update.setBusinessDomain(trimToNull(reqVO.getBusinessDomain()));
        update.setDescription(trimToNull(reqVO.getDescription()));
        update.setDefinitionStatus("CONFIRMED");
        tableMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateField(MetadataFieldUpdateReqVO reqVO) {
        DataPlatformMetadataFieldDO existing = fieldMapper.selectById(reqVO.getId());
        if (existing == null) throw new ServiceException(METADATA_FIELD_NOT_EXISTS, "字段字典记录不存在");
        DataPlatformMetadataFieldDO update = new DataPlatformMetadataFieldDO();
        update.setId(reqVO.getId());
        update.setBusinessName(reqVO.getBusinessName().trim());
        update.setDescription(trimToNull(reqVO.getDescription()));
        update.setClassification(trimToNull(reqVO.getClassification()));
        update.setSensitivityLevel(reqVO.getSensitivityLevel());
        update.setIncrementalCandidate(Boolean.TRUE.equals(reqVO.getIncrementalCandidate()));
        update.setDefinitionStatus("CONFIRMED");
        fieldMapper.updateById(update);
    }

    private List<DiscoveredTable> scanSqlServer(Connection connection, boolean excludeTechnicalLogs) throws SQLException {
        String sql = "SELECT s.name source_schema,t.name source_table,c.name source_column,ty.name data_type," +
                "c.max_length column_size,c.precision decimal_precision,c.scale decimal_digits,c.is_nullable," +
                "c.column_id ordinal_position,CASE WHEN EXISTS (SELECT 1 FROM sys.indexes i " +
                "JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id " +
                "WHERE i.object_id=t.object_id AND i.is_primary_key=1 AND ic.column_id=c.column_id) THEN 1 ELSE 0 END primary_key," +
                "CAST(ep.value AS nvarchar(1000)) source_comment " +
                "FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id " +
                "JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id " +
                "LEFT JOIN sys.extended_properties ep ON ep.class=1 AND ep.major_id=t.object_id " +
                "AND ep.minor_id=c.column_id AND ep.name='MS_Description' " +
                "WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name,c.column_id";
        Map<TableKey, DiscoveredTable> tables = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("source_table");
                if (excludeTechnicalLogs && FACTORY_DAREN_TECHNICAL_LOGS.contains(tableName)) continue;
                String schema = rs.getString("source_schema");
                DiscoveredTable table = tables.computeIfAbsent(new TableKey(schema, tableName),
                        ignored -> new DiscoveredTable(schema, tableName));
                String type = rs.getString("data_type");
                table.fields.add(new DiscoveredField(rs.getString("source_column"), type, jdbcType(type),
                        nullableInt(rs, "column_size"), nullableInt(rs, "decimal_digits"),
                        rs.getBoolean("is_nullable"), rs.getBoolean("primary_key"),
                        rs.getInt("ordinal_position"), trimToNull(rs.getString("source_comment"))));
            }
        }
        return new ArrayList<>(tables.values());
    }

    private List<DiscoveredTable> scanGeneric(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<DiscoveredTable> tables = new ArrayList<>();
        try (ResultSet tableRs = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tableRs.next()) {
                String schema = Optional.ofNullable(tableRs.getString("TABLE_SCHEM")).orElse("");
                String tableName = tableRs.getString("TABLE_NAME");
                Set<String> primaryKeys = new HashSet<>();
                try (ResultSet pkRs = metadata.getPrimaryKeys(connection.getCatalog(), schema, tableName)) {
                    while (pkRs.next()) primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
                DiscoveredTable table = new DiscoveredTable(schema, tableName);
                try (ResultSet columnRs = metadata.getColumns(connection.getCatalog(), schema, tableName, "%")) {
                    while (columnRs.next()) {
                        String column = columnRs.getString("COLUMN_NAME");
                        table.fields.add(new DiscoveredField(column, columnRs.getString("TYPE_NAME"),
                                columnRs.getInt("DATA_TYPE"), nullableInt(columnRs, "COLUMN_SIZE"),
                                nullableInt(columnRs, "DECIMAL_DIGITS"),
                                columnRs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                                primaryKeys.contains(column), columnRs.getInt("ORDINAL_POSITION"),
                                trimToNull(columnRs.getString("REMARKS"))));
                    }
                }
                tables.add(table);
            }
        }
        return tables;
    }

    private RefreshCounters persist(DataPlatformDataSourceDO dataSource, List<DiscoveredTable> discovered,
                                    LocalDateTime scanTime) {
        jdbcTemplate.update("UPDATE dp_metadata_field f JOIN dp_metadata_table t ON t.id=f.metadata_table_id " +
                "SET f.status=1 WHERE t.data_source_id=? AND f.deleted=b'0'", dataSource.getId());
        jdbcTemplate.update("UPDATE dp_metadata_table SET status=1 WHERE data_source_id=? AND deleted=b'0'",
                dataSource.getId());
        Map<TableKey, Long> ids = new HashMap<>();
        boolean factoryDaren = isFactoryDaren(dataSource);
        for (DiscoveredTable item : discovered) {
            DataPlatformMetadataTableDO table = tableMapper.selectBySource(dataSource.getId(), item.schema, item.name);
            if (table == null) {
                table = new DataPlatformMetadataTableDO();
                table.setDataSourceId(dataSource.getId());
                table.setSourceSchema(item.schema);
                table.setSourceTable(item.name);
                table.setBusinessName(suggester.suggestTableName(item.name));
                table.setBusinessDomain(suggester.suggestBusinessDomain(item.name));
                table.setDefinitionStatus("GENERATED");
                table.setCreator("metadata-scan");
            }
            table.setTargetDatabase(factoryDaren ? "ods" : table.getTargetDatabase());
            table.setTargetTable(factoryDaren ? "ods_factory_daren_" + snake(item.name) : table.getTargetTable());
            table.setFieldCount(item.fields.size());
            table.setCommentedFieldCount((int) item.fields.stream().filter(field -> field.comment != null).count());
            table.setStatus(0);
            table.setLastScanTime(scanTime);
            table.setUpdater("metadata-scan");
            if (table.getId() == null) tableMapper.insert(table); else tableMapper.updateById(table);
            ids.put(new TableKey(item.schema, item.name), table.getId());
        }

        String upsert = "INSERT INTO dp_metadata_field " +
                "(metadata_table_id,source_column,target_column,data_type,jdbc_type,column_size,decimal_digits,nullable," +
                "primary_key,ordinal_position,source_comment,business_name,description,classification,sensitivity_level," +
                "incremental_candidate,definition_status,status,last_scan_time,creator,updater,deleted) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'metadata-scan','metadata-scan',b'0') " +
                "ON DUPLICATE KEY UPDATE target_column=VALUES(target_column),data_type=VALUES(data_type)," +
                "jdbc_type=VALUES(jdbc_type),column_size=VALUES(column_size),decimal_digits=VALUES(decimal_digits)," +
                "nullable=VALUES(nullable),primary_key=VALUES(primary_key),ordinal_position=VALUES(ordinal_position)," +
                "source_comment=VALUES(source_comment),business_name=IF(business_name IS NULL OR business_name='',VALUES(business_name),business_name)," +
                "description=IF(definition_status='GENERATED' AND (description IS NULL OR description=''),VALUES(description),description)," +
                "classification=IF(classification IS NULL OR classification='',VALUES(classification),classification)," +
                "sensitivity_level=IF(definition_status='GENERATED',VALUES(sensitivity_level),sensitivity_level)," +
                "incremental_candidate=IF(definition_status='GENERATED',VALUES(incremental_candidate),incremental_candidate)," +
                "status=0,last_scan_time=VALUES(last_scan_time),updater='metadata-scan'";
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        RefreshCounters counters = new RefreshCounters();
        counters.tableCount = discovered.size();
        for (DiscoveredTable table : discovered) {
            Long tableId = ids.get(new TableKey(table.schema, table.name));
            for (DiscoveredField field : table.fields) {
                String businessName = suggester.suggestFieldName(field.name, field.comment);
                String definitionStatus = field.comment == null ? "GENERATED" : "CONFIRMED";
                batch.add(new Object[]{tableId, field.name, field.name, field.dataType, field.jdbcType,
                        field.columnSize, field.decimalDigits, field.nullable, field.primaryKey, field.ordinal,
                        field.comment, businessName, field.comment, suggester.suggestClassification(field.name),
                        suggester.suggestSensitivity(field.name),
                        suggester.isIncrementalCandidate(field.name, field.dataType), definitionStatus, 0,
                        Timestamp.valueOf(scanTime)});
                counters.fieldCount++;
                if (field.comment != null) counters.sourceCommentCount++; else counters.generatedNameCount++;
                if (batch.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(upsert, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) jdbcTemplate.batchUpdate(upsert, batch);
        jdbcTemplate.update("UPDATE dp_metadata_field f " +
                "JOIN dp_metadata_table t ON t.id=f.metadata_table_id " +
                "JOIN dp_sync_job j ON j.source_data_source_id=t.data_source_id " +
                "AND j.target_database=t.target_database AND j.target_table=t.target_table " +
                "AND j.sync_mode='INCREMENTAL' AND j.deleted=b'0' " +
                "SET f.incremental_candidate=b'1' " +
                "WHERE t.data_source_id=? AND LOWER(j.watermark_column)=LOWER(f.source_column) " +
                "AND t.status=0 AND f.status=0 AND t.deleted=b'0' AND f.deleted=b'0'", dataSource.getId());
        return counters;
    }

    private Map<Long, Long> confirmedFieldCounts(List<DataPlatformMetadataTableDO> tables) {
        if (tables.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(tables.size(), "?"));
        Object[] ids = tables.stream().map(DataPlatformMetadataTableDO::getId).toArray();
        Map<Long, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT metadata_table_id,COUNT(*) count_value FROM dp_metadata_field " +
                        "WHERE deleted=b'0' AND status=0 AND definition_status='CONFIRMED' " +
                        "AND metadata_table_id IN (" + placeholders + ") GROUP BY metadata_table_id", ids);
        rows.forEach(row -> result.put(number(row.get("metadata_table_id")), number(row.get("count_value"))));
        return result;
    }

    private DataPlatformMetadataTableDO requireTable(Long id) {
        DataPlatformMetadataTableDO table = id == null ? null : tableMapper.selectById(id);
        if (table == null) throw new ServiceException(METADATA_TABLE_NOT_EXISTS, "表字典记录不存在");
        return table;
    }

    private MetadataTableRespVO toTableResp(DataPlatformMetadataTableDO item, String dataSourceName,
                                            long confirmedFieldCount) {
        MetadataTableRespVO resp = new MetadataTableRespVO();
        resp.setId(item.getId());
        resp.setDataSourceId(item.getDataSourceId());
        resp.setDataSourceName(dataSourceName);
        resp.setSourceSchema(item.getSourceSchema());
        resp.setSourceTable(item.getSourceTable());
        resp.setBusinessName(item.getBusinessName());
        resp.setBusinessDomain(item.getBusinessDomain());
        resp.setDescription(item.getDescription());
        resp.setTargetDatabase(item.getTargetDatabase());
        resp.setTargetTable(item.getTargetTable());
        resp.setFieldCount(item.getFieldCount());
        resp.setCommentedFieldCount(item.getCommentedFieldCount());
        resp.setConfirmedFieldCount(confirmedFieldCount);
        resp.setDefinitionStatus(item.getDefinitionStatus());
        resp.setLastScanTime(item.getLastScanTime());
        return resp;
    }

    private MetadataFieldRespVO toFieldResp(DataPlatformMetadataFieldDO item) {
        MetadataFieldRespVO resp = new MetadataFieldRespVO();
        resp.setId(item.getId());
        resp.setMetadataTableId(item.getMetadataTableId());
        resp.setSourceColumn(item.getSourceColumn());
        resp.setTargetColumn(item.getTargetColumn());
        resp.setDataType(item.getDataType());
        resp.setJdbcType(item.getJdbcType());
        resp.setColumnSize(item.getColumnSize());
        resp.setDecimalDigits(item.getDecimalDigits());
        resp.setNullable(item.getNullable());
        resp.setPrimaryKey(item.getPrimaryKey());
        resp.setOrdinalPosition(item.getOrdinalPosition());
        resp.setSourceComment(item.getSourceComment());
        resp.setBusinessName(item.getBusinessName());
        resp.setDescription(item.getDescription());
        resp.setClassification(item.getClassification());
        resp.setSensitivityLevel(item.getSensitivityLevel());
        resp.setIncrementalCandidate(item.getIncrementalCandidate());
        resp.setDefinitionStatus(item.getDefinitionStatus());
        resp.setLastScanTime(item.getLastScanTime());
        return resp;
    }

    private boolean isFactoryDaren(DataPlatformDataSourceDO source) {
        String code = Optional.ofNullable(source.getCode()).orElse("").toLowerCase(Locale.ROOT);
        return "工厂达人".equals(source.getName()) || code.contains("factory") && code.contains("daren");
    }

    private String snake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private Integer jdbcType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "bigint" -> Types.BIGINT;
            case "int" -> Types.INTEGER;
            case "smallint" -> Types.SMALLINT;
            case "tinyint" -> Types.TINYINT;
            case "bit" -> Types.BIT;
            case "decimal", "numeric", "money", "smallmoney" -> Types.DECIMAL;
            case "float" -> Types.DOUBLE;
            case "real" -> Types.REAL;
            case "date" -> Types.DATE;
            case "datetime", "datetime2", "smalldatetime" -> Types.TIMESTAMP;
            case "time" -> Types.TIME;
            case "char", "nchar" -> Types.CHAR;
            case "varchar", "nvarchar", "text", "ntext", "xml", "uniqueidentifier" -> Types.VARCHAR;
            case "binary", "varbinary", "image", "timestamp", "rowversion" -> Types.VARBINARY;
            default -> Types.OTHER;
        };
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record TableKey(String schema, String table) {
    }

    private static final class DiscoveredTable {
        private final String schema;
        private final String name;
        private final List<DiscoveredField> fields = new ArrayList<>();

        private DiscoveredTable(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }
    }

    private record DiscoveredField(String name, String dataType, Integer jdbcType, Integer columnSize,
                                   Integer decimalDigits, boolean nullable, boolean primaryKey, int ordinal,
                                   String comment) {
    }

    private static final class RefreshCounters {
        private int tableCount;
        private int fieldCount;
        private int sourceCommentCount;
        private int generatedNameCount;
    }
}
