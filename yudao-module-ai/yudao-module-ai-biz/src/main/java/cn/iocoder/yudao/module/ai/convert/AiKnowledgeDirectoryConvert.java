package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo.AiKnowledgeDirectoryUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiKnowledgeDirectoryDO;

import java.util.List;

/**
 * AI 知识库目录 Convert。
 */
public class AiKnowledgeDirectoryConvert {

    public static final AiKnowledgeDirectoryConvert INSTANCE = new AiKnowledgeDirectoryConvert();

    public AiKnowledgeDirectoryDO convert(AiKnowledgeDirectoryCreateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeDirectoryDO result = new AiKnowledgeDirectoryDO();
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setParentId(bean.getParentId());
        result.setName(bean.getName());
        result.setSort(bean.getSort());
        result.setStatus(bean.getStatus());
        return result;
    }

    public AiKnowledgeDirectoryDO convert(AiKnowledgeDirectoryUpdateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeDirectoryDO result = new AiKnowledgeDirectoryDO();
        result.setId(bean.getId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setParentId(bean.getParentId());
        result.setName(bean.getName());
        result.setSort(bean.getSort());
        result.setStatus(bean.getStatus());
        return result;
    }

    public AiKnowledgeDirectoryRespVO convert(AiKnowledgeDirectoryDO bean) {
        if (bean == null) {
            return null;
        }
        AiKnowledgeDirectoryRespVO result = new AiKnowledgeDirectoryRespVO();
        result.setId(bean.getId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setParentId(bean.getParentId());
        result.setName(bean.getName());
        result.setSort(bean.getSort());
        result.setStatus(bean.getStatus());
        result.setCreateTime(bean.getCreateTime());
        result.setUpdateTime(bean.getUpdateTime());
        return result;
    }

    public List<AiKnowledgeDirectoryRespVO> convertList(List<AiKnowledgeDirectoryDO> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(this::convert).toList();
    }

}
