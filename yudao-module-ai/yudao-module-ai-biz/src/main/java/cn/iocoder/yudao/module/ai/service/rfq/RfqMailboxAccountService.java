package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.controller.admin.rfq.vo.RfqMailboxAccountRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.rfq.vo.RfqMailboxAccountSaveReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.RfqMailboxAccountDO;
import cn.iocoder.yudao.module.ai.dal.mysql.RfqMailboxAccountMapper;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_EMAIL_CONFIG_INVALID;

/**
 * RFQ mailbox account service.
 */
@Service
@RequiredArgsConstructor
public class RfqMailboxAccountService {

    private final RfqMailboxAccountMapper mailboxAccountMapper;
    private final RfqMailboxPasswordCryptoService passwordCryptoService;

    public List<RfqMailboxAccountRespVO> listCurrentTenantAccounts() {
        return mailboxAccountMapper.selectListByTenantId(AiTenantContextHolder.getTenantId()).stream()
                .map(this::toRespVO)
                .toList();
    }

    public Long save(RfqMailboxAccountSaveReqVO reqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        RfqMailboxAccountDO oldAccount = reqVO.getId() == null ? null
                : mailboxAccountMapper.selectByIdAndTenantId(reqVO.getId(), tenantId);
        if (reqVO.getId() != null && oldAccount == null) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox account does not exist");
        }
        if (oldAccount == null && !StringUtils.hasText(reqVO.getPassword())) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox password is required");
        }

        RfqMailboxAccountDO accountDO = new RfqMailboxAccountDO();
        accountDO.setTenantId(tenantId);
        accountDO.setAccount(reqVO.getAccount().trim());
        accountDO.setEmailAddress(reqVO.getEmailAddress().trim());
        accountDO.setHost(reqVO.getHost().trim());
        accountDO.setPort(reqVO.getPort());
        accountDO.setUsername(reqVO.getUsername().trim());
        accountDO.setFolder(StringUtils.hasText(reqVO.getFolder()) ? reqVO.getFolder().trim() : "INBOX");
        accountDO.setEnabled(reqVO.getEnabled() == null || Boolean.TRUE.equals(reqVO.getEnabled()));
        accountDO.setDeleted(false);
        if (StringUtils.hasText(reqVO.getPassword())) {
            accountDO.setPasswordCiphertext(passwordCryptoService.encrypt(reqVO.getPassword()));
            accountDO.setPasswordMask(passwordCryptoService.mask(reqVO.getPassword()));
        } else {
            accountDO.setPasswordCiphertext(oldAccount.getPasswordCiphertext());
            accountDO.setPasswordMask(oldAccount.getPasswordMask());
        }
        if (oldAccount == null) {
            mailboxAccountMapper.insert(accountDO);
            return accountDO.getId();
        }
        accountDO.setId(oldAccount.getId());
        mailboxAccountMapper.updateById(accountDO);
        return oldAccount.getId();
    }

    public String revealPassword(Long id) {
        RfqMailboxAccountDO account = mailboxAccountMapper.selectByIdAndTenantId(id, AiTenantContextHolder.getTenantId());
        if (account == null) {
            throw new ServiceException(RFQ_EMAIL_CONFIG_INVALID, "Mailbox account does not exist");
        }
        return passwordCryptoService.decrypt(account.getPasswordCiphertext());
    }

    public void importConfiguredMailboxes(List<AiRfqProperties.MailboxProperties> mailboxes) {
        if (mailboxes == null || mailboxes.isEmpty()) {
            return;
        }
        for (AiRfqProperties.MailboxProperties mailbox : mailboxes) {
            if (mailbox == null || Boolean.FALSE.equals(mailbox.getEnabled())
                    || !StringUtils.hasText(mailbox.getPassword())) {
                continue;
            }
            upsert(mailbox);
        }
    }

    public List<AiRfqProperties.MailboxProperties> listEnabledMailboxProperties() {
        List<RfqMailboxAccountDO> accounts = mailboxAccountMapper.selectEnabledList();
        List<AiRfqProperties.MailboxProperties> result = new ArrayList<>(accounts.size());
        for (RfqMailboxAccountDO account : accounts) {
            result.add(toMailboxProperties(account));
        }
        return result;
    }

    public RfqMailboxAccountDO upsert(AiRfqProperties.MailboxProperties mailbox) {
        RfqMailboxAccountDO accountDO = toAccountDO(mailbox);
        RfqMailboxAccountDO oldAccount = mailboxAccountMapper.selectByAccountAndTenantId(
                accountDO.getAccount(), accountDO.getTenantId());
        if (oldAccount == null) {
            try {
                mailboxAccountMapper.insert(accountDO);
                return accountDO;
            } catch (DuplicateKeyException ignored) {
                oldAccount = mailboxAccountMapper.selectByAccountAndTenantId(accountDO.getAccount(), accountDO.getTenantId());
            }
        }
        accountDO.setId(oldAccount.getId());
        mailboxAccountMapper.updateByAccountAndTenantId(accountDO);
        return mailboxAccountMapper.selectByAccountAndTenantId(
                accountDO.getAccount() == null ? oldAccount.getAccount() : accountDO.getAccount(),
                accountDO.getTenantId() == null ? oldAccount.getTenantId() : accountDO.getTenantId());
    }

    public List<AiRfqProperties.MailboxProperties> mergeWithConfiguredMailboxes(List<AiRfqProperties.MailboxProperties> configuredMailboxes) {
        Map<String, AiRfqProperties.MailboxProperties> merged = new LinkedHashMap<>();
        for (AiRfqProperties.MailboxProperties mailbox : listEnabledMailboxProperties()) {
            merged.put(mailboxKey(mailbox), mailbox);
        }
        if (configuredMailboxes != null) {
            for (AiRfqProperties.MailboxProperties mailbox : configuredMailboxes) {
                if (mailbox == null || Boolean.FALSE.equals(mailbox.getEnabled())) {
                    continue;
                }
                merged.putIfAbsent(mailboxKey(mailbox), mailbox);
            }
        }
        return List.copyOf(merged.values());
    }

    private AiRfqProperties.MailboxProperties toMailboxProperties(RfqMailboxAccountDO account) {
        AiRfqProperties.MailboxProperties mailbox = new AiRfqProperties.MailboxProperties();
        mailbox.setEnabled(account.getEnabled());
        mailbox.setAccount(account.getAccount());
        mailbox.setTenantId(account.getTenantId());
        mailbox.setHost(account.getHost());
        mailbox.setPort(account.getPort());
        mailbox.setUsername(account.getUsername());
        mailbox.setPassword(passwordCryptoService.decrypt(account.getPasswordCiphertext()));
        mailbox.setFolder(account.getFolder());
        return mailbox;
    }

    private RfqMailboxAccountDO toAccountDO(AiRfqProperties.MailboxProperties mailbox) {
        RfqMailboxAccountDO accountDO = new RfqMailboxAccountDO();
        String account = resolveAccount(mailbox);
        accountDO.setTenantId(mailbox.getTenantId() == null ? 0L : mailbox.getTenantId());
        accountDO.setAccount(account);
        accountDO.setEmailAddress(account);
        accountDO.setHost(trim(mailbox.getHost()));
        accountDO.setPort(mailbox.getPort() == null || mailbox.getPort() <= 0 ? 993 : mailbox.getPort());
        accountDO.setUsername(trim(mailbox.getUsername()));
        accountDO.setPasswordCiphertext(passwordCryptoService.encrypt(mailbox.getPassword()));
        accountDO.setPasswordMask(passwordCryptoService.mask(mailbox.getPassword()));
        accountDO.setFolder(StringUtils.hasText(mailbox.getFolder()) ? mailbox.getFolder().trim() : "INBOX");
        accountDO.setEnabled(!Boolean.FALSE.equals(mailbox.getEnabled()));
        accountDO.setDeleted(false);
        return accountDO;
    }

    private String resolveAccount(AiRfqProperties.MailboxProperties mailbox) {
        if (StringUtils.hasText(mailbox.getAccount())) {
            return mailbox.getAccount().trim();
        }
        return trim(mailbox.getUsername()).toLowerCase(Locale.ROOT);
    }

    private String mailboxKey(AiRfqProperties.MailboxProperties mailbox) {
        Long tenantId = mailbox.getTenantId() == null ? 0L : mailbox.getTenantId();
        return tenantId + ":" + resolveAccount(mailbox);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private RfqMailboxAccountRespVO toRespVO(RfqMailboxAccountDO account) {
        RfqMailboxAccountRespVO respVO = new RfqMailboxAccountRespVO();
        respVO.setId(account.getId());
        respVO.setTenantId(account.getTenantId());
        respVO.setAccount(account.getAccount());
        respVO.setEmailAddress(account.getEmailAddress());
        respVO.setHost(account.getHost());
        respVO.setPort(account.getPort());
        respVO.setUsername(account.getUsername());
        respVO.setPasswordMask(account.getPasswordMask());
        respVO.setFolder(account.getFolder());
        respVO.setEnabled(account.getEnabled());
        respVO.setCreateTime(account.getCreateTime());
        respVO.setUpdateTime(account.getUpdateTime());
        return respVO;
    }

}
