package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeBaseDO;

import java.util.List;

/**
 * AI knowledge convert.
 */
public class AiKnowledgeConvert {

    public static final AiKnowledgeConvert INSTANCE = new AiKnowledgeConvert();

    public AiKnowledgeBaseDO convert(AiKnowledgeCreateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeBaseDO result = new AiKnowledgeBaseDO();
        fillBaseFields(result, bean.getName(), bean.getCode(), bean.getDescription(), bean.getStatus(),
                bean.getVisibility(), bean.getDepartmentIds(), bean.getVectorStoreType(), bean.getEmbeddingModel(),
                bean.getChatModel(), bean.getChunkSize(), bean.getChunkOverlap(), bean.getTopK(),
                bean.getScoreThreshold());
        return result;
    }

    public AiKnowledgeBaseDO convert(AiKnowledgeUpdateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeBaseDO result = new AiKnowledgeBaseDO();
        result.setId(bean.getId());
        fillBaseFields(result, bean.getName(), bean.getCode(), bean.getDescription(), bean.getStatus(),
                bean.getVisibility(), bean.getDepartmentIds(), bean.getVectorStoreType(), bean.getEmbeddingModel(),
                bean.getChatModel(), bean.getChunkSize(), bean.getChunkOverlap(), bean.getTopK(),
                bean.getScoreThreshold());
        return result;
    }

    public AiKnowledgeRespVO convert(AiKnowledgeBaseDO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeRespVO result = new AiKnowledgeRespVO();
        result.setId(bean.getId());
        result.setName(bean.getName());
        result.setCode(bean.getCode());
        result.setDescription(bean.getDescription());
        result.setStatus(bean.getStatus());
        result.setVisibility(bean.getVisibility());
        result.setDepartmentIds(bean.getDepartmentIds());
        result.setVectorStoreType(bean.getVectorStoreType());
        result.setEmbeddingModel(bean.getEmbeddingModel());
        result.setChatModel(bean.getChatModel());
        result.setChunkSize(bean.getChunkSize());
        result.setChunkOverlap(bean.getChunkOverlap());
        result.setTopK(bean.getTopK());
        result.setScoreThreshold(bean.getScoreThreshold());
        result.setDocumentCount(bean.getDocumentCount());
        result.setChunkCount(bean.getChunkCount());
        result.setCreateTime(bean.getCreateTime());
        result.setUpdateTime(bean.getUpdateTime());
        return result;
    }

    public PageResult<AiKnowledgeRespVO> convertPage(PageResult<AiKnowledgeBaseDO> page) {
        if (page == null) {
            return null;
        }
        List<AiKnowledgeRespVO> list = page.getList().stream()
                .map(this::convert)
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    private void fillBaseFields(AiKnowledgeBaseDO result, String name, String code, String description, Integer status,
                                String visibility, String departmentIds, String vectorStoreType, String embeddingModel,
                                String chatModel, Integer chunkSize, Integer chunkOverlap, Integer topK,
                                Double scoreThreshold) {
        result.setName(name);
        result.setCode(code);
        result.setDescription(description);
        result.setStatus(status);
        result.setVisibility(visibility);
        result.setDepartmentIds(departmentIds);
        result.setVectorStoreType(vectorStoreType);
        result.setEmbeddingModel(embeddingModel);
        result.setChatModel(chatModel);
        result.setChunkSize(chunkSize);
        result.setChunkOverlap(chunkOverlap);
        result.setTopK(topK);
        result.setScoreThreshold(scoreThreshold);
    }

}
