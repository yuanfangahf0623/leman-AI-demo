package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SearchEvidence;

import java.util.List;

/**
 * Search provider abstraction for lead collection.
 */
public interface LeadSearchProvider {

    String providerName();

    List<String> search(String keyword, String country, int maxResults);

    default List<SearchEvidence> searchEvidence(String query, String country, int maxResults) {
        return List.of();
    }

    default java.util.Map<String, String> getMockPages() {
        return java.util.Map.of();
    }

}
