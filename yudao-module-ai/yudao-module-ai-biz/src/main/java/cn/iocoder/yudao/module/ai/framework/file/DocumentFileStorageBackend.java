package cn.iocoder.yudao.module.ai.framework.file;

import java.io.InputStream;

/**
 * Backend implementation used by the configurable document file storage service.
 */
public interface DocumentFileStorageBackend {

    FileStorageResult store(String objectKey, byte[] content);

    InputStream load(String objectKey);

}
