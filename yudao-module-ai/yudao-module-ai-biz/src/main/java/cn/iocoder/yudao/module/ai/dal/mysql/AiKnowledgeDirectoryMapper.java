package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeDirectoryDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 知识库目录 Mapper。
 */
@Mapper
public interface AiKnowledgeDirectoryMapper extends BaseMapper<AiKnowledgeDirectoryDO> {

    default AiKnowledgeDirectoryDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(Wrappers.lambdaQuery(AiKnowledgeDirectoryDO.class)
                .eq(AiKnowledgeDirectoryDO::getId, id)
                .eq(AiKnowledgeDirectoryDO::getTenantId, tenantId));
    }

    default List<AiKnowledgeDirectoryDO> selectListByKnowledgeBaseId(Long tenantId, Long knowledgeBaseId) {
        return selectList(Wrappers.lambdaQuery(AiKnowledgeDirectoryDO.class)
                .eq(AiKnowledgeDirectoryDO::getTenantId, tenantId)
                .eq(AiKnowledgeDirectoryDO::getKnowledgeBaseId, knowledgeBaseId)
                .orderByAsc(AiKnowledgeDirectoryDO::getSort)
                .orderByAsc(AiKnowledgeDirectoryDO::getId));
    }

    default AiKnowledgeDirectoryDO selectByName(Long tenantId, Long knowledgeBaseId, Long parentId, String name) {
        return selectOne(Wrappers.lambdaQuery(AiKnowledgeDirectoryDO.class)
                .eq(AiKnowledgeDirectoryDO::getTenantId, tenantId)
                .eq(AiKnowledgeDirectoryDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiKnowledgeDirectoryDO::getParentId, parentId)
                .eq(AiKnowledgeDirectoryDO::getName, name));
    }

    default Long selectCountByParentId(Long tenantId, Long knowledgeBaseId, Long parentId) {
        return selectCount(Wrappers.lambdaQuery(AiKnowledgeDirectoryDO.class)
                .eq(AiKnowledgeDirectoryDO::getTenantId, tenantId)
                .eq(AiKnowledgeDirectoryDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiKnowledgeDirectoryDO::getParentId, parentId));
    }

}
