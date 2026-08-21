package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.JobRunPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobPageReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobSaveReqVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo.SyncJobRespVO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformJobRunMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformSyncJobMapper;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.SYNC_JOB_CONFIG_INVALID;
import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.SYNC_JOB_NOT_EXISTS;
import static cn.iocoder.yudao.module.dataplatform.enums.DataPlatformErrorCodeConstants.SYNC_JOB_RUNNING;

@Service
@RequiredArgsConstructor
public class DataPlatformSyncJobServiceImpl implements DataPlatformSyncJobService {

    private final DataPlatformSyncJobMapper jobMapper;
    private final DataPlatformJobRunMapper runMapper;
    private final DataPlatformDataSourceService dataSourceService;
    private final SeaTunnelJobExecutor executor;
    private final SyncFieldMappingSqlBuilder mappingSqlBuilder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SyncJobSaveReqVO reqVO) {
        validate(reqVO, null);
        DataPlatformSyncJobDO job = toDO(reqVO);
        job.setParallelism(reqVO.getParallelism() == null ? 1 : reqVO.getParallelism());
        job.setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        jobMapper.insert(job);
        return job.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SyncJobSaveReqVO reqVO) {
        requireJob(reqVO.getId());
        validate(reqVO, reqVO.getId());
        DataPlatformSyncJobDO job = toDO(reqVO);
        job.setParallelism(reqVO.getParallelism() == null ? 1 : reqVO.getParallelism());
        job.setStatus(reqVO.getStatus() == null ? 0 : reqVO.getStatus());
        jobMapper.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireJob(id);
        if (runMapper.selectRunningCount(id) > 0) {
            throw new ServiceException(SYNC_JOB_RUNNING, "任务正在运行，不能删除");
        }
        jobMapper.deleteById(id);
    }

    @Override
    public SyncJobRespVO get(Long id) {
        return toResp(requireJob(id));
    }

    @Override
    public PageResult<SyncJobRespVO> page(SyncJobPageReqVO reqVO) {
        PageResult<DataPlatformSyncJobDO> page = jobMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toResp).toList(), page.getTotal());
    }

    @Override
    public Long execute(Long id, String creator) {
        DataPlatformSyncJobDO job = requireJob(id);
        if (job.getStatus() != null && job.getStatus() != 0) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步任务未启用");
        }
        if (runMapper.selectRunningCount(id) > 0) {
            throw new ServiceException(SYNC_JOB_RUNNING, "同步任务已有运行实例");
        }
        DataPlatformJobRunDO run = new DataPlatformJobRunDO();
        run.setJobId(job.getId());
        run.setJobName(job.getName());
        run.setBatchId(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                + "_ods_" + job.getCode());
        run.setTriggerType("MANUAL");
        run.setStatus("PENDING");
        run.setCreator(normalizeCreator(creator));
        runMapper.insert(run);
        executor.executeAsync(run.getId());
        return run.getId();
    }

    @Override
    public PageResult<DataPlatformJobRunDO> runPage(JobRunPageReqVO reqVO) {
        return runMapper.selectPage(reqVO);
    }

    @Override
    public DataPlatformJobRunDO getRun(Long id) {
        DataPlatformJobRunDO run = runMapper.selectById(id);
        if (run == null) {
            throw new ServiceException(SYNC_JOB_NOT_EXISTS, "运行记录不存在");
        }
        return run;
    }

    @Override
    public String readRunLog(Long id, int tailLines) {
        DataPlatformJobRunDO run = getRun(id);
        if (run.getLogPath() == null) {
            return "";
        }
        try {
            Path path = Path.of(run.getLogPath()).normalize();
            if (!Files.isRegularFile(path)) {
                return "";
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, Math.min(tailLines, 1000)));
            return String.join(System.lineSeparator(), lines.subList(from, lines.size()));
        } catch (Exception ex) {
            throw new ServiceException(SYNC_JOB_NOT_EXISTS, "任务日志读取失败");
        }
    }

    private void validate(SyncJobSaveReqVO reqVO, Long id) {
        dataSourceService.requireDataSource(reqVO.getSourceDataSourceId());
        dataSourceService.requireDataSource(reqVO.getTargetDataSourceId());
        DataPlatformSyncJobDO existing = jobMapper.selectByCode(reqVO.getCode());
        if (existing != null && !existing.getId().equals(id)) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "同步任务编码已存在");
        }
        if ("INCREMENTAL".equals(reqVO.getSyncMode()) &&
                (reqVO.getWatermarkColumn() == null || reqVO.getWatermarkColumn().isBlank())) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "增量任务必须配置水位字段");
        }
        mappingSqlBuilder.validate(reqVO.getFieldMappings());
        if ((reqVO.getFieldMappings() == null || reqVO.getFieldMappings().isEmpty())
                && !StringUtils.hasText(reqVO.getSinkSql())) {
            throw new ServiceException(SYNC_JOB_CONFIG_INVALID, "未配置字段映射时必须填写 Sink SQL");
        }
    }

    private DataPlatformSyncJobDO requireJob(Long id) {
        DataPlatformSyncJobDO job = id == null ? null : jobMapper.selectById(id);
        if (job == null) {
            throw new ServiceException(SYNC_JOB_NOT_EXISTS, "同步任务不存在");
        }
        return job;
    }

    private DataPlatformSyncJobDO toDO(SyncJobSaveReqVO reqVO) {
        DataPlatformSyncJobDO job = new DataPlatformSyncJobDO();
        job.setId(reqVO.getId());
        job.setName(reqVO.getName().trim());
        job.setCode(reqVO.getCode().trim());
        job.setSourceDataSourceId(reqVO.getSourceDataSourceId());
        job.setSourceSql(reqVO.getSourceSql().trim());
        job.setTargetDataSourceId(reqVO.getTargetDataSourceId());
        job.setTargetDatabase(reqVO.getTargetDatabase().trim());
        job.setTargetTable(reqVO.getTargetTable().trim());
        String mappingConfig = mappingSqlBuilder.serialize(reqVO.getFieldMappings());
        job.setMappingConfig(mappingConfig);
        job.setSinkSql(mappingSqlBuilder.buildSinkSql(reqVO.getTargetDatabase().trim(), reqVO.getTargetTable().trim(),
                mappingConfig, reqVO.getSinkSql() == null ? null : reqVO.getSinkSql().trim()));
        job.setSyncMode(reqVO.getSyncMode());
        job.setWatermarkColumn(reqVO.getWatermarkColumn());
        job.setWatermarkValue(reqVO.getWatermarkValue());
        job.setParallelism(reqVO.getParallelism());
        job.setStatus(reqVO.getStatus());
        job.setRemark(reqVO.getRemark());
        return job;
    }

    private SyncJobRespVO toResp(DataPlatformSyncJobDO job) {
        SyncJobRespVO respVO = new SyncJobRespVO();
        BeanUtils.copyProperties(job, respVO);
        respVO.setFieldMappings(mappingSqlBuilder.deserialize(job.getMappingConfig()));
        return respVO;
    }

    private String normalizeCreator(String creator) {
        String value = StringUtils.hasText(creator) ? creator.trim() : "system";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
