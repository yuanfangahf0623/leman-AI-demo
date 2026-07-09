package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.EmailSyncStateDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.RfqDO;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.email.EmailAttachmentStorageService;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.RfqEmailClassificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_PARSE_FAILED;

/**
 * Entry bean for the existing scheduler module.
 */
@Slf4j
@Service("emailSyncScheduler")
@RequiredArgsConstructor
public class EmailSyncScheduler {

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AiRfqProperties properties;
    private final RfqMailboxAccountService mailboxAccountService;
    private final ImapEmailSyncService imapEmailSyncService;
    private final EmlParseService emlParseService;
    private final EmailAttachmentStorageService emailAttachmentStorageService;
    private final RfqEmailGateService rfqEmailGateService;
    private final RfqEmailClassificationService rfqEmailClassificationService;
    private final HermesRfqAgentService hermesRfqAgentService;
    private final RfqService rfqService;

    /**
     * Method signature friendly to scheduler modules that call bean methods without parameters.
     */
    public void execute() {
        syncAll();
    }

    /**
     * Method signature friendly to scheduler modules that pass a string parameter.
     */
    public void execute(String ignoredParam) {
        syncAll();
    }

    @Scheduled(
            initialDelayString = "${ai.rfq.email.sync-initial-delay-millis:5000}",
            fixedDelayString = "${ai.rfq.email.sync-interval-millis:30000}"
    )
    public void scheduledExecute() {
        execute();
    }

    public void syncAll() {
        if (!isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("RFQ email sync skipped because previous run is still active");
            return;
        }
        try {
            mailboxAccountService.importConfiguredMailboxes(properties.getEmail().getMailboxes());
            List<AiRfqProperties.MailboxProperties> mailboxes = mailboxAccountService.mergeWithConfiguredMailboxes(
                    properties.getEmail().getMailboxes());
            if (CollectionUtils.isEmpty(mailboxes)) {
                log.info("RFQ email sync skipped because no mailbox is configured");
                return;
            }
            for (AiRfqProperties.MailboxProperties mailbox : mailboxes) {
                if (mailbox == null || Boolean.FALSE.equals(mailbox.getEnabled())) {
                    continue;
                }
                syncMailbox(mailbox);
            }
        } finally {
            running.set(false);
        }
    }

    private void syncMailbox(AiRfqProperties.MailboxProperties mailbox) {
        AiTenantContextHolder.setTenantId(mailbox.getTenantId());
        String account = mailbox.getAccount();
        try {
            EmailSyncStateDO state = imapEmailSyncService.getOrCreateState(mailbox);
            List<EmailRawDTO> emails = imapEmailSyncService.fetchIncremental(mailbox, state.getLastUid());
            int rfqCount = 0;
            int nonRfqCount = 0;
            int duplicateCount = 0;
            for (EmailRawDTO rawEmail : emails) {
                ProcessResult result = processOne(state, rawEmail);
                if (result == ProcessResult.RFQ_CREATED) {
                    rfqCount++;
                } else if (result == ProcessResult.NON_RFQ) {
                    nonRfqCount++;
                } else if (result == ProcessResult.DUPLICATE) {
                    duplicateCount++;
                } else if (result == ProcessResult.BLOCKED_RETRY) {
                    break;
                }
            }
            log.info("RFQ email sync mailbox finished, tenantId={}, account={}, fetched={}, rfq={}, nonRfq={}, duplicate={}",
                    mailbox.getTenantId(), account, emails.size(), rfqCount, nonRfqCount, duplicateCount);
        } catch (Exception ex) {
            log.warn("RFQ email sync mailbox failed, tenantId={}, account={}, errorType={}",
                    mailbox.getTenantId(), account, ex.getClass().getSimpleName());
        } finally {
            AiTenantContextHolder.clear();
        }
    }

