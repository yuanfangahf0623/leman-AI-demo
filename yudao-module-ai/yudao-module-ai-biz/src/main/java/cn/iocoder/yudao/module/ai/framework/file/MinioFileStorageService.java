package cn.iocoder.yudao.module.ai.framework.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static cn.iocoder.yudao.module.ai.enums.AiDocumentErrorCodeConstants.DOCUMENT_FILE_STORAGE_FAILED;

/**
 * MinIO-backed document storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorageService implements DocumentFileStorageBackend {

    private final AiProperties aiProperties;

    private final Object bucketLock = new Object();

    private volatile boolean bucketReady;

    @Override
    public FileStorageResult store(String objectKey, byte[] content) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        if (content == null || content.length == 0) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件内容为空");
        }
        AiProperties.MinioProperties minio = getMinioProperties();
        MinioClient minioClient = buildClient(minio);
        String bucket = minio.getBucket().trim();
        try {
            ensureBucket(minioClient, bucket, minio);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedObjectKey)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .contentType(detectContentType(normalizedObjectKey))
                    .build());
            log.info("MinIO file stored, endpoint={}, bucket={}, objectKey={}, size={}",
                    sanitizeEndpoint(minio.getEndpoint()), bucket, normalizedObjectKey, content.length);
            return new FileStorageResult(normalizedObjectKey, buildSourceUri(minio, bucket, normalizedObjectKey));
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("MinIO file store failed, endpoint={}, bucket={}, objectKey={}, size={}, errorType={}",
                    sanitizeEndpoint(minio.getEndpoint()), bucket, normalizedObjectKey, content.length,
                    ex.getClass().getSimpleName());
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件存储失败");
        }
    }

    @Override
    public InputStream load(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        AiProperties.MinioProperties minio = getMinioProperties();
        MinioClient minioClient = buildClient(minio);
        String bucket = minio.getBucket().trim();
        try {
            ensureBucket(minioClient, bucket, minio);
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedObjectKey)
                    .build());
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("MinIO file load failed, endpoint={}, bucket={}, objectKey={}, errorType={}",
                    sanitizeEndpoint(minio.getEndpoint()), bucket, normalizedObjectKey, ex.getClass().getSimpleName());
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "读取文件失败");
        }
    }

    private void ensureBucket(MinioClient minioClient, String bucket, AiProperties.MinioProperties minio) throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (bucketLock) {
            if (bucketReady) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build());
                log.info("MinIO bucket created, endpoint={}, bucket={}", sanitizeEndpoint(minio.getEndpoint()), bucket);
            }
            bucketReady = true;
        }
    }

    private MinioClient buildClient(AiProperties.MinioProperties minio) {
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().trim())
                .credentials(minio.getAccessKey().trim(), minio.getSecretKey().trim())
                .build();
    }

    private AiProperties.MinioProperties getMinioProperties() {
        AiProperties.MinioProperties minio = aiProperties.getDocument().getMinio();
        if (minio == null || isBlank(minio.getEndpoint()) || isBlank(minio.getBucket())
                || isBlank(minio.getAccessKey()) || isBlank(minio.getSecretKey())) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "MinIO 文件存储配置不完整");
        }
        return minio;
    }

    private String buildSourceUri(AiProperties.MinioProperties minio, String bucket, String objectKey) {
        String externalEndpoint = isBlank(minio.getExternalEndpoint()) ? minio.getEndpoint() : minio.getExternalEndpoint();
        return trimTrailingSlash(externalEndpoint.trim()) + "/" + bucket + "/" + encodeObjectKey(objectKey);
    }

    private String normalizeObjectKey(String objectKey) {
        if (isBlank(objectKey)) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件存储 Key 为空");
        }
        String normalized = objectKey.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.equals("..")
                || normalized.startsWith("../") || normalized.contains("/../")) {
            throw new ServiceException(DOCUMENT_FILE_STORAGE_FAILED, "文件存储 Key 非法");
        }
        return normalized;
    }

    private String encodeObjectKey(String objectKey) {
        String[] parts = objectKey.split("/");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    private String detectContentType(String objectKey) {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);
        if (lowerKey.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerKey.endsWith(".doc")) {
            return "application/msword";
        }
        if (lowerKey.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerKey.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lowerKey.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerKey.endsWith(".png")) {
            return "image/png";
        }
        if (lowerKey.endsWith(".jpg") || lowerKey.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerKey.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String sanitizeEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return "";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            URI sanitizedUri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
            return trimTrailingSlash(sanitizedUri.toString());
        } catch (Exception ex) {
            return "<invalid>";
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
