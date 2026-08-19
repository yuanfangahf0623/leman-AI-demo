package cn.iocoder.yudao.module.dataplatform.service.warehouse;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseColumnRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseTableRespVO;
import cn.iocoder.yudao.module.dataplatform.framework.config.DataPlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.WAREHOUSE_CONNECT_FAILED;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataWarehouseServiceImpl implements DataWarehouseService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]{1,128}$");
    private static final Set<String> BUSINESS_DATABASES = Set.of("ods", "dwd", "dim", "dws", "ads", "tmp");

    private final DataPlatformProperties properties;

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT VERSION()")) {
            result.put("status", "UP");
            result.put("engine", "Apache Doris");
            result.put("version", rs.next() ? rs.getString(1) : "unknown");
            result.put("databases", listDatabases());
            return result;
        } catch (Exception ex) {
            result.put("status", "DOWN");
            result.put("engine", "Apache Doris");
            return result;
        }
    }

    @Override
    public List<String> listDatabases() {
        List<String> databases = new ArrayList<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (BUSINESS_DATABASES.contains(name.toLowerCase())) {
                    databases.add(name);
                }
            }
            return databases;
        } catch (Exception ex) {
            throw warehouseException("读取数仓库列表失败", ex);
        }
    }

    @Override
    public List<WarehouseTableRespVO> listTables(String database) {
        validateIdentifier(database, "数仓库名非法");
        if (!BUSINESS_DATABASES.contains(database.toLowerCase())) {
            throw new ServiceException(WAREHOUSE_CONNECT_FAILED, "只能访问 ODS/DWD/DIM/DWS/ADS/TMP 数仓分层");
        }
        List<WarehouseTableRespVO> tables = new ArrayList<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW TABLES FROM `" + database + "`")) {
            while (rs.next()) {
                tables.add(new WarehouseTableRespVO(database, rs.getString(1)));
            }
            return tables;
        } catch (Exception ex) {
            throw warehouseException("读取数仓表列表失败", ex);
        }
    }

    @Override
    public List<WarehouseColumnRespVO> listColumns(String database, String table) {
        validateIdentifier(database, "数仓库名非法");
        validateIdentifier(table, "数仓表名非法");
        if (!BUSINESS_DATABASES.contains(database.toLowerCase())) {
            throw new ServiceException(WAREHOUSE_CONNECT_FAILED, "只能访问数仓业务分层");
        }
        List<WarehouseColumnRespVO> columns = new ArrayList<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("DESC `" + database + "`.`" + table + "`")) {
            while (rs.next()) {
                columns.add(new WarehouseColumnRespVO(rs.getString("Field"), rs.getString("Type"),
                        "YES".equalsIgnoreCase(rs.getString("Null")), rs.getString("Key"),
                        rs.getString("Default"), getOptional(rs, "Comment")));
            }
            return columns;
        } catch (Exception ex) {
            throw warehouseException("读取数仓字段失败", ex);
        }
    }

    private Connection connection() throws Exception {
        DataPlatformProperties.Warehouse warehouse = properties.getWarehouse();
        return DriverManager.getConnection(warehouse.getJdbcUrl(), warehouse.getUsername(), warehouse.getPassword());
    }

    private void validateIdentifier(String value, String message) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new ServiceException(WAREHOUSE_CONNECT_FAILED, message);
        }
    }

    private String getOptional(ResultSet rs, String name) {
        try {
            return rs.getString(name);
        } catch (Exception ignored) {
            return "";
        }
    }

    private ServiceException warehouseException(String message, Exception ex) {
        log.warn("Doris 元数据访问失败, action={}, errorType={}", message, ex.getClass().getSimpleName());
        return new ServiceException(WAREHOUSE_CONNECT_FAILED, message);
    }
}
