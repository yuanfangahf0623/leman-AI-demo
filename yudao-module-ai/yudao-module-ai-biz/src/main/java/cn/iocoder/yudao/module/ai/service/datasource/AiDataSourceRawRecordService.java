package cn.iocoder.yudao.module.ai.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * AI data source raw record service.
 */
public interface AiDataSourceRawRecordService {

    void saveRecords(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String provider, String moduleName,
                     String objectType, String sourceUri, List<JsonNode> records);

    PageResult<AiDataSourceRawRecordRespVO> getRawRecordPage(AiDataSourceRawRecordPageReqVO pageReqVO);

    String toMaskedJson(JsonNode payload);

}
