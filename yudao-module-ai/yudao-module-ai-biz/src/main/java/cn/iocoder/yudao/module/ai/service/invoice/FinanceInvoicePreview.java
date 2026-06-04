package cn.iocoder.yudao.module.ai.service.invoice;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/**
 * Invoice attachment preview.
 */
@Data
@AllArgsConstructor
public class FinanceInvoicePreview {

    private String fileName;
    private String contentType;
    private InputStream inputStream;

}
