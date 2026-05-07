package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;

import java.util.List;

/**
 * AI data source convert.
 */
public class AiDataSourceConvert {

    public static final AiDataSourceConvert INSTANCE = new AiDataSourceConvert();

    public AiDataSourceDO convert(AiDataSourceCreateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiDataSourceDO result = new AiDataSourceDO();
        fillBaseFields(result, bean.getKnowledgeBaseId(), bean.getName(), bean.getSourceType(), bean.getSyncMode(),
                bean.getConfigJson(), bean.getSyncEnabled(), bean.getStatus());
        return result;
    }

    public AiDataSourceDO convert(AiDataSourceUpdateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiDataSourceDO result = new AiDataSourceDO();
        result.setId(bean.getId());
        fillBaseFields(result, bean.getKnowledgeBaseId(), bean.getName(), bean.getSourceType(), bean.getSyncMode(),
                bean.getConfigJson(), bean.getSyncEnabled(), bean.getStatus());
        return result;
    }

    public AiDataSourceRespVO convert(AiDataSourceDO bean) {
        if (bean == null) {
            return null;
        }
        AiDataSourceRespVO result = new AiDataSourceRespVO();
        result.setId(bean.getId());
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setName(bean.getName());
        result.setSourceType(bean.getType());
        result.setSyncMode(bean.getSyncMode());
        result.setConfigJson(bean.getConfigJson());
        result.setSyncEnabled(bean.getSyncEnabled());
        result.setLastSyncTime(bean.getLastSyncTime());
        result.setStatus(bean.getStatus());
        result.setCreateTime(bean.getCreateTime());
        result.setUpdateTime(bean.getUpdateTime());
        return result;
    }

    public PageResult<AiDataSourceRespVO> convertPage(PageResult<AiDataSourceDO> page) {
        if (page == null) {
            return null;
        }
        List<AiDataSourceRespVO> list = page.getList().stream()
                .map(this::convert)
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    private void fillBaseFields(AiDataSourceDO result, Long knowledgeBaseId, String name, String sourceType,
                                String syncMode, String configJson, Boolean syncEnabled, Integer status) {
        result.setKnowledgeBaseId(knowledgeBaseId);
        result.setName(name);
        result.setType(sourceType);
        result.setSyncMode(syncMode);
        result.setConfigJson(configJson);
        result.setSyncEnabled(syncEnabled);
        result.setStatus(status);
    }

}
