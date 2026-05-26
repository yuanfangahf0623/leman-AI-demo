package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiTwoHaoHrAttendanceRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

/**
 * 2hao HR attendance detail record Mapper.
 */
@Mapper
public interface AiTwoHaoHrAttendanceRecordMapper extends BaseMapper<AiTwoHaoHrAttendanceRecordDO> {

    default AiTwoHaoHrAttendanceRecordDO selectByUniqueKey(Long tenantId, Long dataSourceId, String recordType,
                                                           String externalId) {
        return selectOne(Wrappers.lambdaQuery(AiTwoHaoHrAttendanceRecordDO.class)
                .eq(AiTwoHaoHrAttendanceRecordDO::getTenantId, tenantId)
                .eq(AiTwoHaoHrAttendanceRecordDO::getDataSourceId, dataSourceId)
                .eq(AiTwoHaoHrAttendanceRecordDO::getRecordType, recordType)
                .eq(AiTwoHaoHrAttendanceRecordDO::getExternalId, externalId));
    }

}
