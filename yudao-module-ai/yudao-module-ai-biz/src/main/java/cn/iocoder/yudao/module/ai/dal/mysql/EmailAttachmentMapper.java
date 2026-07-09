package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.EmailAttachmentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Email attachment Mapper.
 */
@Mapper
public interface EmailAttachmentMapper extends BaseMapper<EmailAttachmentDO> {

    default EmailAttachmentDO selectByMessageHashAndContentHash(Long tenantId, String messageHash,
                                                                String contentHash) {
        return selectOne(Wrappers.lambdaQuery(EmailAttachmentDO.class)
                .eq(EmailAttachmentDO::getTenantId, tenantId)
                .eq(EmailAttachmentDO::getMessageHash, messageHash)
                .eq(EmailAttachmentDO::getContentHash, contentHash)
                .last("LIMIT 1"));
    }

    default List<EmailAttachmentDO> selectListByMessageId(Long tenantId, String messageId) {
        return selectList(Wrappers.lambdaQuery(EmailAttachmentDO.class)
                .eq(EmailAttachmentDO::getTenantId, tenantId)
                .eq(EmailAttachmentDO::getMessageId, messageId)
                .orderByAsc(EmailAttachmentDO::getId));
    }

    default int updateRfqIdByMessageHash(Long tenantId, String messageHash, Long rfqId) {
        return update(null, Wrappers.lambdaUpdate(EmailAttachmentDO.class)
                .set(EmailAttachmentDO::getRfqId, rfqId)
                .eq(EmailAttachmentDO::getTenantId, tenantId)
                .eq(EmailAttachmentDO::getMessageHash, messageHash));
    }

}
