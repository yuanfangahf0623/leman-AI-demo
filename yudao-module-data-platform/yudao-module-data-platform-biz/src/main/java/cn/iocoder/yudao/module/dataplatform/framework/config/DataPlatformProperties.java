package cn.iocoder.yudao.module.dataplatform.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "data-platform")
public class DataPlatformProperties {

    private String secretKey;
    private final Warehouse warehouse = new Warehouse();
    private final Seatunnel seatunnel = new Seatunnel();

    @Data
    public static class Warehouse {
        private String jdbcUrl = "jdbc:mysql://127.0.0.1:9030/information_schema?useUnicode=true&characterEncoding=utf8&useSSL=false&connectTimeout=5000&socketTimeout=15000";
        private String username = "root";
        private String password = "";
    }

    @Data
    public static class Seatunnel {
        private String command = "/opt/data-platform/shared/bin/seatunnel-submit.sh";
        private String jobDirectory = "/opt/data-platform/seatunnel/jobs";
        private String logDirectory = "/var/log/data-platform/jobs";
        private long timeoutSeconds = 3600;
    }
}
