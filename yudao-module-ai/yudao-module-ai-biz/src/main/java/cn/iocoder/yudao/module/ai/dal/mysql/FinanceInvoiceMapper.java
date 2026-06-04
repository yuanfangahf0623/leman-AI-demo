package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.invoice.vo.FinanceInvoicePageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceInvoiceDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Finance invoice Mapper.
 */
@Mapper
public interface FinanceInvoiceMapper extends BaseMapper<FinanceInvoiceDO> {

    default FinanceInvoiceDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getId, id)
                .eq(FinanceInvoiceDO::getTenantId, tenantId));
    }

    default FinanceInvoiceDO selectByProcessInstanceIdAndTenantId(String processInstanceId, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getProcessInstanceId, processInstanceId)
                .eq(FinanceInvoiceDO::getTenantId, tenantId)
                .last("LIMIT 1"));
    }

    default PageResult<FinanceInvoiceDO> selectPage(FinanceInvoicePageReqVO reqVO, Long tenantId) {
        IPage<FinanceInvoiceDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                        .eq(FinanceInvoiceDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getSupplierName()), FinanceInvoiceDO::getSupplierName,
                                reqVO.getSupplierName())
                        .like(StringUtils.isNotBlank(reqVO.getInvoiceNo()), FinanceInvoiceDO::getInvoiceNo,
                                reqVO.getInvoiceNo())
                        .eq(StringUtils.isNotBlank(reqVO.getApprovalStatus()), FinanceInvoiceDO::getApprovalStatus,
                                reqVO.getApprovalStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getAiStatus()), FinanceInvoiceDO::getAiStatus,
                                reqVO.getAiStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getFinanceReviewStatus()),
                                FinanceInvoiceDO::getFinanceReviewStatus, reqVO.getFinanceReviewStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getPaymentStatus()), FinanceInvoiceDO::getPaymentStatus,
                                reqVO.getPaymentStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getBookkeepingStatus()),
                                FinanceInvoiceDO::getBookkeepingStatus, reqVO.getBookkeepingStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getRiskLevel()), FinanceInvoiceDO::getRiskLevel,
                                reqVO.getRiskLevel())
                        .ge(reqVO.getInvoiceDateStart() != null, FinanceInvoiceDO::getInvoiceDate,
                                reqVO.getInvoiceDateStart())
                        .le(reqVO.getInvoiceDateEnd() != null, FinanceInvoiceDO::getInvoiceDate,
                                reqVO.getInvoiceDateEnd())
                        .ge(reqVO.getCreateTimeStart() != null, FinanceInvoiceDO::getCreateTime,
                                reqVO.getCreateTimeStart())
                        .le(reqVO.getCreateTimeEnd() != null, FinanceInvoiceDO::getCreateTime,
                                reqVO.getCreateTimeEnd())
                        .orderByDesc(FinanceInvoiceDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default Long selectDuplicateCount(Long tenantId, Long currentId, String supplierName, String invoiceNo,
                                      BigDecimal grossAmount, LocalDateTime invoiceDate) {
        if (StringUtils.isBlank(supplierName) || StringUtils.isBlank(invoiceNo)
                || grossAmount == null || invoiceDate == null) {
            return 0L;
        }
        return selectCount(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getTenantId, tenantId)
                .ne(currentId != null, FinanceInvoiceDO::getId, currentId)
                .eq(FinanceInvoiceDO::getSupplierName, supplierName)
                .eq(FinanceInvoiceDO::getInvoiceNo, invoiceNo)
                .eq(FinanceInvoiceDO::getGrossAmount, grossAmount)
                .eq(FinanceInvoiceDO::getInvoiceDate, invoiceDate));
    }

    default Long selectSupplierHistoryCount(Long tenantId, Long currentId, String supplierName) {
        if (StringUtils.isBlank(supplierName)) {
            return 0L;
        }
        return selectCount(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getTenantId, tenantId)
                .ne(currentId != null, FinanceInvoiceDO::getId, currentId)
                .eq(FinanceInvoiceDO::getSupplierName, supplierName));
    }

    default List<FinanceInvoiceDO> selectSupplierIbanHistory(Long tenantId, Long currentId, String supplierName) {
        if (StringUtils.isBlank(supplierName)) {
            return List.of();
        }
        return selectList(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .select(FinanceInvoiceDO::getIban)
                .eq(FinanceInvoiceDO::getTenantId, tenantId)
                .ne(currentId != null, FinanceInvoiceDO::getId, currentId)
                .eq(FinanceInvoiceDO::getSupplierName, supplierName)
                .isNotNull(FinanceInvoiceDO::getIban));
    }

    default int updateByIdAndTenantId(FinanceInvoiceDO invoice, Long tenantId) {
        invoice.setTenantId(null);
        return update(invoice, Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getId, invoice.getId())
                .eq(FinanceInvoiceDO::getTenantId, tenantId));
    }

    default int deleteByIdAndTenantId(Long id, Long tenantId) {
        return delete(Wrappers.lambdaQuery(FinanceInvoiceDO.class)
                .eq(FinanceInvoiceDO::getId, id)
                .eq(FinanceInvoiceDO::getTenantId, tenantId));
    }

}
