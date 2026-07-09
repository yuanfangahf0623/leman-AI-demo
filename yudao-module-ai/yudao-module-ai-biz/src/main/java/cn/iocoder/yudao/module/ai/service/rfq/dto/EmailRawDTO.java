package cn.iocoder.yudao.module.ai.service.rfq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Raw email fetched from IMAP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRawDTO {

    private Long tenantId;

    private Long uid;

    private String messageId;

    private String from;

    private String to;

    private String subject;

    private LocalDateTime receivedTime;

    /**
     * Raw MIME content encoded with ISO-8859-1 for byte-preserving in-memory transport.
     */
    private String rawMime;

    private String account;

}
