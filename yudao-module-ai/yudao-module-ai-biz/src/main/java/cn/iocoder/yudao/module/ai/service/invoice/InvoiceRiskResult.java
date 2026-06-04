package cn.iocoder.yudao.module.ai.service.invoice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Invoice risk detection result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRiskResult {

    private String riskLevel;
    private List<String> riskFlags = new ArrayList<>();
    private String riskSummary;

    public boolean hasRiskFlag(String riskFlag) {
        return riskFlags != null && riskFlags.contains(riskFlag);
    }

}
