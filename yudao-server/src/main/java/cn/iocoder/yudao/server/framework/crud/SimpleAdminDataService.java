package cn.iocoder.yudao.server.framework.crud;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.server.framework.security.LoginUser;
import cn.iocoder.yudao.server.framework.security.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Local integration CRUD helper for yudao compatible admin pages.
 */
@Service
@RequiredArgsConstructor
public class SimpleAdminDataService {

    private final JdbcTemplate jdbcTemplate;

    public TableDef table(String tableName, Collection<String> columns, Collection<String> searchColumns, String orderBy) {
        return new TableDef(tableName, new LinkedHashSet<>(columns), new LinkedHashSet<>(searchColumns), orderBy);
    }

    public PageResult<Map<String, Object>> page(TableDef table, Map<String, ?> params) {
        Query query = buildQuery(table, params);
        int pageNo = positiveInt(params.get("pageNo"), 1);
        int pageSize = Math.min(positiveInt(params.get("pageSize"), 10), 200);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + quote(table.tableName()) + query.whereSql(),
                Long.class, query.args().toArray());
        List<Object> args = new ArrayList<>(query.args());
        args.add((pageNo - 1) * pageSize);
        args.add(pageSize);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM " + quote(table.tableName())
                + query.whereSql() + orderBy(table) + " LIMIT ?, ?", args.toArray()).stream().map(this::toCamelMap).toList();
        return new PageResult<>(list, total == null ? 0L : total);
    }

    public List<Map<String, Object>> list(TableDef table, Map<String, ?> params) {
        Query query = buildQuery(table, params);
        return jdbcTemplate.queryForList("SELECT * FROM " + quote(table.tableName()) + query.whereSql() + orderBy(table),
                query.args().toArray()).stream().map(this::toCamelMap).toList();
    }

    public Map<String, Object> get(TableDef table, Long id) {
        if (id == null) {
            throw new ServiceException(400, "编号不能为空");
        }
        try {
            return toCamelMap(jdbcTemplate.queryForMap("SELECT * FROM " + quote(table.tableName())
                    + " WHERE id = ? AND deleted = 0", id));
        } catch (EmptyResultDataAccessException ex) {
            throw new ServiceException(404, "数据不存在");
        }
    }

    public Long create(TableDef table, Map<String, ?> data) {
        Map<String, Object> values = normalizeForWrite(table, data, false);
        values.putIfAbsent("creator", currentUsername());
        values.putIfAbsent("updater", currentUsername());
        values.putIfAbsent("create_time", LocalDateTime.now());
        values.putIfAbsent("update_time", LocalDateTime.now());
        values.putIfAbsent("deleted", false);
        if (table.columns().contains("tenant_id")) {
            values.putIfAbsent("tenant_id", currentTenantId());
        }
        if (table.columns().contains("status")) {
            values.putIfAbsent("status", 0);
        }
        if (table.columns().contains("sort")) {
            values.putIfAbsent("sort", 0);
        }
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + quote(table.tableName()) + " (" + joinQuoted(columns) + ") VALUES ("
                + String.join(", ", columns.stream().map(column -> "?").toList()) + ")";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            bind(ps, values.values().stream().toList());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void update(TableDef table, Map<String, ?> data) {
        Long id = longValue(data.get("id"));
        if (id == null) {
            throw new ServiceException(400, "编号不能为空");
        }
        get(table, id);
        Map<String, Object> values = normalizeForWrite(table, data, true);
        values.remove("id");
        values.put("updater", currentUsername());
        values.put("update_time", LocalDateTime.now());
        if (values.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = "UPDATE " + quote(table.tableName()) + " SET "
                + String.join(", ", columns.stream().map(column -> quote(column) + " = ?").toList())
                + " WHERE id = ? AND deleted = 0";
        List<Object> args = new ArrayList<>(values.values());
        args.add(id);
        jdbcTemplate.update(sql, args.toArray());
    }

    public void delete(TableDef table, Long id) {
        if (id == null) {
            throw new ServiceException(400, "编号不能为空");
        }
        jdbcTemplate.update("UPDATE " + quote(table.tableName()) + " SET deleted = 1, updater = ?, update_time = ?"
                + " WHERE id = ? AND deleted = 0", currentUsername(), LocalDateTime.now(), id);
    }

    public void deleteList(TableDef table, String ids) {
        if (!StringUtils.hasText(ids)) {
            return;
        }
        for (String id : ids.split(",")) {
            Long value = longValue(id.trim());
            if (value != null) {
                delete(table, value);
            }
        }
    }

    public void replaceLongRelations(String tableName, String ownerColumn, Long ownerId, String valueColumn, Collection<?> values) {
        jdbcTemplate.update("DELETE FROM " + quote(tableName) + " WHERE " + quote(ownerColumn) + " = ?", ownerId);
        if (values == null) {
            return;
        }
        for (Object value : values) {
            Long longValue = longValue(value);
            if (longValue != null) {
                jdbcTemplate.update("INSERT INTO " + quote(tableName) + " (" + quote(ownerColumn) + ", " + quote(valueColumn)
                        + ") VALUES (?, ?)", ownerId, longValue);
            }
        }
    }

    public List<Long> selectLongRelations(String tableName, String ownerColumn, Long ownerId, String valueColumn) {
        return jdbcTemplate.queryForList("SELECT " + quote(valueColumn) + " FROM " + quote(tableName)
                + " WHERE " + quote(ownerColumn) + " = ?", Long.class, ownerId);
    }

    public void updateColumns(String tableName, Long id, Map<String, Object> values) {
        if (id == null || values == null || values.isEmpty()) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(key, normalizeWriteValue(value)));
        List<String> columns = new ArrayList<>(normalized.keySet());
        List<Object> args = new ArrayList<>(normalized.values());
        args.add(id);
        jdbcTemplate.update("UPDATE " + quote(tableName) + " SET "
                        + String.join(", ", columns.stream().map(column -> quote(column) + " = ?").toList())
                        + " WHERE id = ?",
                args.toArray());
    }

    public byte[] exportExcel(TableDef table, Map<String, ?> params, LinkedHashMap<String, String> columns, String sheetName) {
        return exportExcel(list(table, params), columns, sheetName);
    }

    public byte[] exportExcel(List<Map<String, Object>> rows, LinkedHashMap<String, String> columns, String sheetName) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            CreationHelper creationHelper = workbook.getCreationHelper();
            dateTimeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            Row header = sheet.createRow(0);
            int columnIndex = 0;
            for (String title : columns.values()) {
                header.createCell(columnIndex++).setCellValue(title);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> data = rows.get(rowIndex);
                columnIndex = 0;
                for (String field : columns.keySet()) {
                    writeCell(row.createCell(columnIndex++), data.get(field), dateTimeStyle);
                }
            }
            for (int i = 0; i < columns.size(); i++) {
                sheet.setColumnWidth(i, 20 * 256);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ServiceException(500, "导出 Excel 失败");
        }
    }

    private void writeCell(Cell cell, Object value, CellStyle dateTimeStyle) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(dateTimeStyle);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private String safeSheetName(String sheetName) {
        if (!StringUtils.hasText(sheetName)) {
            return "Sheet1";
        }
        String safeName = sheetName.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return safeName.length() > 31 ? safeName.substring(0, 31) : safeName;
    }

    private Query buildQuery(TableDef table, Map<String, ?> params) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (table.columns().contains("deleted")) {
            conditions.add("deleted = 0");
        }
        if (params != null) {
            params.forEach((key, value) -> {
                if (!StringUtils.hasText(key) || value == null || isPageParam(key) || key.contains("[")) {
                    return;
                }
                String column = camelToUnderline(key);
                if (!table.columns().contains(column) || !StringUtils.hasText(String.valueOf(value))) {
                    return;
                }
                if (!table.searchColumns().isEmpty() && !table.searchColumns().contains(column)) {
                    return;
                }
                if (isLikeColumn(column)) {
                    conditions.add(quote(column) + " LIKE ?");
                    args.add("%" + value + "%");
                } else {
                    conditions.add(quote(column) + " = ?");
                    args.add(value);
                }
            });
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new Query(where, args);
    }

    private Map<String, Object> normalizeForWrite(TableDef table, Map<String, ?> data, boolean update) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (data == null) {
            return values;
        }
        data.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null || (update && "createTime".equals(key))) {
                return;
            }
            String column = camelToUnderline(key);
            if (!table.columns().contains(column) || "deleted".equals(column) || "create_time".equals(column)
                    || "update_time".equals(column) || "creator".equals(column) || "updater".equals(column)) {
                return;
            }
            values.put(column, normalizeWriteValue(value));
        });
        return values;
    }

    private Map<String, Object> toCamelMap(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(underlineToCamel(key), normalizeValue(value)));
        return result;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes && bytes.length == 1) {
            return bytes[0] != 0;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }

    private Object normalizeWriteValue(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return String.valueOf(value);
        }
        return value;
    }

    private void bind(PreparedStatement ps, List<Object> values) throws java.sql.SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setObject(i + 1, values.get(i));
        }
    }

    private String orderBy(TableDef table) {
        if (!StringUtils.hasText(table.orderBy())) {
            return " ORDER BY id DESC";
        }
        return " ORDER BY " + table.orderBy();
    }

    private String joinQuoted(List<String> names) {
        return String.join(", ", names.stream().map(this::quote).toList());
    }

    private String quote(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new ServiceException(500, "数据库标识非法");
        }
        return "`" + identifier + "`";
    }

    private boolean isPageParam(String key) {
        return Set.of("pageNo", "pageSize").contains(key);
    }

    private boolean isLikeColumn(String column) {
        String lower = column.toLowerCase(Locale.ROOT);
        return lower.contains("name") || lower.contains("title") || lower.contains("code")
                || lower.contains("username") || lower.contains("nickname") || lower.contains("remark")
                || lower.contains("description") || lower.contains("path") || lower.contains("ip");
    }

    private int positiveInt(Object value, int defaultValue) {
        Long number = longValue(value);
        return number == null || number <= 0 ? defaultValue : number.intValue();
    }

    public Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public List<?> collectionValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private Long currentTenantId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null || loginUser.getTenantId() == null ? 1L : loginUser.getTenantId();
    }

    private String currentUsername() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null || !StringUtils.hasText(loginUser.getUsername()) ? "admin" : loginUser.getUsername();
    }

    private String camelToUnderline(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String underlineToCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public record TableDef(String tableName, Set<String> columns, Set<String> searchColumns, String orderBy) {
    }

    private record Query(String whereSql, List<Object> args) {
    }
}
