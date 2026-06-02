package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;

import java.util.List;

public interface TwoHaoHrAttendanceStatService {

    TwoHaoHrAttendanceStatRespVO getDepartmentStat(TwoHaoHrAttendanceStatReqVO reqVO);

    AiDataSourceDO findTwoHaoHrDataSource(Long tenantId, List<Long> knowledgeBaseIds);

}
