package cn.iocoder.yudao.module.dataplatform.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobPageReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataPlatformSyncJobMapper extends BaseMapper<DataPlatformSyncJobDO> {

    default DataPlatformSyncJobDO selectByCode(String code) {
        return selectOne(Wrappers.lambdaQuery(DataPlatformSyncJobDO.class).eq(DataPlatformSyncJobDO::getCode, code));
    }

    default PageResult<DataPlatformSyncJobDO> selectPage(SyncJobPageReqVO reqVO) {
        IPage<DataPlatformSyncJobDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(DataPlatformSyncJobDO.class)
                        .like(StringUtils.isNotBlank(reqVO.getName()), DataPlatformSyncJobDO::getName, reqVO.getName())
                        .eq(StringUtils.isNotBlank(reqVO.getSyncMode()), DataPlatformSyncJobDO::getSyncMode, reqVO.getSyncMode())
                        .eq(reqVO.getStatus() != null, DataPlatformSyncJobDO::getStatus, reqVO.getStatus())
                        .orderByDesc(DataPlatformSyncJobDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }
}
