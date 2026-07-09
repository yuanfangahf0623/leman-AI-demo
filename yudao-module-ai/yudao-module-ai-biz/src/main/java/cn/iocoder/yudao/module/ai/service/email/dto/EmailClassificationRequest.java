package cn.iocoder.yudao.module.ai.service.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic email classification request definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailClassificationRequest {

    private String model;

    private String systemPrompt;

    private List<String> allowedTypes;

    private List<String> positiveTypes;

    private String nonMatchType;

    private String callType;

    private Integer maxInputChars;

    private Integer maxTokens;

}
