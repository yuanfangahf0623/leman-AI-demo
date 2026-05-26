package cn.iocoder.yudao.module.ai.service.datasource.twohaohr.callback;

import cn.iocoder.yudao.module.ai.controller.open.twohaohr.vo.TwoHaoHrCallbackReqVO;

public interface TwoHaoHrCallbackService {

    void acceptCallback(TwoHaoHrCallbackReqVO reqVO, Long tenantId, Long dataSourceId, String token, String userAgent);

}
