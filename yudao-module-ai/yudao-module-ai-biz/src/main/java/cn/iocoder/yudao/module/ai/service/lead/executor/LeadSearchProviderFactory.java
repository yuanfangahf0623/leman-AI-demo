package cn.iocoder.yudao.module.ai.service.lead.executor;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the configured search provider.
 */
@Component
public class LeadSearchProviderFactory {

    public LeadSearchProvider create(String requestedProvider) {
        String provider = requestedProvider == null || requestedProvider.isBlank()
                ? "auto" : requestedProvider.trim().toLowerCase(Locale.ROOT);
        String bingKey = env("BING_SEARCH_API_KEY");
        String serpApiKey = env("SERPAPI_API_KEY");
        if ("bing".equals(provider) && LeadAgentUtils.hasText(bingKey)) {
            return new LeadHttpSearchProvider("bing", bingKey);
        }
        if ("serpapi".equals(provider) && LeadAgentUtils.hasText(serpApiKey)) {
            return new LeadHttpSearchProvider("serpapi", serpApiKey);
        }
        if ("auto".equals(provider)) {
            if (LeadAgentUtils.hasText(bingKey)) {
                return new LeadHttpSearchProvider("bing", bingKey);
            }
            if (LeadAgentUtils.hasText(serpApiKey)) {
                return new LeadHttpSearchProvider("serpapi", serpApiKey);
            }
        }
        return new LeadMockSearchProvider();
    }

    private String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

}
