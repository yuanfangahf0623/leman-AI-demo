package cn.iocoder.yudao.module.ai.service.websearch;

import java.util.List;

public interface WebSearchService {

    List<WebSearchResult> search(String query, int limit);

}
