package cn.iocoder.yudao.module.dataplatform.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.JobRunPageReqVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataPlatformJobRunMapper extends BaseMapper<DataPlatformJobRunDO> {

    default PageResult<DataPlatformJobRunDO> selectPage(JobRunPageReqVO reqVO) {
        IPage<DataPlatformJobRunDO> page = selectPage(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()),
                Wrappers.lambdaQuery(DataPlatformJobRunDO.class)
                        .eq(reqVO.getJobId() != null, DataPlatformJobRunDO::getJobId, reqVO.getJobId())
                        .eq(StringUtils.isNotBlank(reqVO.getStatus()), DataPlatformJobRunDO::getStatus, reqVO.getStatus())
                        .orderByDesc(DataPlatformJobRunDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default Long selectRunningCount(Long jobId) {
        return selectCount(Wrappers.lambdaQuery(DataPlatformJobRunDO.class)
                .eq(DataPlatformJobRunDO::getJobId, jobId)
                .in(DataPlatformJobRunDO::getStatus, "PENDING", "RUNNING"));
    }
}
