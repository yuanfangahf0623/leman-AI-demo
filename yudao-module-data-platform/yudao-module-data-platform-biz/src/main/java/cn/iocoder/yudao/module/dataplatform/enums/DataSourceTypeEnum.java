package cn.iocoder.yudao.module.dataplatform.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DataSourceTypeEnum {
    MYSQL("MYSQL", "DATABASE", "com.mysql.cj.jdbc.Driver", 3306, true),
    POSTGRESQL("POSTGRESQL", "DATABASE", "org.postgresql.Driver", 5432, true),
    SQLSERVER("SQLSERVER", "DATABASE", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, true),
    DORIS("DORIS", "DATABASE", "com.mysql.cj.jdbc.Driver", 9030, true),
    TWO_HAO_HR("TWO_HAO_HR", "API", null, 443, false);

    private final String code;
    private final String category;
    private final String driverClassName;
    private final int defaultPort;
    private final boolean jdbc;

    DataSourceTypeEnum(String code, String category, String driverClassName, int defaultPort, boolean jdbc) {
        this.code = code;
        this.category = category;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
        this.jdbc = jdbc;
    }

    public static DataSourceTypeEnum of(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equalsIgnoreCase(code)).findFirst().orElse(null);
    }
}
