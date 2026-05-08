package cn.iocoder.yudao.module.ai.service.embedding;

import java.util.List;

/**
 * AI Embedding 服务抽象。
 */
public interface AiEmbeddingService {

    List<Double> embed(String text);

    List<List<Double>> embedBatch(List<String> texts);

}
