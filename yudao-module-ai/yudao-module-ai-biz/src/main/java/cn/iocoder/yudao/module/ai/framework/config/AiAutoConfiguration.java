package cn.iocoder.yudao.module.ai.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI 模块自动配置。
 *
 * <p>当前阶段只注册配置属性，不创建真实大模型或向量库客户端。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@MapperScan("cn.iocoder.yudao.module.ai.dal.mysql")
public class AiAutoConfiguration {
}
