package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.FinanceSupplierPaymentInfoDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Supplier payment info Mapper.
 */
@Mapper
public interface FinanceSupplierPaymentInfoMapper extends BaseMapper<FinanceSupplierPaymentInfoDO> {

    default Long selectCountBySupplierName(Long tenantId, String supplierName) {
        if (StringUtils.isBlank(supplierName)) {
            return 0L;
        }
        return selectCount(Wrappers.lambdaQuery(FinanceSupplierPaymentInfoDO.class)
                .eq(FinanceSupplierPaymentInfoDO::getTenantId, tenantId)
                .eq(FinanceSupplierPaymentInfoDO::getSupplierName, supplierName));
    }

    default List<FinanceSupplierPaymentInfoDO> selectListBySupplierName(Long tenantId, String supplierName) {
        if (StringUtils.isBlank(supplierName)) {
            return List.of();
        }
        return selectList(Wrappers.lambdaQuery(FinanceSupplierPaymentInfoDO.class)
                .eq(FinanceSupplierPaymentInfoDO::getTenantId, tenantId)
                .eq(FinanceSupplierPaymentInfoDO::getSupplierName, supplierName));
    }

    default FinanceSupplierPaymentInfoDO selectBySupplierNameAndIban(Long tenantId, String supplierName, String iban) {
        if (StringUtils.isBlank(supplierName) || StringUtils.isBlank(iban)) {
            return null;
        }
        return selectOne(Wrappers.lambdaQuery(FinanceSupplierPaymentInfoDO.class)
                .eq(FinanceSupplierPaymentInfoDO::getTenantId, tenantId)
                .eq(FinanceSupplierPaymentInfoDO::getSupplierName, supplierName)
                .eq(FinanceSupplierPaymentInfoDO::getIban, iban)
                .last("LIMIT 1"));
    }

    default int updateByIdAndTenantId(FinanceSupplierPaymentInfoDO paymentInfo, Long tenantId) {
        paymentInfo.setTenantId(null);
        return update(paymentInfo, Wrappers.lambdaQuery(FinanceSupplierPaymentInfoDO.class)
                .eq(FinanceSupplierPaymentInfoDO::getId, paymentInfo.getId())
                .eq(FinanceSupplierPaymentInfoDO::getTenantId, tenantId));
    }

}
