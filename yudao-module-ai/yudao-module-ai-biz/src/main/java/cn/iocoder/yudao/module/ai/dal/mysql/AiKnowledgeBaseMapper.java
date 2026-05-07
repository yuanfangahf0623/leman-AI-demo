package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgePageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 知识库 Mapper。
 */
@Mapper
public interface AiKnowledgeBaseMapper extends BaseMapper<AiKnowledgeBaseDO> {

    default AiKnowledgeBaseDO selectByTenantIdAndCode(Long tenantId, String code) {
        return selectOne(Wrappers.lambdaQuery(AiKnowledgeBaseDO.class)
                .eq(AiKnowledgeBaseDO::getTenantId, tenantId)
                .eq(AiKnowledgeBaseDO::getCode, code));
    }

    default AiKnowledgeBaseDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiKnowledgeBaseDO.class)
                .eq(AiKnowledgeBaseDO::getId, id)
                .eq(AiKnowledgeBaseDO::getTenantId, tenantId));
    }

    default PageResult<AiKnowledgeBaseDO> selectPage(AiKnowledgePageReqVO reqVO, Long tenantId) {
        IPage<AiKnowledgeBaseDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiKnowledgeBaseDO.class)
                        .eq(AiKnowledgeBaseDO::getTenantId, tenantId)
                        .like(StringUtils.isNotBlank(reqVO.getName()), AiKnowledgeBaseDO::getName, reqVO.getName())
                        .eq(StringUtils.isNotBlank(reqVO.getCode()), AiKnowledgeBaseDO::getCode, reqVO.getCode())
                        .eq(reqVO.getStatus() != null, AiKnowledgeBaseDO::getStatus, reqVO.getStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getVectorStoreType()), AiKnowledgeBaseDO::getVectorStoreType,
                                reqVO.getVectorStoreType())
                        .orderByDesc(AiKnowledgeBaseDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}
