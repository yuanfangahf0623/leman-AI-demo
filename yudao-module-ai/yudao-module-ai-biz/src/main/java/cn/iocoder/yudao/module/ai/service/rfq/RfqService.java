package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.RfqDO;
import cn.iocoder.yudao.module.ai.dal.mysql.RfqMapper;
import cn.iocoder.yudao.module.ai.enums.RfqConstants;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_CREATE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_STATUS_INVALID;

/**
 * RFQ core business service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqService {

    private static final List<String> STATUS_ORDER = List.of(
            RfqConstants.STATUS_NEW,
            RfqConstants.STATUS_ANALYZING,
            RfqConstants.STATUS_COSTING,
            RfqConstants.STATUS_QUOTED,
            RfqConstants.STATUS_SENT);

    private final RfqMapper rfqMapper;
    private final RfqTaskService rfqTaskService;
    private final ObjectMapper objectMapper;

    public boolean existsByMessageId(String messageId) {
        return StringUtils.hasText(messageId) && rfqMapper.selectByMessageId(messageId) != null;
    }

    public RfqDO getByMessageId(String messageId) {
        return StringUtils.hasText(messageId) ? rfqMapper.selectByMessageId(messageId) : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public RfqDO createRfq(EmailRawDTO rawEmail, EmailDTO emailDTO, HermesRfqDTO hermesRfq) {
        validateCreateRequest(rawEmail, hermesRfq);
        RfqDO oldRfq = rfqMapper.selectByMessageId(rawEmail.getMessageId());
        if (oldRfq != null) {
            return oldRfq;
        }
        RfqDO rfq = buildRfq(rawEmail, emailDTO, hermesRfq);
        try {
            rfqMapper.insert(rfq);
        } catch (DuplicateKeyException ignored) {
            return rfqMapper.selectByMessageId(rawEmail.getMessageId());
        } catch (Exception ex) {
            log.warn("RFQ create failed, tenantId={}, messageId={}, errorType={}",
                    resolveTenantId(rawEmail), rawEmail.getMessageId(), ex.getClass().getSimpleName());
            throw new ServiceException(RFQ_CREATE_FAILED, "RFQ create failed");
        }
        rfqTaskService.generateTasks(rfq, hermesRfq);
        return rfq;
    }

    @Transactional(rollbackFor = Exception.class)
    public void advanceStatus(Long rfqId, String targetStatus) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        RfqDO rfq = rfqMapper.selectByIdAndTenantId(rfqId, tenantId);
        if (rfq == null) {
            throw new ServiceException(RFQ_NOT_EXISTS, "RFQ does not exist");
        }
        validateStatusTransition(rfq.getStatus(), targetStatus);
        rfqMapper.updateStatusByIdAndTenantId(rfqId, tenantId, targetStatus);
    }

    private RfqDO buildRfq(EmailRawDTO rawEmail, EmailDTO emailDTO, HermesRfqDTO hermesRfq) {
        RfqDO rfq = new RfqDO();
        rfq.setTenantId(resolveTenantId(rawEmail));
        rfq.setCustomer(limit(hermesRfq.getCustomer(), 255));
        rfq.setProduct(limit(resolvePrimaryProduct(hermesRfq), 512));
        rfq.setProductsJson(toJson(hermesRfq.getProducts()));
        rfq.setStatus(RfqConstants.STATUS_NEW);
        rfq.setRiskScore(hermesRfq.getRiskScore());
        rfq.setConfidence(hermesRfq.getConfidence());
        rfq.setMessageId(limit(rawEmail.getMessageId(), 512));
        rfq.setSourceAccount(limit(rawEmail.getAccount(), 255));
        rfq.setEmailFrom(limit(rawEmail.getFrom(), 512));
        rfq.setEmailSubject(limit(firstText(rawEmail.getSubject(), emailDTO == null ? null : emailDTO.getSubject()), 512));
        rfq.setReceivedTime(rawEmail.getReceivedTime());
        rfq.setMissingInfoJson(toJson(hermesRfq.getMissingInfo()));
        rfq.setNextActionsJson(toJson(hermesRfq.getNextActions()));
        rfq.setRawResultJson(firstText(hermesRfq.getRawJson(), toJson(hermesRfq)));
        rfq.setDeleted(false);
        return rfq;
    }

    private void validateCreateRequest(EmailRawDTO rawEmail, HermesRfqDTO hermesRfq) {
        if (rawEmail == null || !StringUtils.hasText(rawEmail.getMessageId())) {
            throw new ServiceException(RFQ_CREATE_FAILED, "Email messageId is required");
        }
        if (hermesRfq == null || !Boolean.TRUE.equals(hermesRfq.getIsRfq())) {
            throw new ServiceException(RFQ_CREATE_FAILED, "Hermes result is not RFQ");
        }
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (!RfqConstants.RFQ_STATUSES.contains(targetStatus)) {
            throw new ServiceException(RFQ_STATUS_INVALID, "RFQ target status is invalid");
        }
        int currentIndex = STATUS_ORDER.indexOf(currentStatus);
        int targetIndex = STATUS_ORDER.indexOf(targetStatus);
        if (currentIndex < 0 || targetIndex < currentIndex || targetIndex > currentIndex + 1
                && !Objects.equals(currentStatus, targetStatus)) {
            throw new ServiceException(RFQ_STATUS_INVALID, "RFQ status transition is invalid");
        }
    }

    private Long resolveTenantId(EmailRawDTO rawEmail) {
        return rawEmail != null && rawEmail.getTenantId() != null ? rawEmail.getTenantId() : AiTenantContextHolder.getTenantId();
    }

    private String resolvePrimaryProduct(HermesRfqDTO hermesRfq) {
        if (hermesRfq.getProducts() == null || hermesRfq.getProducts().isEmpty()) {
            return null;
        }
        HermesRfqDTO.Product product = hermesRfq.getProducts().get(0);
        if (product == null) {
            return null;
        }
        return firstText(product.getName(), product.getRawText());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
