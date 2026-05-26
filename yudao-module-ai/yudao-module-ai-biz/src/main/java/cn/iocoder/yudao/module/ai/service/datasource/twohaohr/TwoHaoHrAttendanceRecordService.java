package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface TwoHaoHrAttendanceRecordService {

    void saveRecords(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String recordType, List<JsonNode> records);

}
