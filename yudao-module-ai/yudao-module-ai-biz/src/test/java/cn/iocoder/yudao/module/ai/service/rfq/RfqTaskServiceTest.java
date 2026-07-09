package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.dal.dataobject.RfqDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.RfqTaskDO;
import cn.iocoder.yudao.module.ai.dal.mysql.RfqTaskMapper;
import cn.iocoder.yudao.module.ai.enums.RfqConstants;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfqTaskServiceTest {

    @Mock
    private RfqTaskMapper rfqTaskMapper;

    @Test
    void generateTasksShouldCreateDefaultRoleTasks() {
        RfqTaskService service = new RfqTaskService(rfqTaskMapper);
        when(rfqTaskMapper.selectListByRfqIdAndTenantId(100L, 1L)).thenReturn(List.of());

        service.generateTasks(buildRfq(), HermesRfqDTO.builder()
                .missingInfo(List.of("drawing"))
                .build());

        ArgumentCaptor<RfqTaskDO> captor = ArgumentCaptor.forClass(RfqTaskDO.class);
        verify(rfqTaskMapper, org.mockito.Mockito.times(4)).insert(captor.capture());
        List<String> taskTypes = captor.getAllValues().stream().map(RfqTaskDO::getTaskType).toList();
        assertEquals(List.of(RfqConstants.TASK_TYPE_ENGINEERING, RfqConstants.TASK_TYPE_COSTING,
                RfqConstants.TASK_TYPE_PROCUREMENT, RfqConstants.TASK_TYPE_SALES), taskTypes);
        for (RfqTaskDO task : captor.getAllValues()) {
            assertEquals(1L, task.getTenantId());
            assertEquals(100L, task.getRfqId());
            assertEquals(RfqConstants.TASK_STATUS_PENDING, task.getStatus());
        }
    }

    @Test
    void generateTasksShouldNotDuplicateExistingTasks() {
        RfqTaskService service = new RfqTaskService(rfqTaskMapper);
        when(rfqTaskMapper.selectListByRfqIdAndTenantId(100L, 1L))
                .thenReturn(List.of(new RfqTaskDO()));

        List<RfqTaskDO> tasks = service.generateTasks(buildRfq(), HermesRfqDTO.builder().build());

        assertEquals(1, tasks.size());
        verify(rfqTaskMapper, never()).insert(any(RfqTaskDO.class));
    }

    private RfqDO buildRfq() {
        RfqDO rfq = new RfqDO();
        rfq.setId(100L);
        rfq.setTenantId(1L);
        rfq.setProduct("CNC part");
        return rfq;
    }

}
