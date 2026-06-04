package cn.iocoder.yudao.module.ai.service.invoice;

/**
 * Finance invoice AI recognition service.
 */
public interface FinanceInvoiceAiService {

    InvoiceAiResult recognizeInvoice(Long invoiceId, String fileUrl, String fileType, byte[] fileContent);

}
