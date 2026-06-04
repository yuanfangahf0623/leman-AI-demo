package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.module.ai.enums.ChunkStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentEmbeddingStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentParseStatusEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.parser.ParsedDocument;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVector;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeVectorStore;
import cn.iocoder.yudao.module.ai.service.chunk.ChunkService;
import cn.iocoder.yudao.module.ai.service.embedding.AiEmbeddingService;
import cn.iocoder.yudao.module.ai.service.meeting.dto.MeetingKnowledgeDocumentDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingKnowledgeIngestService {

    private static final String DEFAULT_DOCUMENT_VERSION = "v1";
    private static final int DEFAULT_EMBEDDING_BATCH_SIZE = 32;
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper documentChunkMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkService chunkService;
    private final AiEmbeddingService aiEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public Long upsert(MeetingKnowledgeDocumentDTO request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            return null;
        }
        AiKnowledgeBaseDO knowledgeBase = validateKnowledgeBase(request.getTenantId(), request.getKnowledgeBaseId());
        String title = normalizeTitle(request.getTitle());
        byte[] content = request.getContent().getBytes(StandardCharsets.UTF_8);
        String sourceUri = sourceUri(request);
        String contentHash = sha256Hex(content);
        AiDocumentDO oldDocument = documentMapper.selectBySourceUri(request.getTenantId(),
                request.getKnowledgeBaseId(), sourceUri);
        if (oldDocument != null && contentHash.equals(oldDocument.getContentHash())) {
            return oldDocument.getId();
        }

        FileStorageResult storageResult = fileStorageService.store(objectKey(request), content);
        AiDocumentDO document = buildDocument(request, title, content.length, sourceUri, contentHash, storageResult);
        if (oldDocument == null) {
            documentMapper.insert(document);
        } else {
            document.setId(oldDocument.getId());
            documentMapper.updateSyncDocumentByIdAndTenantId(document, request.getTenantId());
            knowledgeVectorStore.deleteByDocumentId(oldDocument.getId());
        }
        reindexDocument(knowledgeBase, document, request);
        return document.getId();
    }

    private void reindexDocument(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document,
                                 MeetingKnowledgeDocumentDTO request) {
        Map<String, Object> meetingMetadata = buildMeetingMetadata(request, document);
        ParsedDocument parsedDocument = new ParsedDocument(document.getTitle(), request.getContent(),
                new LinkedHashMap<>(meetingMetadata));
        List<AiDocumentChunkDO> chunks = chunkService.recreateChunks(knowledgeBase, document, parsedDocument);
        for (AiDocumentChunkDO chunk : chunks) {
            Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
            metadata.putAll(meetingMetadata);
            chunk.setMetadataJson(toJson(metadata));
            documentChunkMapper.updateById(chunk);
        }
        documentMapper.updateParseStatusByIdAndTenantId(document.getId(), document.getTenantId(),
                DocumentParseStatusEnum.SUCCESS.getCode(), null);
        embedDocument(knowledgeBase, document, chunks);
    }

    private void embedDocument(AiKnowledgeBaseDO knowledgeBase, AiDocumentDO document, List<AiDocumentChunkDO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            documentMapper.updateEmbeddingStatusByIdAndTenantId(document.getId(), document.getTenantId(),
                    DocumentEmbeddingStatusEnum.FAILED.getCode(), "No chunks to embed");
            throw new ServiceException(500, "Meeting knowledge document has no chunks");
        }
        documentMapper.updateEmbeddingStatusByIdAndTenantId(document.getId(), document.getTenantId(),
                DocumentEmbeddingStatusEnum.RUNNING.getCode(), null);
        String embeddingModel = resolveEmbeddingModel(knowledgeBase);
        int batchSize = resolveEmbeddingBatchSize();
        try {
            for (int from = 0; from < chunks.size(); from += batchSize) {
                List<AiDocumentChunkDO> batchChunks = chunks.subList(from, Math.min(from + batchSize, chunks.size()));
                List<List<Double>> embeddings = aiEmbeddingService.embedBatch(batchChunks.stream()
                        .map(AiDocumentChunkDO::getContent).toList());
                if (embeddings == null || embeddings.size() != batchChunks.size()) {
                    throw new ServiceException(500, "Embedding result count mismatch");
                }
                List<KnowledgeVector> vectors = new ArrayList<>(batchChunks.size());
                for (int i = 0; i < batchChunks.size(); i++) {
                    AiDocumentChunkDO chunk = batchChunks.get(i);
                    String vectorId = vectorId(document, chunk);
                    vectors.add(KnowledgeVector.builder()
                            .vectorId(vectorId)
                            .tenantId(document.getTenantId())
                            .knowledgeBaseId(document.getKnowledgeBaseId())
                            .documentId(document.getId())
                            .chunkId(chunk.getId())
                            .chunkNo(chunk.getChunkIndex())
                            .content(chunk.getContent())
                            .embedding(embeddings.get(i))
                            .metadata(vectorMetadata(chunk, embeddingModel))
                            .build());
                    documentChunkMapper.updateEmbeddingSuccessByIdAndTenantId(chunk.getId(), document.getTenantId(),
                            vectorId, embeddingModel, ChunkStatusEnum.SUCCESS.getCode());
                }
                knowledgeVectorStore.upsert(vectors);
            }
            documentMapper.updateEmbeddingStatusByIdAndTenantId(document.getId(), document.getTenantId(),
                    DocumentEmbeddingStatusEnum.SUCCESS.getCode(), null);
        } catch (Exception ex) {
            documentMapper.updateEmbeddingStatusByIdAndTenantId(document.getId(), document.getTenantId(),
                    DocumentEmbeddingStatusEnum.FAILED.getCode(), safeError(ex));
            documentChunkMapper.updateEmbeddingFailedByDocumentIdAndTenantId(document.getId(),
                    document.getKnowledgeBaseId(), document.getTenantId(), ChunkStatusEnum.ERROR.getCode());
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ServiceException(500, "Meeting document embedding failed");
        }
    }

    private AiDocumentDO buildDocument(MeetingKnowledgeDocumentDTO request, String title, long fileSize,
                                       String sourceUri, String contentHash, FileStorageResult storageResult) {
        AiDocumentDO document = new AiDocumentDO();
        document.setTenantId(request.getTenantId());
        document.setKnowledgeBaseId(request.getKnowledgeBaseId());
        document.setDirectoryId(normalizeDirectoryId(request.getDirectoryId()));
        document.setTitle(title);
        document.setFileName(safeFileName(title) + ".md");
        document.setFileType("md");
        document.setFileSize(fileSize);
        document.setObjectKey(storageResult.getObjectKey());
        document.setSourceUri(sourceUri);
        document.setContentHash(contentHash);
        document.setDocumentVersion(DEFAULT_DOCUMENT_VERSION);
        document.setParseStatus(DocumentParseStatusEnum.PENDING.getCode());
        document.setEmbeddingStatus(DocumentEmbeddingStatusEnum.PENDING.getCode());
        document.setChunkCount(0);
        document.setTokenCount(0);
        document.setErrorMessage(null);
        return document;
    }

    private Map<String, Object> buildMeetingMetadata(MeetingKnowledgeDocumentDTO request, AiDocumentDO document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", request.getTenantId());
        metadata.put("knowledgeBaseId", request.getKnowledgeBaseId());
        metadata.put("meetingId", request.getMeetingId());
        metadata.put("sourceMeetingId", request.getSourceMeetingId());
        metadata.put("documentType", request.getDocumentType());
        metadata.put("projectCode", request.getProjectCode());
        metadata.put("sensitivityLevel", request.getSensitivityLevel());
        metadata.put("meetingStartTime", request.getMeetingStartTime() == null ? null : request.getMeetingStartTime().toString());
        metadata.put("documentTitle", document.getTitle());
        metadata.put("sourceType", "TEAMS");
        return metadata;
    }

    private Map<String, Object> vectorMetadata(AiDocumentChunkDO chunk, String embeddingModel) {
        Map<String, Object> metadata = parseMetadata(chunk.getMetadataJson());
        metadata.put("embeddingModel", embeddingModel);
        metadata.put("contentHash", chunk.getContentHash());
        metadata.put("tokenCount", chunk.getTokenCount());
        return metadata;
    }

    private AiKnowledgeBaseDO validateKnowledgeBase(Long tenantId, Long knowledgeBaseId) {
        AiKnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectByIdAndTenantId(knowledgeBaseId, tenantId);
        if (knowledgeBase == null) {
            throw new ServiceException(404, "Knowledge base not found");
        }
        return knowledgeBase;
    }

    private String sourceUri(MeetingKnowledgeDocumentDTO request) {
        return "teams://meeting/" + request.getSourceMeetingId() + "/" + request.getDocumentType();
    }

    private String objectKey(MeetingKnowledgeDocumentDTO request) {
        return "ai/meeting/" + request.getTenantId() + "/" + request.getKnowledgeBaseId() + "/"
                + request.getSourceMeetingId() + "/" + request.getDocumentType() + "-" + UUID.randomUUID() + ".md";
    }

    private String resolveEmbeddingModel(AiKnowledgeBaseDO knowledgeBase) {
        if (StringUtils.hasText(knowledgeBase.getEmbeddingModel())) {
            return knowledgeBase.getEmbeddingModel().trim();
        }
        return aiProperties.getModel().getEmbeddingModel();
    }

    private int resolveEmbeddingBatchSize() {
        Integer batchSize = aiProperties.getDocument().getEmbeddingBatchSize();
        return batchSize == null || batchSize <= 0 ? DEFAULT_EMBEDDING_BATCH_SIZE : batchSize;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(metadataJson, METADATA_TYPE));
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(500, "Meeting chunk metadata serialize failed");
        }
    }

    private Long normalizeDirectoryId(Long directoryId) {
        return directoryId == null || directoryId <= 0 ? null : directoryId;
    }

    private String normalizeTitle(String title) {
        String normalized = StringUtils.hasText(title) ? title.trim() : "Teams meeting";
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String safeFileName(String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").trim();
        return safeName.isBlank() ? "teams-meeting" : (safeName.length() > 120 ? safeName.substring(0, 120) : safeName);
    }

    private String vectorId(AiDocumentDO document, AiDocumentChunkDO chunk) {
        return document.getTenantId() + ":" + document.getKnowledgeBaseId() + ":" + document.getId() + ":"
                + chunk.getId();
    }

    private String sha256Hex(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(500, "Meeting document hash failed");
        }
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }

}
