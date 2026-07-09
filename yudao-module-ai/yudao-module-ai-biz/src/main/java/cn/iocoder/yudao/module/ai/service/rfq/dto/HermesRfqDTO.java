package cn.iocoder.yudao.module.ai.service.rfq.dto;

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
 * Strict Hermes RFQ extraction result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HermesRfqDTO {

    @JsonProperty("is_rfq")
    private Boolean isRfq;

    private BigDecimal confidence;

    private String customer;

    private List<Product> products;

    @JsonProperty("risk_score")
    private BigDecimal riskScore;

    @JsonProperty("missing_info")
    private List<String> missingInfo;

    @JsonProperty("next_actions")
    private List<String> nextActions;

    @JsonIgnore
    private String rawJson;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {

        private String name;

        private String quantity;

        private String unit;

        private String specifications;

        @JsonProperty("raw_text")
        private String rawText;

    }

}
