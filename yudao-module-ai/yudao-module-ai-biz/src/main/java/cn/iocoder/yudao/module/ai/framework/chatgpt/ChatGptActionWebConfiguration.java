package cn.iocoder.yudao.module.ai.framework.chatgpt;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiChatGptActionsProperties.class)
@RequiredArgsConstructor
public class ChatGptActionWebConfiguration implements WebMvcConfigurer {

    private final ChatGptActionAuthInterceptor chatGptActionAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(chatGptActionAuthInterceptor)
                .addPathPatterns("/openapi/chatgpt/**");
    }

}
