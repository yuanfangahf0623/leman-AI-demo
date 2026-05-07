package cn.iocoder.yudao.module.ai.framework.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_STORAGE_FAILED;

/**
 * 本地文件存储实现，仅用于开发阶段。
 *
 * <p>生产环境建议替换为 MinIO 或项目统一文件模块实现。</p>
 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final AiProperties aiProperties;

    @Override
    public FileStorageResult store(String objectKey, byte[] content) {
        // basePath 来源于配置，统一规范化后再拼接 objectKey。
        Path basePath = Paths.get(aiProperties.getDocument().getStorageBasePath()).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(objectKey).normalize();
        // 防止 objectKey 中出现路径穿越导致文件写出存储根目录。
        if (!targetPath.startsWith(basePath)) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件存储路径非法");
        }
        try {
            // CREATE_NEW 避免覆盖已有文件；objectKey 使用 UUID，正常不会冲突。
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new FileStorageResult(objectKey, targetPath.toUri().toString());
        } catch (IOException ex) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件存储失败");
        }
    }

}
