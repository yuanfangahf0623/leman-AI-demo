package cn.iocoder.yudao.module.ai.service.document;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentChunkMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDocumentMapper;
import cn.iocoder.yudao.module.ai.enums.DocumentEmbeddingStatusEnum;
import cn.iocoder.yudao.module.ai.enums.DocumentParseStatusEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageResult;
import cn.iocoder.yudao.module.ai.framework.file.FileStorageService;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.knowledge.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_CONTENT_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_EMPTY;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_NAME_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_STORAGE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_TYPE_UNSUPPORTED;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_KNOWLEDGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_NOT_EXISTS;

/**
 * AI 文档 Service 实现。
 *
 * <p>负责文档上传编排：权限范围校验、文件安全校验、文件存储和 ai_document 记录创建。
 * 当前阶段不在请求链路内执行解析和向量化。</p>
 */
@Service
@RequiredArgsConstructor
public class AiDocumentServiceImpl implements AiDocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "pdf", "md");
    private static final int TEXT_CHECK_BYTES = 4096;
    private static final Integer DEFAULT_COUNT = 0;

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper documentChunkMapper;
    private final AiKnowledgeService knowledgeService;
    private final FileStorageService fileStorageService;
    private final AiProperties aiProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        // 先确认知识库属于当前租户，避免跨租户上传文档。
        validateKnowledgeExists(knowledgeBaseId);
        // 文件大小和内容校验必须在存储前完成，不能信任用户传入的文件名或 Content-Type。
        validateFileNotEmpty(file);
        validateFileSize(file);

        // 仅保留清洗后的文件名用于展示，实际存储路径由服务端生成。
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = getSupportedExtension(fileName);
        byte[] content = readFileContent(file);
        validateFileContent(extension, content);

        // 对象 Key 使用租户、知识库和 UUID 组成，避免路径穿越和文件名碰撞。
        Long tenantId = AiTenantContextHolder.getTenantId();
        String objectKey = buildObjectKey(tenantId, knowledgeBaseId, extension);
        FileStorageResult storageResult = fileStorageService.store(objectKey, content);

        // 只创建文档元数据记录，解析和向量化状态置为 PENDING，由后续异步任务处理。
        AiDocumentDO document = new AiDocumentDO();
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(removeExtension(fileName));
        document.setFileName(fileName);
        document.setFileType(extension);
        document.setFileSize((long) content.length);
        document.setObjectKey(storageResult.getObjectKey());
        document.setSourceUri(storageResult.getSourceUri());
        document.setContentHash(sha256Hex(content));
        document.setParseStatus(DocumentParseStatusEnum.PENDING.getCode());
        document.setEmbeddingStatus(DocumentEmbeddingStatusEnum.PENDING.getCode());
        document.setChunkCount(DEFAULT_COUNT);
        document.setTokenCount(DEFAULT_COUNT);
        documentMapper.insert(document);
        return document.getId();
    }

    @Override
    public PageResult<AiDocumentDO> getDocumentPage(AiDocumentPageReqVO pageReqVO) {
        // 如果指定知识库，则先校验当前租户是否可访问该知识库。
        if (pageReqVO.getKnowledgeBaseId() != null) {
            validateKnowledgeExists(pageReqVO.getKnowledgeBaseId());
        }
        return documentMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId());
    }

    @Override
    public AiDocumentDO getDocument(Long id) {
        AiDocumentDO document = validateDocumentExists(id);
        // 文档详情返回前再次校验知识库权限，避免只凭 documentId 越权访问。
        validateKnowledgeExists(document.getKnowledgeBaseId());
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        AiDocumentDO document = validateDocumentExists(id);
        // 删除文档前校验当前用户仍然有该知识库权限。
        validateKnowledgeExists(document.getKnowledgeBaseId());

        // 先逻辑删除文档，再同步逻辑删除切片；两者处于同一事务中。
        documentMapper.deleteByIdAndTenantId(id, document.getTenantId());
        documentChunkMapper.deleteByDocumentIdAndTenantId(id, document.getKnowledgeBaseId(), document.getTenantId());

        // TODO 后续接入 VectorStore 后，在异步任务中删除对应向量库数据。
    }

    private AiDocumentDO validateDocumentExists(Long id) {
        AiDocumentDO document = documentMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
        if (document == null) {
            throw new ServiceException(DOCUMENT_NOT_EXISTS, "文档不存在");
        }
        return document;
    }

    private void validateKnowledgeExists(Long knowledgeBaseId) {
        // 复用知识库查询的租户过滤能力，知识库不存在或无权限都按不可访问处理。
        if (knowledgeService.getKnowledge(knowledgeBaseId) == null) {
            throw new ServiceException(DOCUMENT_KNOWLEDGE_NOT_EXISTS, "知识库不存在或无权限访问");
        }
    }

    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(DOCUMENT_FILE_EMPTY, "上传文件不能为空");
        }
    }

    private void validateFileSize(MultipartFile file) {
        // 文件大小限制来源于 ai.document.max-file-size-mb，方便不同环境独立配置。
        Integer maxFileSizeMb = aiProperties.getDocument().getMaxFileSizeMb();
        long maxFileSizeBytes = maxFileSizeMb.longValue() * 1024L * 1024L;
        if (file.getSize() > maxFileSizeBytes) {
            throw new ServiceException(DOCUMENT_FILE_TOO_LARGE, "上传文件大小超过限制");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        // 只取 basename，并拒绝空文件名、路径穿越、控制字符和过长文件名。
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ServiceException(DOCUMENT_FILE_NAME_INVALID, "上传文件名不能为空");
        }
        String normalized = originalFilename.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.contains("..") || hasControlChar(fileName)) {
            throw new ServiceException(DOCUMENT_FILE_NAME_INVALID, "上传文件名非法");
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
        // 第一阶段只支持 txt、pdf、md，类型判断不依赖浏览器传入的 Content-Type。
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ServiceException(DOCUMENT_FILE_TYPE_UNSUPPORTED, "上传文件类型不支持");
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(DOCUMENT_FILE_TYPE_UNSUPPORTED, "上传文件类型不支持");
        }
        return extension;
    }

    private byte[] readFileContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "读取上传文件失败");
        }
    }

    private void validateFileContent(String extension, byte[] content) {
        // 扩展名通过后仍要做基础内容校验，降低伪装文件上传风险。
        if ("pdf".equals(extension)) {
            validatePdfHeader(content);
            return;
        }
        validateTextContent(content);
    }

    private void validatePdfHeader(byte[] content) {
        // PDF 至少需要符合 %PDF- 文件头，后续可接入更严格的文件解析器校验。
        if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new ServiceException(DOCUMENT_FILE_CONTENT_INVALID, "PDF 文件内容非法");
        }
    }

    private void validateTextContent(byte[] content) {
        // 文本类文件检查前几个 KB 是否包含 NUL 字节，避免明显二进制文件伪装为文本。
        int checkLength = Math.min(content.length, TEXT_CHECK_BYTES);
        for (int i = 0; i < checkLength; i++) {
            if (content[i] == 0) {
                throw new ServiceException(DOCUMENT_FILE_CONTENT_INVALID, "文本文件内容非法");
            }
        }
    }

    private String buildObjectKey(Long tenantId, Long knowledgeBaseId, String extension) {
        return "ai/document/" + tenantId + "/" + knowledgeBaseId + "/" + UUID.randomUUID() + "." + extension;
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String sha256Hex(byte[] content) {
        // 内容哈希用于后续重复文档识别、同步去重或审计追踪。
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException(DOCUMENT_FILE_CONTENT_INVALID, "计算文件哈希失败");
        }
    }

}
