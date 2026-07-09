package cn.iocoder.yudao.module.ai.service.rfq.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Normalized email content for RFQ extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDTO {

    private String from;

    private String to;

    private String subject;

    private String bodyText;

    private List<Attachment> attachments;

    private LocalDateTime receivedTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {

        private String fileName;

        private String contentType;

        private Long size;

        private String text;

        @JsonIgnore
        private byte[] content;

    }

}
