package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 数据源 Mapper。
 */
@Mapper
public interface AiDataSourceMapper extends BaseMapper<AiDataSourceDO> {

    default AiDataSourceDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiDataSourceDO.class)
                .eq(AiDataSourceDO::getId, id)
                .eq(AiDataSourceDO::getTenantId, tenantId));
    }

    default PageResult<AiDataSourceDO> selectPage(AiDataSourcePageReqVO reqVO, Long tenantId) {
        IPage<AiDataSourceDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiDataSourceDO.class)
                        .eq(AiDataSourceDO::getTenantId, tenantId)
                        .eq(reqVO.getKnowledgeBaseId() != null, AiDataSourceDO::getKnowledgeBaseId,
                                reqVO.getKnowledgeBaseId())
                        .like(StringUtils.isNotBlank(reqVO.getName()), AiDataSourceDO::getName, reqVO.getName())
                        .eq(StringUtils.isNotBlank(reqVO.getSourceType()), AiDataSourceDO::getType,
                                reqVO.getSourceType())
                        .eq(StringUtils.isNotBlank(reqVO.getSyncMode()), AiDataSourceDO::getSyncMode,
                                reqVO.getSyncMode())
                        .eq(reqVO.getStatus() != null, AiDataSourceDO::getStatus, reqVO.getStatus())
                        .orderByDesc(AiDataSourceDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}
