package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.module.ai.controller.admin.sync.vo.AiSyncJobCreateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;

/**
 * AI sync job convert.
 */
public class AiSyncJobConvert {

    public static final AiSyncJobConvert INSTANCE = new AiSyncJobConvert();

    public AiSyncJobDO convert(AiSyncJobCreateReqVO bean) {
        if (bean == null) {
            return null;
        }
        AiSyncJobDO result = new AiSyncJobDO();
        result.setKnowledgeBaseId(bean.getKnowledgeBaseId());
        result.setDataSourceId(bean.getDataSourceId());
        result.setJobType(bean.getJobType());
        return result;
    }

}
