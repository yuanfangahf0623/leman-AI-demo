package cn.iocoder.yudao.module.ai.service.invoice;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Unified invoice file source. Manual upload and future email collection both use this entry object.
 */
@Data
@Builder
public class FinanceInvoiceSourceFile {

    private String sourceType;
    private String sourceMessageId;
    private String sourceSender;
    private LocalDateTime sourceReceivedTime;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private byte[] content;
    private String remark;

}
