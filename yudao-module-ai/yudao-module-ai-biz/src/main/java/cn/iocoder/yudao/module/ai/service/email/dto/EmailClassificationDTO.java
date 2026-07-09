package cn.iocoder.yudao.module.ai.service.email.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Generic email classification result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailClassificationDTO {

    @JsonProperty("is_match")
    private Boolean match;

    @JsonProperty("classification_type")
    private String classificationType;

    private BigDecimal confidence;

    private String reason;

    @JsonProperty("risk_flags")
    private List<String> riskFlags;

    @JsonIgnore
    private String rawJson;

}
