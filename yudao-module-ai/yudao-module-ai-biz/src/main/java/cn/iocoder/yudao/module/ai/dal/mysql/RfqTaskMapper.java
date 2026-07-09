package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.RfqTaskDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * RFQ task Mapper.
 */
@Mapper
public interface RfqTaskMapper extends BaseMapper<RfqTaskDO> {

    default List<RfqTaskDO> selectListByRfqIdAndTenantId(Long rfqId, Long tenantId) {
        return selectList(Wrappers.lambdaQuery(RfqTaskDO.class)
                .eq(RfqTaskDO::getTenantId, tenantId)
                .eq(RfqTaskDO::getRfqId, rfqId)
                .orderByAsc(RfqTaskDO::getId));
    }

}
