package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceRawRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceRawRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoHaoHrLeaveEmployeeListServiceImplTest {

    @Mock
    private AiDataSourceRawRecordMapper rawRecordMapper;

    private TwoHaoHrLeaveEmployeeListServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TwoHaoHrLeaveEmployeeListServiceImpl(rawRecordMapper, new ObjectMapper());
    }

    @Test
    void listEmployeesShouldReturnFullNameAndFilterByLeaveDate() {
        when(rawRecordMapper.selectList(any())).thenReturn(List.of(
                AiDataSourceRawRecordDO.builder()
                        .id(1L)
                        .tenantId(1L)
                        .dataSourceId(7L)
                        .payloadJson("""
                                {"id":"e1","name":"张三","emp_no":"1001","leave_date":"2026-05-21","leave_type_name":"主动离职","leave_reason":"个人原因","department_id":"d1"}
                                """)
                        .build(),
                AiDataSourceRawRecordDO.builder()
                        .id(2L)
                        .tenantId(1L)
                        .dataSourceId(7L)
                        .payloadJson("""
                                {"id":"e2","name":"李四","emp_no":"1002","leave_date":"2026-04-30","leave_type_name":"主动离职","leave_reason":"个人原因","department_id":"d2"}
                                """)
                        .build()));

        TwoHaoHrLeaveEmployeeListService.LeaveEmployeeListResult result = service.listEmployees(1L,
                AiDataSourceDO.builder().id(7L).knowledgeBaseId(10L).build(),
                TwoHaoHrLeaveEmployeeListService.LEAVE_EMPLOYEE_LIST,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31));

        assertEquals(2L, result.rawRecordCount());
        assertEquals(1, result.employees().size());
        assertEquals("张三", result.employees().get(0).name());
        assertEquals("1001", result.employees().get(0).employeeNo());
    }

}
