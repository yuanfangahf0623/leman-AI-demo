package cn.iocoder.yudao.module.dataplatform.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;

public interface DataPlatformDataSourceService {
    Long create(DataSourceSaveReqVO reqVO);
    void update(DataSourceSaveReqVO reqVO);
    void delete(Long id);
    DataSourceRespVO get(Long id);
    PageResult<DataSourceRespVO> page(DataSourcePageReqVO reqVO);
    void testConnection(Long id);
    void testConnection(DataSourceSaveReqVO reqVO);
    DataPlatformDataSourceDO requireDataSource(Long id);
    String decryptPassword(DataPlatformDataSourceDO dataSource);
    String buildJdbcUrl(DataPlatformDataSourceDO dataSource);
}
