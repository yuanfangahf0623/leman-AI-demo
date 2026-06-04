package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceAttachmentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Finance invoice attachment Mapper.
 */
@Mapper
public interface FinanceInvoiceAttachmentMapper extends BaseMapper<FinanceInvoiceAttachmentDO> {

    default List<FinanceInvoiceAttachmentDO> selectListByInvoiceIdAndTenantId(Long invoiceId, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(FinanceInvoiceAttachmentDO.class)
                .eq(FinanceInvoiceAttachmentDO::getInvoiceId, invoiceId)
                .eq(FinanceInvoiceAttachmentDO::getTenantId, tenantId)
                .orderByDesc(FinanceInvoiceAttachmentDO::getId));
    }

}
