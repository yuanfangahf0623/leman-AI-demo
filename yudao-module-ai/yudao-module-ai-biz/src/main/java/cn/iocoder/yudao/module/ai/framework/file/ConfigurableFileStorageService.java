package cn.iocoder.yudao.module.ai.framework.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Runtime switchable file storage service for AI documents.
 */
@Service
@RequiredArgsConstructor
public class ConfigurableFileStorageService implements FileStorageService {

    private final AiDocumentStorageConfigService storageConfigService;
    private final MinioFileStorageService minioFileStorageService;
    private final LocalFileStorageService localFileStorageService;

    @Override
    public FileStorageResult store(String objectKey, byte[] content) {
        return getBackend().store(objectKey, content);
    }

    @Override
    public InputStream load(String objectKey) {
        DocumentFileStorageBackend primaryBackend = getBackend();
        try {
            return primaryBackend.load(objectKey);
        } catch (ServiceException ex) {
            return getFallbackBackend(primaryBackend).load(objectKey);
        }
    }

    private DocumentFileStorageBackend getBackend() {
        if (AiDocumentStorageConfigService.STORAGE_TYPE_LOCAL.equals(storageConfigService.getStorageType())) {
            return localFileStorageService;
        }
        return minioFileStorageService;
    }

    private DocumentFileStorageBackend getFallbackBackend(DocumentFileStorageBackend primaryBackend) {
        if (primaryBackend == localFileStorageService) {
            return minioFileStorageService;
        }
        return localFileStorageService;
    }

}
