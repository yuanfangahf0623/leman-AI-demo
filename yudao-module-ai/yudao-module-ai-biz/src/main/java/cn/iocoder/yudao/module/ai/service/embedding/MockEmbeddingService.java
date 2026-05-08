package cn.iocoder.yudao.module.ai.service.embedding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_DIMENSIONS_INVALID;

/**
 * 本地开发和测试使用的 Mock Embedding 服务。
 */
public class MockEmbeddingService implements AiEmbeddingService {

    private final int dimensions;
    private final List<Double> mockVector;

    public MockEmbeddingService(Integer dimensions) {
        if (dimensions == null || dimensions <= 0) {
            throw new ServiceException(EMBEDDING_DIMENSIONS_INVALID, "Embedding 向量维度配置非法");
        }
        this.dimensions = dimensions;
        this.mockVector = Collections.unmodifiableList(new ArrayList<>(Collections.nCopies(dimensions, 0.0D)));
    }

    @Override
    public List<Double> embed(String text) {
        return new ArrayList<>(mockVector);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Double>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    public int getDimensions() {
        return dimensions;
    }

}
