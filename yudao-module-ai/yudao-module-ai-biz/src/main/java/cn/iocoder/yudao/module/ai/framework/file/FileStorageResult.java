package cn.iocoder.yudao.module.ai.framework.file;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * File storage result.
 */
@Data
@AllArgsConstructor
public class FileStorageResult {

    private String objectKey;

    private String sourceUri;

}
