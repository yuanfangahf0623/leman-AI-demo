package cn.iocoder.yudao.module.ai.service.chunk;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.framework.parser.ParsedDocument;

import java.util.List;

/**
 * AI 文档切片 Service。
 */
public interface ChunkService {

    /**
     * 重新生成文档切片。
     *
     * <p>同一文档重新解析时，先逻辑删除旧 chunk，再生成新 chunk。当前阶段只生成 chunk，不做 embedding。</p>
     *
     * @param knowledgeBase 知识库配置
     * @param document 文档记录
     * @param parsedDocument 文档解析结果
     * @return 新生成的 chunk 列表
     */
    List<AiDocumentChunkDO> recreateChunks(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document,
                                           ParsedDocument parsedDocument);

}
