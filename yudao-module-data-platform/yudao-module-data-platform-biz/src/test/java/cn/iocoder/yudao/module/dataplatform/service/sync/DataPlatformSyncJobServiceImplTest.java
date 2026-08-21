package cn.iocoder.yudao.module.dataplatform.service.sync;

import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformJobRunDO;
import cn.iocoder.yudao.module.dataplatform.dal.dataobject.DataPlatformSyncJobDO;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformJobRunMapper;
import cn.iocoder.yudao.module.dataplatform.dal.mysql.DataPlatformSyncJobMapper;
import cn.iocoder.yudao.module.dataplatform.service.datasource.DataPlatformDataSourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlatformSyncJobServiceImplTest {

    @Mock
    private DataPlatformSyncJobMapper jobMapper;
    @Mock
    private DataPlatformJobRunMapper runMapper;
    @Mock
    private DataPlatformDataSourceService dataSourceService;
    @Mock
    private SeaTunnelJobExecutor executor;
    @Mock
    private SyncFieldMappingSqlBuilder mappingSqlBuilder;
    @InjectMocks
    private DataPlatformSyncJobServiceImpl service;

    @Test
    void shouldLimitRunCreatorToDatabaseColumnLength() {
        DataPlatformSyncJobDO job = new DataPlatformSyncJobDO();
        job.setId(1L);
        job.setName("ERP 同步");
        job.setCode("erp_sync");
        job.setStatus(0);
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(runMapper.selectRunningCount(1L)).thenReturn(0L);
        when(runMapper.insert(any(DataPlatformJobRunDO.class))).thenAnswer(invocation -> {
            invocation.<DataPlatformJobRunDO>getArgument(0).setId(10L);
            return 1;
        });

        assertEquals(10L, service.execute(1L, "user:" + "x".repeat(100)));

        ArgumentCaptor<DataPlatformJobRunDO> captor = ArgumentCaptor.forClass(DataPlatformJobRunDO.class);
        verify(runMapper).insert(captor.capture());
        assertEquals(64, captor.getValue().getCreator().length());
        verify(executor).executeAsync(10L);
    }
}
