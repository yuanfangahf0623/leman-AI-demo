package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceRawRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI data source raw record Mapper.
 */
@Mapper
public interface AiDataSourceRawRecordMapper extends BaseMapper<AiDataSourceRawRecordDO> {

    default AiDataSourceRawRecordDO selectByUniqueKey(Long tenantId, Long dataSourceId, String provider,
                                                      String objectType, String externalId) {
        return selectOne(Wrappers.lambdaQuery(AiDataSourceRawRecordDO.class)
                .eq(AiDataSourceRawRecordDO::getTenantId, tenantId)
                .eq(AiDataSourceRawRecordDO::getDataSourceId, dataSourceId)
                .eq(AiDataSourceRawRecordDO::getProvider, provider)
                .eq(AiDataSourceRawRecordDO::getObjectType, objectType)
                .eq(AiDataSourceRawRecordDO::getExternalId, externalId));
    }

    default PageResult<AiDataSourceRawRecordDO> selectPage(AiDataSourceRawRecordPageReqVO reqVO, Long tenantId) {
        IPage<AiDataSourceRawRecordDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiDataSourceRawRecordDO.class)
                        .eq(AiDataSourceRawRecordDO::getTenantId, tenantId)
                        .eq(reqVO.getKnowledgeBaseId() != null, AiDataSourceRawRecordDO::getKnowledgeBaseId,
                                reqVO.getKnowledgeBaseId())
                        .eq(reqVO.getDataSourceId() != null, AiDataSourceRawRecordDO::getDataSourceId,
                                reqVO.getDataSourceId())
                        .eq(reqVO.getSyncJobId() != null, AiDataSourceRawRecordDO::getSyncJobId,
                                reqVO.getSyncJobId())
                        .eq(StringUtils.isNotBlank(reqVO.getProvider()), AiDataSourceRawRecordDO::getProvider,
                                reqVO.getProvider())
                        .eq(StringUtils.isNotBlank(reqVO.getModuleName()), AiDataSourceRawRecordDO::getModuleName,
                                reqVO.getModuleName())
                        .eq(StringUtils.isNotBlank(reqVO.getObjectType()), AiDataSourceRawRecordDO::getObjectType,
                                reqVO.getObjectType())
                        .like(StringUtils.isNotBlank(reqVO.getExternalId()), AiDataSourceRawRecordDO::getExternalId,
                                reqVO.getExternalId())
                        .orderByDesc(AiDataSourceRawRecordDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}
