package cn.iocoder.yudao.module.ai.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 模块自动配置。
 *
 * <p>当前阶段只注册配置属性，不创建真实大模型或向量库客户端。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@MapperScan("cn.iocoder.yudao.module.ai.dal.mysql")
public class AiAutoConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 管理端分页接口统一走 MyBatis Plus 分页插件，当前数据库脚本以 MySQL 为主。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}