    private ProcessResult processOne(EmailSyncStateDO state, EmailRawDTO rawEmail) {
        if (rfqService.existsByMessageId(rawEmail.getMessageId())) {
            imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
            return ProcessResult.DUPLICATE;
        }
        EmailDTO emailDTO;
        try {
            emailDTO = emlParseService.parse(rawEmail);
        } catch (ServiceException ex) {
            if (RFQ_EMAIL_PARSE_FAILED.equals(ex.getCode())) {
                log.warn("RFQ email parse skipped, tenantId={}, account={}, uid={}, messageId={}",
                        rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId());
                imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
                return ProcessResult.NON_RFQ;
            }
            return ProcessResult.BLOCKED_RETRY;
        }
        try {
            emailAttachmentStorageService.storeEmailAttachments(rawEmail, emailDTO);
        } catch (ServiceException ex) {
            log.warn("RFQ email attachment storage blocked for retry, tenantId={}, account={}, uid={}, messageId={}, code={}, reason={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    ex.getCode(), ex.getMessage());
            return ProcessResult.BLOCKED_RETRY;
        }
        RfqEmailGateService.GateResult preClassificationGate = rfqEmailGateService.evaluateBeforeClassification(rawEmail, emailDTO);
        if (!preClassificationGate.isPassed()) {
            log.info("RFQ email gate skipped before classification, tenantId={}, account={}, uid={}, messageId={}, reason={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    preClassificationGate.getReason());
            imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
            return ProcessResult.NON_RFQ;
        }
        RfqEmailClassificationDTO classification;
        try {
            classification = rfqEmailClassificationService.classify(emailDTO);
        } catch (ServiceException ex) {
            log.warn("RFQ email classification blocked for retry, tenantId={}, account={}, uid={}, messageId={}, code={}, reason={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    ex.getCode(), ex.getMessage());
            return ProcessResult.BLOCKED_RETRY;
        }
        if (!Boolean.TRUE.equals(classification.getIsRfq())) {
            log.info("RFQ email classification skipped, tenantId={}, account={}, uid={}, messageId={}, type={}, confidence={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    classification.getRfqType(), classification.getConfidence());
            imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
            return ProcessResult.NON_RFQ;
        }
        HermesRfqDTO hermesRfq;
        try {
            hermesRfq = hermesRfqAgentService.extractRfq(emailDTO);
        } catch (ServiceException ex) {
            log.warn("RFQ email Hermes blocked for retry, tenantId={}, account={}, uid={}, messageId={}, code={}, reason={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    ex.getCode(), ex.getMessage());
            return ProcessResult.BLOCKED_RETRY;
        }
        if (!Boolean.TRUE.equals(hermesRfq.getIsRfq())) {
            imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
            return ProcessResult.NON_RFQ;
        }
        RfqEmailGateService.GateResult preCreateGate = rfqEmailGateService.evaluateBeforeCreate(hermesRfq);
        if (!preCreateGate.isPassed()) {
            log.info("RFQ email gate skipped before create, tenantId={}, account={}, uid={}, messageId={}, reason={}",
                    rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                    preCreateGate.getReason());
            imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
            return ProcessResult.NON_RFQ;
        }
        RfqDO rfq = rfqService.createRfq(rawEmail, emailDTO, hermesRfq);
        emailAttachmentStorageService.bindRfqId(rawEmail, rfq == null ? null : rfq.getId());
        imapEmailSyncService.updateLastUid(state, rawEmail.getUid());
        log.info("RFQ created from email, tenantId={}, account={}, uid={}, messageId={}, rfqId={}",
                rawEmail.getTenantId(), rawEmail.getAccount(), rawEmail.getUid(), rawEmail.getMessageId(),
                rfq == null ? null : rfq.getId());
        return ProcessResult.RFQ_CREATED;
    }

    private boolean isEnabled() {
        return properties.getEmail() != null && Boolean.TRUE.equals(properties.getEmail().getEnabled());
    }

    private enum ProcessResult {
        RFQ_CREATED,
        NON_RFQ,
        DUPLICATE,
        BLOCKED_RETRY
    }

}
