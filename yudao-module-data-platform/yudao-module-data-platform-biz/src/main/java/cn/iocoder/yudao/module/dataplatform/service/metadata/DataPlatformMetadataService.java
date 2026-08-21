package cn.iocoder.yudao.module.dataplatform.service.metadata;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.*;

import java.util.Map;

public interface DataPlatformMetadataService {
    PageResult<MetadataTableRespVO> pageTables(MetadataTablePageReqVO reqVO);
    PageResult<MetadataFieldRespVO> pageFields(MetadataFieldPageReqVO reqVO);
    Map<String, Object> summary(Long dataSourceId);
    MetadataRefreshRespVO refresh(Long dataSourceId);
    void updateTable(MetadataTableUpdateReqVO reqVO);
    void updateField(MetadataFieldUpdateReqVO reqVO);
}
