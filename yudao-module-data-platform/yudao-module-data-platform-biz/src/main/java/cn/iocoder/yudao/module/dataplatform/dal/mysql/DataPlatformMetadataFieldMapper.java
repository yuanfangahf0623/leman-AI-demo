package cn.iocoder.yudao.module.dataplatform.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo.MetadataFieldPageReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformMetadataFieldDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataPlatformMetadataFieldMapper extends BaseMapper<DataPlatformMetadataFieldDO> {

    default PageResult<DataPlatformMetadataFieldDO> selectPage(MetadataFieldPageReqVO reqVO) {
        IPage<DataPlatformMetadataFieldDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(DataPlatformMetadataFieldDO.class)
                        .eq(DataPlatformMetadataFieldDO::getMetadataTableId, reqVO.getMetadataTableId())
                        .and(StringUtils.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                                .like(DataPlatformMetadataFieldDO::getSourceColumn, reqVO.getKeyword())
                                .or().like(DataPlatformMetadataFieldDO::getBusinessName, reqVO.getKeyword())
                                .or().like(DataPlatformMetadataFieldDO::getDescription, reqVO.getKeyword()))
                        .eq(StringUtils.isNotBlank(reqVO.getDefinitionStatus()),
                                DataPlatformMetadataFieldDO::getDefinitionStatus, reqVO.getDefinitionStatus())
                        .eq(StringUtils.isNotBlank(reqVO.getSensitivityLevel()),
                                DataPlatformMetadataFieldDO::getSensitivityLevel, reqVO.getSensitivityLevel())
                        .eq(DataPlatformMetadataFieldDO::getStatus, 0)
                        .orderByAsc(DataPlatformMetadataFieldDO::getOrdinalPosition));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
