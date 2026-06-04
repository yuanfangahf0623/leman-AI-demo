package cn.iocoder.yudao.module.ai.service.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock invoice recognition service. Replace with real OCR/LLM adapter later.
 */
@Service
@RequiredArgsConstructor
public class MockFinanceInvoiceAiService implements FinanceInvoiceAiService {

    private final ObjectMapper objectMapper;

    @Override
    public InvoiceAiResult recognizeInvoice(Long invoiceId, String fileUrl, String fileType) {
        int scenario = invoiceId == null ? 1 : (int) ((invoiceId - 1) % 3) + 1;
        InvoiceAiResult result = switch (scenario) {
            case 2 -> abcServiceInvoice();
            case 3 -> dhlDuplicateInvoice();
            default -> dhlLogisticsInvoice();
        };
        result.setRawJson(toRawJson(result, fileUrl, fileType, "mock"));
        return result;
    }

    private InvoiceAiResult dhlLogisticsInvoice() {
        InvoiceAiResult result = new InvoiceAiResult();
        result.setSupplierName("DHL Logistics GmbH");
        result.setInvoiceNo("INV-2026-001");
        result.setInvoiceDate(LocalDateTime.of(2026, 5, 10, 0, 0));
        result.setDueDate(LocalDateTime.of(2026, 6, 9, 0, 0));
        result.setCurrency("EUR");
        result.setNetAmount(new BigDecimal("2300.00"));
        result.setVatAmount(new BigDecimal("437.00"));
        result.setGrossAmount(new BigDecimal("2737.00"));
        result.setIban("DE00000000000000000000");
        result.setBic("COBADEFFXXX");
        result.setPaymentAccountName("DHL Logistics GmbH");
        result.setExpenseCategory("LOGISTICS");
        result.setBusinessDesc("Germany logistics service fee");
        result.setPoNo("PO-2026-001");
        result.setConfidence(new BigDecimal("0.91"));
        result.setSummary("本发票来自 DHL Logistics GmbH，发票号 INV-2026-001，含税金额 EUR 2,737.00，费用类别为物流。识别到 PO，未识别到合同号。");
        return result;
    }

    private InvoiceAiResult abcServiceInvoice() {
        InvoiceAiResult result = new InvoiceAiResult();
        result.setSupplierName("ABC Service GmbH");
        result.setInvoiceNo("RE-2026-045");
        result.setInvoiceDate(LocalDateTime.of(2026, 5, 18, 0, 0));
        result.setDueDate(LocalDateTime.of(2026, 6, 17, 0, 0));
        result.setCurrency("EUR");
        result.setNetAmount(new BigDecimal("6554.62"));
        result.setVatAmount(new BigDecimal("1245.38"));
        result.setGrossAmount(new BigDecimal("7800.00"));
        result.setIban("DE11111111111111111111");
        result.setBic("COBADEFFXXX");
        result.setPaymentAccountName("ABC Service GmbH");
        result.setExpenseCategory("SERVICE");
        result.setBusinessDesc("Consulting service fee");
        result.setConfidence(new BigDecimal("0.88"));
        result.setSummary("本发票来自 ABC Service GmbH，发票号 RE-2026-045，含税金额 EUR 7,800.00，未识别到 PO 和合同号。");
        return result;
    }

    private InvoiceAiResult dhlDuplicateInvoice() {
        InvoiceAiResult result = dhlLogisticsInvoice();
        result.setConfidence(new BigDecimal("0.90"));
        return result;
    }

    private String toRawJson(InvoiceAiResult result, String fileUrl, String fileType, String provider) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", provider);
        raw.put("fileUrl", fileUrl);
        raw.put("fileType", fileType);
        raw.put("supplierName", result.getSupplierName());
        raw.put("invoiceNo", result.getInvoiceNo());
        raw.put("currency", result.getCurrency());
        raw.put("grossAmount", result.getGrossAmount());
        raw.put("confidence", result.getConfidence());
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

}
