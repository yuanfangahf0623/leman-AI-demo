package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.document.vo.AiDocumentPageReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 文档 Mapper。
 */
@Mapper
public interface AiDocumentMapper extends BaseMapper<AiDocumentDO> {

    default AiDocumentDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default int deleteByIdAndTenantId(Long id, Long tenantId) {
        return delete(Wrappers.lambdaQuery(AiDocumentDO.class)
                .eq(AiDocumentDO::getId, id)
                .eq(AiDocumentDO::getTenantId, tenantId));
    }

    default PageResult<AiDocumentDO> selectPage(AiDocumentPageReqVO reqVO, Long tenantId) {
        IPage<AiDocumentDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(AiDocumentDO.class)
                        .eq(AiDocumentDO::getTenantId, tenantId)
                        .eq(reqVO.getKnowledgeBaseId() != null, AiDocumentDO::getKnowledgeBaseId,
                                reqVO.getKnowledgeBaseId())
                        .eq(reqVO.getParseStatus() != null, AiDocumentDO::getParseStatus, reqVO.getParseStatus())
                        .eq(reqVO.getEmbeddingStatus() != null, AiDocumentDO::getEmbeddingStatus,
                                reqVO.getEmbeddingStatus())
                        .like(StringUtils.isNotBlank(reqVO.getTitle()), AiDocumentDO::getTitle, reqVO.getTitle())
                        .orderByDesc(AiDocumentDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}
