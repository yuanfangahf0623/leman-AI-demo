package cn.iocoder.yudao.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * yudao 兼容后端启动入口。
 */
@SpringBootApplication(scanBasePackages = "cn.iocoder.yudao")
@ConfigurationPropertiesScan(basePackages = "cn.iocoder.yudao")
@EnableMethodSecurity
@MapperScan("cn.iocoder.yudao.module.system.dal.mysql")
public class YudaoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YudaoServerApplication.class, args);
    }

}
