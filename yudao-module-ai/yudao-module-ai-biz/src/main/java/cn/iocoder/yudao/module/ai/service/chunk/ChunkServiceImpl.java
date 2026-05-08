package cn.iocoder.yudao.module.ai.service.chunk;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.enums.ChunkStatusEnum;
import cn.iocoder.yudao.module.ai.framework.parser.ParsedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_CHUNK_CREATE_FAILED;

/**
 * AI 文档切片 Service 实现。
 *
 * <p>只负责文档纯文本切片和 ai_document_chunk 落库，不调用向量库、不做 embedding。</p>
 */
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final AiDocumentChunkMapper documentChunkMapper;
    private final AiDocumentMapper documentMapper;
    private final DocumentChunkSplitter documentChunkSplitter;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AiDocumentChunkDO> recreateChunks(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document,
                                                  ParsedDocument parsedDocument) {
        // 先完成配置校验、切片和元数据构造，再替换旧 chunk，避免配置非法时误删旧数据。
        List<String> chunkContents = documentChunkSplitter.split(parsedDocument.getContent(),
                knowledgeBase.getChunkSize(), knowledgeBase.getChunkOverlap());
        List<AiDocumentChunkDO> chunks = buildChunks(knowledgeBase, document, parsedDocument, chunkContents);

        // 重新解析同一文档时，先逻辑删除旧 chunk，保证不会存在重复有效 chunk。
        documentChunkMapper.deleteByDocumentIdAndTenantId(document.getId(), knowledgeBase.getId(),
                document.getTenantId());
        for (AiDocumentChunkDO chunk : chunks) {
            documentChunkMapper.insert(chunk);
        }

        int totalTokenCount = chunks.stream()
                .map(AiDocumentChunkDO::getTokenCount)
                .reduce(0, Integer::sum);
        documentMapper.updateChunkSummaryByIdAndTenantId(document.getId(), document.getTenantId(),
                chunks.size(), totalTokenCount);
        return chunks;
    }

    private List<AiDocumentChunkDO> buildChunks(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document,
                                                ParsedDocument parsedDocument, List<String> chunkContents) {
        ArrayList<AiDocumentChunkDO> chunks = new ArrayList<>(chunkContents.size());
        for (int i = 0; i < chunkContents.size(); i++) {
            chunks.add(buildChunk(knowledgeBase, document, parsedDocument, chunkContents.get(i), i + 1));
        }
        return chunks;
    }

    private AiDocumentChunkDO buildChunk(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document,
                                         ParsedDocument parsedDocument, String content, Integer chunkNo) {
        int tokenCount = estimateTokenCount(content);
        AiDocumentChunkDO chunk = new AiDocumentChunkDO();
        chunk.setTenantId(document.getTenantId());
        chunk.setKnowledgeBaseId(knowledgeBase.getId());
        chunk.setDocumentId(document.getId());
        // 数据库字段名为 chunk_index，这里承载接口语义里的 chunkNo，从 1 开始连续编号。
        chunk.setChunkIndex(chunkNo);
        chunk.setContent(content);
        chunk.setTokenCount(tokenCount);
        chunk.setContentHash(sha256Hex(content));
        chunk.setMetadataJson(buildMetadataJson(parsedDocument, chunkNo, content, tokenCount));
        chunk.setStatus(ChunkStatusEnum.PENDING.getCode());
        return chunk;
    }

    private String buildMetadataJson(ParsedDocument parsedDocument, Integer chunkNo, String content, Integer tokenCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkNo", chunkNo);
        metadata.put("title", parsedDocument.getTitle());
        metadata.put("charCount", content.length());
        metadata.put("tokenCount", tokenCount);
        metadata.put("source", parsedDocument.getMetadata());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(DOCUMENT_CHUNK_CREATE_FAILED, "切片元数据序列化失败");
        }
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int cjkCount = 0;
        int otherCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else if (!Character.isWhitespace(ch)) {
                otherCount++;
            }
        }
        return Math.max(1, cjkCount + (int) Math.ceil(otherCount / 4.0D));
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(DOCUMENT_CHUNK_CREATE_FAILED, "计算切片哈希失败");
        }
    }

}
