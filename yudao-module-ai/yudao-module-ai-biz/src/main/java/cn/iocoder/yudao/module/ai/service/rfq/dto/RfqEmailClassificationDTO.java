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
 * Structured RFQ email classification result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RfqEmailClassificationDTO {

    public static final String TYPE_NEW_RFQ = "NEW_RFQ";
    public static final String TYPE_RFQ_THREAD_REPLY = "RFQ_THREAD_REPLY";
    public static final String TYPE_NON_RFQ = "NON_RFQ";

    @JsonProperty("is_rfq")
    private Boolean isRfq;

    @JsonProperty("rfq_type")
    private String rfqType;

    private BigDecimal confidence;

    private String reason;

    @JsonProperty("risk_flags")
    private List<String> riskFlags;

    @JsonIgnore
    private String rawJson;

}
