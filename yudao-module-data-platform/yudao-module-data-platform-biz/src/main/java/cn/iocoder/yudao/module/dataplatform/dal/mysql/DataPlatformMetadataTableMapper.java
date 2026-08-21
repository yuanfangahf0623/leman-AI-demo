package cn.iocoder.yudao.module.dataplatform.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.MetadataTablePageReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformMetadataTableDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DataPlatformMetadataTableMapper extends BaseMapper<DataPlatformMetadataTableDO> {

    default DataPlatformMetadataTableDO selectBySource(Long dataSourceId, String schema, String table) {
        return selectOne(Wrappers.lambdaQuery(DataPlatformMetadataTableDO.class)
                .eq(DataPlatformMetadataTableDO::getDataSourceId, dataSourceId)
                .eq(DataPlatformMetadataTableDO::getSourceSchema, schema)
                .eq(DataPlatformMetadataTableDO::getSourceTable, table));
    }

    default List<DataPlatformMetadataTableDO> selectByDataSourceId(Long dataSourceId) {
        return selectList(Wrappers.lambdaQuery(DataPlatformMetadataTableDO.class)
                .eq(DataPlatformMetadataTableDO::getDataSourceId, dataSourceId));
    }

    default PageResult<DataPlatformMetadataTableDO> selectPage(MetadataTablePageReqVO reqVO) {
        IPage<DataPlatformMetadataTableDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(DataPlatformMetadataTableDO.class)
                        .eq(reqVO.getDataSourceId() != null, DataPlatformMetadataTableDO::getDataSourceId,
                                reqVO.getDataSourceId())
                        .and(StringUtils.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                                .like(DataPlatformMetadataTableDO::getSourceTable, reqVO.getKeyword())
                                .or().like(DataPlatformMetadataTableDO::getBusinessName, reqVO.getKeyword()))
                        .eq(StringUtils.isNotBlank(reqVO.getBusinessDomain()),
                                DataPlatformMetadataTableDO::getBusinessDomain, reqVO.getBusinessDomain())
                        .eq(StringUtils.isNotBlank(reqVO.getDefinitionStatus()),
                                DataPlatformMetadataTableDO::getDefinitionStatus, reqVO.getDefinitionStatus())
                        .eq(DataPlatformMetadataTableDO::getStatus, 0)
                        .orderByAsc(DataPlatformMetadataTableDO::getSourceTable));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
