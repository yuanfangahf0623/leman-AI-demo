package cn.iocoder.yudao.module.ai.enums;

import java.util.Set;

/**
 * Finance invoice code constants.
 */
public final class FinanceInvoiceConstants {

    public static final String SOURCE_UPLOAD = "UPLOAD";
    public static final String SOURCE_EMAIL = "EMAIL";

    public static final String AI_STATUS_NOT_RECOGNIZED = "NOT_RECOGNIZED";
    public static final String AI_STATUS_RECOGNIZING = "RECOGNIZING";
    public static final String AI_STATUS_RECOGNIZED = "RECOGNIZED";
    public static final String AI_STATUS_FAILED = "FAILED";

    public static final String REVIEW_STATUS_PENDING = "PENDING";
    public static final String REVIEW_STATUS_CONFIRMED = "CONFIRMED";
    public static final String REVIEW_STATUS_REJECTED = "REJECTED";

    public static final String APPROVAL_STATUS_DRAFT = "DRAFT";
    public static final String APPROVAL_STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
    public static final String APPROVAL_STATUS_APPROVING = "APPROVING";
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    public static final String APPROVAL_STATUS_CANCELED = "CANCELED";

    public static final String BOOKKEEPING_STATUS_NOT_BOOKED = "NOT_BOOKED";
    public static final String BOOKKEEPING_STATUS_BOOKED = "BOOKED";

    public static final String PAYMENT_STATUS_NOT_PAID = "NOT_PAID";
    public static final String PAYMENT_STATUS_PAID = "PAID";

    public static final String RISK_LEVEL_NONE = "NONE";
    public static final String RISK_LEVEL_LOW = "LOW";
    public static final String RISK_LEVEL_MEDIUM = "MEDIUM";
    public static final String RISK_LEVEL_HIGH = "HIGH";

    public static final String RISK_FLAG_NEW_SUPPLIER = "NEW_SUPPLIER";
    public static final String RISK_FLAG_IBAN_CHANGED = "IBAN_CHANGED";
    public static final String RISK_FLAG_NO_PO = "NO_PO";
    public static final String RISK_FLAG_NO_CONTRACT = "NO_CONTRACT";
    public static final String RISK_FLAG_DUPLICATE_INVOICE = "DUPLICATE_INVOICE";
    public static final String RISK_FLAG_AMOUNT_OVER_LIMIT = "AMOUNT_OVER_LIMIT";
    public static final String RISK_FLAG_VAT_ABNORMAL = "VAT_ABNORMAL";

    public static final String ATTACHMENT_TYPE_INVOICE = "INVOICE";
    public static final String SUPPLIER_PAYMENT_STATUS_ENABLED = "ENABLED";

    public static final Set<String> UPLOAD_ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "tif", "tiff",
            "doc", "docx");
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "tif", "tiff");

    private FinanceInvoiceConstants() {
    }

}
