package cn.iocoder.yudao.module.dataplatform.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformDataSourceDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataPlatformDataSourceMapper extends BaseMapper<DataPlatformDataSourceDO> {

    default DataPlatformDataSourceDO selectByCode(String code) {
        return selectOne(Wrappers.lambdaQuery(DataPlatformDataSourceDO.class)
                .eq(DataPlatformDataSourceDO::getCode, code));
    }

    default PageResult<DataPlatformDataSourceDO> selectPage(DataSourcePageReqVO reqVO) {
        IPage<DataPlatformDataSourceDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(DataPlatformDataSourceDO.class)
                        .like(StringUtils.isNotBlank(reqVO.getName()), DataPlatformDataSourceDO::getName, reqVO.getName())
                        .eq(StringUtils.isNotBlank(reqVO.getType()), DataPlatformDataSourceDO::getType, reqVO.getType())
                        .eq(reqVO.getStatus() != null, DataPlatformDataSourceDO::getStatus, reqVO.getStatus())
                        .orderByDesc(DataPlatformDataSourceDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
