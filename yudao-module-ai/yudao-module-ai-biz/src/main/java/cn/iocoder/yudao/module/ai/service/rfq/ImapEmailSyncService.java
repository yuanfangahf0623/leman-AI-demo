package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.dal.dataobject.EmailSyncStateDO;
import cn.iocoder.yudao.module.ai.dal.mysql.EmailSyncStateMapper;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_SYNC_FAILED;

/**
 * IMAP email sync service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImapEmailSyncService {

    private static final int DEFAULT_PORT = 993;
    private static final long DEFAULT_LAST_UID = 0L;

    private final AiRfqProperties properties;
    private final EmailSyncStateMapper emailSyncStateMapper;

    public EmailSyncStateDO getOrCreateState(AiRfqProperties.MailboxProperties mailbox) {
        Long tenantId = resolveTenantId(mailbox);
        String account = resolveAccount(mailbox);
        EmailSyncStateDO oldState = emailSyncStateMapper.selectByAccountAndTenantId(account, tenantId);
        if (oldState != null) {
            return oldState;
        }
        EmailSyncStateDO state = new EmailSyncStateDO();
        state.setTenantId(tenantId);
        state.setAccount(account);
        state.setLastUid(DEFAULT_LAST_UID);
        state.setLastSyncTime(null);
        state.setDeleted(false);
        try {
            emailSyncStateMapper.insert(state);
            return state;
        } catch (DuplicateKeyException ignored) {
            return emailSyncStateMapper.selectByAccountAndTenantId(account, tenantId);
        }
    }

    public void updateLastUid(EmailSyncStateDO state, Long uid) {
        if (state == null || state.getId() == null || uid == null) {
            return;
        }
        emailSyncStateMapper.updateLastUid(state.getId(), state.getTenantId(), uid, LocalDateTime.now());
        state.setLastUid(uid);
        state.setLastSyncTime(LocalDateTime.now());
    }

    public List<EmailRawDTO> fetchIncremental(AiRfqProperties.MailboxProperties mailbox, Long lastUid) {
        validateMailbox(mailbox);
        Store store = null;
        Folder folder = null;
        String account = resolveAccount(mailbox);
        try {
            Session session = Session.getInstance(buildImapProperties(mailbox));
            store = session.getStore("imaps");
            store.connect(mailbox.getHost().trim(), resolvePort(mailbox), mailbox.getUsername().trim(),
                    mailbox.getPassword());
            folder = store.getFolder(resolveFolder(mailbox));
            folder.open(Folder.READ_ONLY);
            if (!(folder instanceof UIDFolder uidFolder)) {
                throw new ServiceException(RFQ_EMAIL_SYNC_FAILED, "IMAP server does not support UID");
            }
            long startUid = Math.max(DEFAULT_LAST_UID, lastUid == null ? DEFAULT_LAST_UID : lastUid) + 1;
            Message[] messages = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
            List<MessageWithUid> sortedMessages = new ArrayList<>();
            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                if (uid >= startUid) {
                    sortedMessages.add(new MessageWithUid(message, uid));
                }
            }
            sortedMessages.sort(Comparator.comparingLong(MessageWithUid::uid));
            int limit = resolveMaxMessages(mailbox);
            List<EmailRawDTO> result = new ArrayList<>(Math.min(sortedMessages.size(), limit));
            for (MessageWithUid item : sortedMessages) {
                if (result.size() >= limit) {
                    break;
                }
                result.add(toRawDTO(mailbox, item.message(), item.uid()));
            }
            return result;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("IMAP sync failed, account={}, host={}, errorType={}",
                    account, mailbox.getHost(), ex.getClass().getSimpleName());
            throw new ServiceException(RFQ_EMAIL_SYNC_FAILED, "IMAP email sync failed");
        } finally {
            closeFolder(folder);
            closeStore(store);
        }
    }

    private EmailRawDTO toRawDTO(AiRfqProperties.MailboxProperties mailbox, Message message, long uid) throws Exception {
        String account = resolveAccount(mailbox);
        String messageId = resolveMessageId(account, uid, message);
        return EmailRawDTO.builder()
                .tenantId(resolveTenantId(mailbox))
                .uid(uid)
                .messageId(messageId)
                .from(addressesToText(message.getFrom()))
                .to(addressesToText(message.getRecipients(Message.RecipientType.TO)))
                .subject(decodeText(message.getSubject()))
                .receivedTime(resolveReceivedTime(message))
                .rawMime(readRawMime(message))
                .account(account)
                .build();
    }

    private Properties buildImapProperties(AiRfqProperties.MailboxProperties mailbox) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", mailbox.getHost().trim());
        props.put("mail.imaps.port", String.valueOf(resolvePort(mailbox)));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", String.valueOf(resolveConnectTimeoutMillis()));
        props.put("mail.imaps.timeout", String.valueOf(resolveReadTimeoutMillis()));
        props.put("mail.imaps.writetimeout", String.valueOf(resolveReadTimeoutMillis()));
        return props;
    }

    private String readRawMime(Message message) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            message.writeTo(outputStream);
            return outputStream.toString(StandardCharsets.ISO_8859_1);
        }
    }

    private String resolveMessageId(String account, long uid, Message message) throws MessagingException {
        String[] headers = message.getHeader("Message-ID");
        if (headers != null && headers.length > 0 && StringUtils.hasText(headers[0])) {
            String messageId = headers[0].trim();
            return messageId.length() <= 512 ? messageId : messageId.substring(0, 512);
        }
        return "<" + account + ":" + uid + ">";
    }

    private LocalDateTime resolveReceivedTime(Message message) throws MessagingException {
        Date date = message.getReceivedDate() == null ? message.getSentDate() : message.getReceivedDate();
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private String addressesToText(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address != null) {
                values.add(address.toString());
            }
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String decodeText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        try {
            return MimeUtility.decodeText(text);
        } catch (Exception ignored) {
            return text;
        }
    }

    private void validateMailbox(AiRfqProperties.MailboxProperties mailbox) {
        if (mailbox == null) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox config is empty");
        }
        if (!StringUtils.hasText(mailbox.getHost()) || !StringUtils.hasText(mailbox.getUsername())
                || !StringUtils.hasText(mailbox.getPassword()) || !StringUtils.hasText(resolveAccount(mailbox))) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox config is incomplete");
        }
    }

    private String resolveAccount(AiRfqProperties.MailboxProperties mailbox) {
        if (mailbox == null) {
            return "";
        }
        if (StringUtils.hasText(mailbox.getAccount())) {
            return mailbox.getAccount().trim();
        }
        return mailbox.getUsername() == null ? "" : mailbox.getUsername().trim().toLowerCase(Locale.ROOT);
    }

    private Long resolveTenantId(AiRfqProperties.MailboxProperties mailbox) {
        return mailbox == null || mailbox.getTenantId() == null ? 0L : mailbox.getTenantId();
    }

    private int resolvePort(AiRfqProperties.MailboxProperties mailbox) {
        return mailbox.getPort() == null || mailbox.getPort() <= 0 ? DEFAULT_PORT : mailbox.getPort();
    }

    private String resolveFolder(AiRfqProperties.MailboxProperties mailbox) {
        if (StringUtils.hasText(mailbox.getFolder())) {
            return mailbox.getFolder().trim();
        }
        String defaultFolder = properties.getEmail() == null ? null : properties.getEmail().getDefaultFolder();
        return StringUtils.hasText(defaultFolder) ? defaultFolder.trim() : "INBOX";
    }

    private int resolveMaxMessages(AiRfqProperties.MailboxProperties mailbox) {
        Integer mailboxLimit = mailbox.getMaxMessagesPerPoll();
        Integer defaultLimit = properties.getEmail() == null ? null : properties.getEmail().getMaxMessagesPerPoll();
        int limit = mailboxLimit == null || mailboxLimit <= 0 ? (defaultLimit == null ? 20 : defaultLimit) : mailboxLimit;
        return Math.max(1, Math.min(limit, 100));
    }

    private int resolveConnectTimeoutMillis() {
        Integer value = properties.getEmail() == null ? null : properties.getEmail().getConnectTimeoutMillis();
        return value == null || value <= 0 ? 10_000 : value;
    }

    private int resolveReadTimeoutMillis() {
        Integer value = properties.getEmail() == null ? null : properties.getEmail().getReadTimeoutMillis();
        return value == null || value <= 0 ? 60_000 : value;
    }

    private void closeFolder(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (MessagingException ignored) {
            // Ignore close failure.
        }
    }

    private void closeStore(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // Ignore close failure.
        }
    }

    private record MessageWithUid(Message message, long uid) {
    }

}
