package cn.iocoder.yudao.module.dataplatform.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(DataPlatformProperties.class)
@MapperScan("cn.iocoder.yudao.module.dataplatform.dal.mysql")
public class DataPlatformAutoConfiguration {

    @Bean("dataPlatformTaskExecutor")
    public Executor dataPlatformTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("data-platform-");
        executor.initialize();
        return executor;
    }
}
