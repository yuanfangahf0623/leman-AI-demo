package cn.iocoder.yudao.module.ai.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncRecordMapper;
import cn.iocoder.yudao.module.ai.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentEmbeddingStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentParseStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncJobStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncRecordActionTypeEnum;
import cn.iocoder.yudao.module.ai.enums.SyncRecordStatusEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.datasource.AiDataSourceService;
import cn.iocoder.yudao.module.ai.service.document.AiDocumentService;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_MISMATCH;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_EXECUTE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiSyncJobErrorCodeConstants.SYNC_JOB_RUNNING;

/**
 * AI 知识库同步 Service 实现。
 *
 * <p>第一阶段只执行 FILE 数据源同步：从配置读取文件列表或目录，按内容 hash 判断新增、更新或跳过。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeSyncServiceImpl implements KnowledgeSyncService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "doc", "docx", "wps",
            "xls", "xlsx", "xlsb", "ppt", "pptx", "pptm");
    private static final Integer DEFAULT_COUNT = 0;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;

    private final AiSyncJobMapper syncJobMapper;
    private final AiSyncRecordMapper syncRecordMapper;
    private final AiDocumentMapper documentMapper;
    private final AiKnowledgeService knowledgeService;
    private final AiDataSourceService dataSourceService;
    private final AiDocumentService documentService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Override
    public void executeSyncJob(Long jobId) {
        long startNanos = System.nanoTime();
        Long tenantId = AiTenantContextHolder.getTenantId();
        AiSyncJobDO syncJob = validateSyncJobExists(jobId, tenantId);
        validateJobNotRunning(syncJob);

        SyncStats stats = new SyncStats();
        syncJobMapper.updateRunningByIdAndTenantId(jobId, tenantId, SyncJobStatusEnum.RUNNING.getCode(),
                LocalDateTime.now());
        try {
            validateKnowledgeExists(syncJob.getKnowledgeBaseId());
            AiDataSourceDO dataSource = validateDataSourceExists(syncJob.getDataSourceId());
            validateDataSource(syncJob, dataSource);

            List<Path> files = resolveSyncFiles(dataSource.getConfigJson());
            for (Path file : files) {
                syncOneFile(syncJob, dataSource, file, stats);
            }
            Integer finalStatus = stats.failCount > 0 ? SyncJobStatusEnum.FAILED.getCode()
                    : SyncJobStatusEnum.SUCCESS.getCode();
            String errorMessage = stats.failCount > 0 ? "部分文件同步失败" : null;
            syncJobMapper.updateResultByIdAndTenantId(jobId, tenantId, stats.totalCount, stats.successCount,
                    stats.failCount, finalStatus, LocalDateTime.now(), errorMessage);
            log.info("FILE 数据源同步完成, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, total={}, success={}, fail={}, elapsedMs={}",
                    syncJob.getId(), tenantId, syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    stats.totalCount, stats.successCount, stats.failCount, elapsedMillis(startNanos));
        } catch (Exception ex) {
            String errorMessage = toSafeErrorMessage(ex, null);
            syncJobMapper.updateResultByIdAndTenantId(jobId, tenantId, stats.totalCount, stats.successCount,
                    stats.failCount, SyncJobStatusEnum.FAILED.getCode(), LocalDateTime.now(), errorMessage);
            log.warn("FILE 数据源同步失败, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, total={}, success={}, fail={}, elapsedMs={}, reason={}",
                    syncJob.getId(), tenantId, syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    stats.totalCount, stats.successCount, stats.failCount, elapsedMillis(startNanos), errorMessage);
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "同步任务执行失败");
        }
    }

    private void syncOneFile(AiSyncJobDO syncJob, AiDataSourceDO dataSource, Path file, SyncStats stats) {
        stats.totalCount++;
        LocalDateTime recordStartTime = LocalDateTime.now();
        String sourceUri = toSourceUri(file);
        String actionType = SyncRecordActionTypeEnum.ERROR.getCode();
        Long documentId = null;
        try {
            validateRegularReadableFile(file);
            String fileName = sanitizeFileName(file.getFileName().toString());
            String extension = getSupportedExtension(fileName);
            long fileSize = Files.size(file);
            validateFileSize(fileSize);
            byte[] content = Files.readAllBytes(file);
            String contentHash = sha256Hex(content);

            AiDocumentDO oldDocument = documentMapper.selectBySourceUri(syncJob.getTenantId(),
                    syncJob.getKnowledgeBaseId(), dataSource.getId(), sourceUri);
            if (oldDocument != null && contentHash.equals(oldDocument.getContentHash())) {
                actionType = SyncRecordActionTypeEnum.SKIP.getCode();
                documentId = oldDocument.getId();
                stats.successCount++;
                insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.SUCCESS.getCode(),
                        recordStartTime, null);
                return;
            }

            FileStorageResult storageResult = fileStorageService.store(buildObjectKey(syncJob, extension), content);
            if (oldDocument == null) {
                actionType = SyncRecordActionTypeEnum.CREATE.getCode();
                documentId = createDocument(syncJob, dataSource, fileName, extension, fileSize, sourceUri, contentHash,
                        storageResult);
            } else {
                actionType = SyncRecordActionTypeEnum.UPDATE.getCode();
                documentId = updateDocument(syncJob, oldDocument, fileName, extension, fileSize, sourceUri, contentHash,
                        storageResult);
            }
            // 只有新增或更新的文档才自动进入解析和向量化；未变化文件保持 SKIP，不重复处理。
            parseAndEmbedDocument(documentId);
            stats.successCount++;
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.SUCCESS.getCode(),
                    recordStartTime, null);
        } catch (Exception ex) {
            stats.failCount++;
            String errorMessage = toSafeErrorMessage(ex, file);
            insertSyncRecord(syncJob, documentId, sourceUri, actionType, SyncRecordStatusEnum.FAILED.getCode(),
                    recordStartTime, errorMessage);
            log.warn("FILE 数据源单文件同步失败, syncJobId={}, tenantId={}, knowledgeBaseId={}, dataSourceId={}, actionType={}, reason={}",
                    syncJob.getId(), syncJob.getTenantId(), syncJob.getKnowledgeBaseId(), syncJob.getDataSourceId(),
                    actionType, errorMessage);
        }
    }

    private Long createDocument(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String fileName, String extension,
                                long fileSize, String sourceUri, String contentHash,
                                FileStorageResult storageResult) {
        AiDocumentDO document = buildDocument(syncJob, dataSource, fileName, extension, fileSize, sourceUri,
                contentHash, storageResult);
        documentMapper.insert(document);
        return document.getId();
    }

    private Long updateDocument(AiSyncJobDO syncJob, AiDocumentDO oldDocument, String fileName, String extension,
                                long fileSize, String sourceUri, String contentHash,
                                FileStorageResult storageResult) {
        AiDocumentDO updateObj = buildDocument(syncJob, null, fileName, extension, fileSize, sourceUri, contentHash,
                storageResult);
        updateObj.setId(oldDocument.getId());
        updateObj.setDataSourceId(oldDocument.getDataSourceId());
        documentMapper.updateSyncDocumentByIdAndTenantId(updateObj, syncJob.getTenantId());
        return oldDocument.getId();
    }

    private void parseAndEmbedDocument(Long documentId) {
        if (documentId == null) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "同步文档编号为空");
        }
        documentService.parseDocument(documentId);
        documentService.embedDocument(documentId);
    }

    private AiDocumentDO buildDocument(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String fileName, String extension,
                                       long fileSize, String sourceUri, String contentHash,
                                       FileStorageResult storageResult) {
        AiDocumentDO document = new AiDocumentDO();
        document.setTenantId(syncJob.getTenantId());
        document.setKnowledgeBaseId(syncJob.getKnowledgeBaseId());
        document.setDataSourceId(dataSource != null ? dataSource.getId() : syncJob.getDataSourceId());
        document.setTitle(removeExtension(fileName));
        document.setFileName(fileName);
        document.setFileType(extension);
        document.setFileSize(fileSize);
        document.setObjectKey(storageResult.getObjectKey());
        document.setSourceUri(sourceUri);
        document.setContentHash(contentHash);
        document.setParseStatus(DocumentParseStatusEnum.PENDING.getCode());
        document.setEmbeddingStatus(DocumentEmbeddingStatusEnum.PENDING.getCode());
        document.setChunkCount(DEFAULT_COUNT);
        document.setTokenCount(DEFAULT_COUNT);
        document.setErrorMessage(null);
        return document;
    }

    private void insertSyncRecord(AiSyncJobDO syncJob, Long documentId, String sourceUri, String actionType,
                                  Integer status, LocalDateTime startTime, String errorMessage) {
        AiSyncRecordDO record = new AiSyncRecordDO();
        record.setTenantId(syncJob.getTenantId());
        record.setSyncJobId(syncJob.getId());
        record.setKnowledgeBaseId(syncJob.getKnowledgeBaseId());
        record.setDataSourceId(syncJob.getDataSourceId());
        record.setDocumentId(documentId);
        record.setSourceUri(sourceUri);
        record.setActionType(actionType);
        record.setStatus(status);
        record.setStartTime(startTime);
        record.setEndTime(LocalDateTime.now());
        record.setErrorMessage(errorMessage);
        syncRecordMapper.insert(record);
    }

    private AiSyncJobDO validateSyncJobExists(Long jobId, Long tenantId) {
        AiSyncJobDO syncJob = syncJobMapper.selectByIdAndTenantId(jobId, tenantId);
        if (syncJob == null) {
            throw new ServiceException(SYNC_JOB_NOT_EXISTS, "同步任务不存在");
        }
        return syncJob;
    }

    private void validateJobNotRunning(AiSyncJobDO syncJob) {
        if (SyncJobStatusEnum.RUNNING.getCode().equals(syncJob.getStatus())) {
            throw new ServiceException(SYNC_JOB_RUNNING, "同步任务正在执行");
        }
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
        if (knowledgeService.getKnowledge(knowledgeBaseId) == null) {
            throw new ServiceException(SYNC_JOB_KNOWLEDGE_NOT_EXISTS, "知识库不存在");
        }
    }

    private AiDataSourceDO validateDataSourceExists(Long dataSourceId) {
        AiDataSourceDO dataSource = dataSourceService.getDataSource(dataSourceId);
        if (dataSource == null) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_NOT_EXISTS, "数据源不存在");
        }
        return dataSource;
    }

    private void validateDataSource(AiSyncJobDO syncJob, AiDataSourceDO dataSource) {
        if (!syncJob.getKnowledgeBaseId().equals(dataSource.getKnowledgeBaseId())) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_MISMATCH, "数据源不属于当前知识库");
        }
        if (!DataSourceTypeEnum.FILE.getCode().equals(dataSource.getType())) {
            throw new ServiceException(SYNC_JOB_DATA_SOURCE_TYPE_UNSUPPORTED, "第一阶段只支持 FILE 类型数据源同步");
        }
    }

    private List<Path> resolveSyncFiles(String configJson) {
        JsonNode config = parseConfig(configJson);
        LinkedHashMap<String, Path> files = new LinkedHashMap<>();
        addConfiguredFiles(config, files);
        addDirectoryFiles(config, files);
        if (files.isEmpty()) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源未配置可同步文件");
        }
        return new ArrayList<>(files.values());
    }

    private JsonNode parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置不能为空");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (config == null || !config.isObject()) {
                throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置格式非法");
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "FILE 数据源配置 JSON 非法");
        }
    }

    private void addConfiguredFiles(JsonNode config, LinkedHashMap<String, Path> files) {
        for (String key : List.of("files", "filePaths", "fileList")) {
            JsonNode node = config.get(key);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isTextual()) {
                addPath(files, node.asText());
                continue;
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    addPath(files, extractFilePath(item));
                }
            }
        }
    }

    private String extractFilePath(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isTextual()) {
            return item.asText();
        }
        if (item.isObject()) {
            JsonNode pathNode = item.get("path");
            if (pathNode == null || pathNode.isNull()) {
                pathNode = item.get("filePath");
            }
            return pathNode != null && pathNode.isTextual() ? pathNode.asText() : null;
        }
        return null;
    }

    private void addDirectoryFiles(JsonNode config, LinkedHashMap<String, Path> files) {
        String directory = firstText(config, "directory", "directoryPath", "rootPath");
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path rootPath = Paths.get(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步目录不存在或不可访问");
        }
        boolean recursive = config.path("recursive").asBoolean(false);
        Set<String> includeExtensions = resolveIncludeExtensions(config);
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(rootPath, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> includeExtensions.contains(getExtension(path.getFileName().toString())))
                    .forEach(path -> addPath(files, path.toString()));
        } catch (IOException ex) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "读取同步目录失败");
        }
    }

    private String firstText(JsonNode config, String... keys) {
        for (String key : keys) {
            JsonNode node = config.get(key);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private Set<String> resolveIncludeExtensions(JsonNode config) {
        JsonNode node = config.get("includeExtensions");
        if (node == null || node.isNull()) {
            return SUPPORTED_EXTENSIONS;
        }
        Set<String> extensions = new LinkedHashSet<>();
        if (node.isTextual()) {
            extensions.add(normalizeExtension(node.asText()));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    extensions.add(normalizeExtension(item.asText()));
                }
            }
        }
        extensions.remove("");
        return extensions.isEmpty() ? SUPPORTED_EXTENSIONS : extensions;
    }

    private void addPath(LinkedHashMap<String, Path> files, String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }
        Path path = Paths.get(pathValue).toAbsolutePath().normalize();
        files.put(path.toString(), path);
    }

    private void validateRegularReadableFile(Path file) {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件不存在或不可读取");
        }
    }

    private void validateFileSize(long fileSize) {
        Integer maxFileSizeMb = aiProperties.getDocument().getMaxFileSizeMb();
        long maxFileSizeBytes = maxFileSizeMb.longValue() * 1024L * 1024L;
        if (fileSize > maxFileSizeBytes) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件大小超过限制");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.contains("..")
                || hasControlChar(fileName)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件名非法");
        }
        return fileName;
    }

    private boolean hasControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String getSupportedExtension(String fileName) {
        String extension = getExtension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步文件类型不支持");
        }
        return extension;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return normalizeExtension(fileName.substring(dotIndex + 1));
    }

    private String normalizeExtension(String extension) {
        return extension == null ? "" : extension.trim().replace(".", "").toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(AiSyncJobDO syncJob, String extension) {
        return "ai/sync/" + syncJob.getTenantId() + "/" + syncJob.getKnowledgeBaseId() + "/"
                + syncJob.getDataSourceId() + "/" + UUID.randomUUID() + "." + extension;
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String toSourceUri(Path path) {
        return path.toAbsolutePath().normalize().toUri().toString();
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(SYNC_JOB_EXECUTE_FAILED, "计算文件哈希失败");
        }
    }

    private String toSafeErrorMessage(Exception ex, Path sourcePath) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        if (sourcePath != null) {
            String normalizedPath = sourcePath.toAbsolutePath().normalize().toString();
            message = message.replace(normalizedPath, "[file]");
            message = message.replace(sourcePath.toAbsolutePath().normalize().toUri().toString(), "[file]");
        }
        return abbreviate(message, ERROR_MESSAGE_MAX_LENGTH);
    }

    private String abbreviate(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static final class SyncStats {

        private int totalCount;
        private int successCount;
        private int failCount;

    }

}
