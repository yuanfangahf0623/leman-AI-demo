package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchRequestDTO;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptMeetingKnowledgeSearchResultDTO;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import cn.iocoder.yudao.module.ai.service.rag.RagChatRequest;
import cn.iocoder.yudao.module.ai.service.rag.RagChatResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatGptMeetingKnowledgeSearchClient implements ChatGptMeetingKnowledgeSearchClient {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final String DOCUMENT_TYPE_MINUTES = "meeting_minutes";
    private static final String DOCUMENT_TYPE_TRANSCRIPT = "meeting_transcript";
    private static final String DOCUMENT_TYPE_KNOWLEDGE = "meeting_knowledge";

    private final RagService ragService;
    private final AiDocumentChunkMapper documentChunkMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ChatGptMeetingKnowledgeSearchResultDTO> search(ChatGptMeetingKnowledgeSearchRequestDTO reqDTO) {
        RagChatResponse response = ragService.chat(RagChatRequest.builder()
                .knowledgeBaseId(reqDTO.getKnowledgeBaseId())
                .question(reqDTO.getQuery())
                .topK(reqDTO.getTopK())
                .webSearchEnabled(false)
                .build());
        if (response == null || response.getCitations() == null || response.getCitations().isEmpty()) {
            return List.of();
        }
        Map<String, ChatGptMeetingKnowledgeSearchResultDTO> results = new LinkedHashMap<>();
        for (RagChatCitation citation : response.getCitations()) {
            CitationMeetingMetadata metadata = resolveMeetingMetadata(citation, reqDTO);
            if (metadata.meetingId() == null) {
                continue;
            }
            String chunk = firstText(citation.getQuoteText(), metadata.chunkContent());
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            String documentType = firstText(metadata.documentType(), inferDocumentType(citation.getDocumentTitle()));
            ChatGptMeetingKnowledgeSearchResultDTO result = ChatGptMeetingKnowledgeSearchResultDTO.builder()
                    .meetingId(metadata.meetingId())
                    .knowledgeBaseId(reqDTO.getKnowledgeBaseId())
                    .documentType(documentType)
                    .chunk(chunk)
                    .score(citation.getScore())
                    .build();
            results.putIfAbsent(buildResultKey(result), result);
        }
        return List.copyOf(results.values());
    }

    private CitationMeetingMetadata resolveMeetingMetadata(RagChatCitation citation,
                                                           ChatGptMeetingKnowledgeSearchRequestDTO reqDTO) {
        Long meetingId = citation.getMeetingId();
        String documentType = citation.getDocumentType();
        String projectCode = citation.getProjectCode();
        String chunkContent = null;

        AiDocumentChunkDO chunk = selectTrustedChunk(citation, reqDTO);
        if (chunk != null) {
            chunkContent = chunk.getContent();
            Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
            meetingId = firstLong(meetingId, longValue(metadata, "meetingId", "meeting_id"));
            documentType = firstText(documentType, stringValue(metadata, "documentType", "document_type"));
            projectCode = firstText(projectCode, stringValue(metadata, "projectCode", "project_code"));
        }
        return new CitationMeetingMetadata(meetingId, documentType, projectCode, chunkContent);
    }

    private AiDocumentChunkDO selectTrustedChunk(RagChatCitation citation, ChatGptMeetingKnowledgeSearchRequestDTO reqDTO) {
        if (citation.getChunkId() == null) {
            return null;
        }
        AiDocumentChunkDO chunk = documentChunkMapper.selectById(citation.getChunkId());
        if (chunk == null || !Objects.equals(chunk.getTenantId(), reqDTO.getTenantId())
                || !Objects.equals(chunk.getKnowledgeBaseId(), reqDTO.getKnowledgeBaseId())) {
            return null;
        }
        if (citation.getDocumentId() != null && !Objects.equals(chunk.getDocumentId(), citation.getDocumentId())) {
            return null;
        }
        return chunk;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, METADATA_TYPE);
            return metadata == null ? Map.of() : metadata;
        } catch (Exception ex) {
            log.warn("[parseMetadata][meeting chunk metadata parse failed]", ex);
            return Map.of();
        }
    }

    private static Long firstLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private static Long longValue(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Long.valueOf(text.trim());
                } catch (NumberFormatException ignored) {
                    // Ignore malformed metadata values.
                }
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private static String inferDocumentType(String documentTitle) {
        if (!StringUtils.hasText(documentTitle)) {
            return DOCUMENT_TYPE_KNOWLEDGE;
        }
        String normalized = documentTitle.toLowerCase();
        if (normalized.contains("minutes") || normalized.contains("\u7EAA\u8981")) {
            return DOCUMENT_TYPE_MINUTES;
        }
        if (normalized.contains("transcript") || normalized.contains("\u8F6C\u5F55")) {
            return DOCUMENT_TYPE_TRANSCRIPT;
        }
        return DOCUMENT_TYPE_KNOWLEDGE;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String buildResultKey(ChatGptMeetingKnowledgeSearchResultDTO result) {
        return result.getMeetingId() + "|" + result.getDocumentType() + "|" + result.getChunk();
    }

    private record CitationMeetingMetadata(Long meetingId, String documentType, String projectCode,
                                           String chunkContent) {
    }

}
