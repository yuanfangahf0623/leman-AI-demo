package cn.iocoder.yudao.module.ai.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceCreateReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourcePageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceUpdateReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;

/**
 * AI data source service.
 */
public interface AiDataSourceService {

    Long createDataSource(AiDataSourceCreateReqVO createReqVO);

    void updateDataSource(AiDataSourceUpdateReqVO updateReqVO);

    void deleteDataSource(Long id);

    AiDataSourceDO getDataSource(Long id);

    PageResult<AiDataSourceDO> getDataSourcePage(AiDataSourcePageReqVO pageReqVO);

}
