package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.TwoHaoHrAttendanceStatRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiTwoHaoHrAttendanceRecordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiTwoHaoHrAttendanceRecordMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoHaoHrAttendanceStatServiceImplTest {

    @Mock
    private AiTwoHaoHrAttendanceRecordMapper attendanceRecordMapper;
    @Mock
    private AiDataSourceMapper dataSourceMapper;

    private TwoHaoHrAttendanceStatServiceImpl service;

    @BeforeEach
    void setUp() {
        AiUserContextHolder.setUserContext(1L, 100L, 20L);
        service = new TwoHaoHrAttendanceStatServiceImpl(attendanceRecordMapper, dataSourceMapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        AiUserContextHolder.clear();
    }

    @Test
    void getDepartmentStatShouldExtractDepartmentHintFromQuestion() {
        when(dataSourceMapper.selectListByTenantIdAndKnowledgeBaseIds(eq(1L), eq(List.of(13L))))
                .thenReturn(List.of(AiDataSourceDO.builder()
                        .id(7L)
                        .tenantId(1L)
                        .knowledgeBaseId(13L)
                        .configJson("{\"provider\":\"two-hao-hr\"}")
                        .build()));
        when(attendanceRecordMapper.selectStatMaps(any()))
                .thenReturn(List.of(Map.of(
                                "department_name", "制造中心/生产制造部/一车间",
                                "record_count", 100L)),
                        List.of(Map.of(
                                "total_records", 80L,
                                "employee_count", 8L,
                                "min_attendance_date", LocalDate.of(2026, 5, 1),
                                "max_attendance_date", LocalDate.of(2026, 5, 25))),
                        List.of(Map.of(
                                "record_type", "attendance_card_record",
                                "record_count", 80L,
                                "employee_count", 8L)),
                        List.of(),
                        List.of(),
                        List.of(Map.of(
                                "department_name", "制造中心/生产制造部/一车间",
                                "record_count", 80L,
                                "employee_count", 8L)));

        TwoHaoHrAttendanceStatReqVO reqVO = new TwoHaoHrAttendanceStatReqVO();
        reqVO.setKnowledgeBaseId(13L);
        reqVO.setDepartmentKeyword("统计一下生产制造部上月的考勤情况");

        TwoHaoHrAttendanceStatRespVO respVO = service.getDepartmentStat(reqVO);

        assertEquals("DEPARTMENT_NAME_CONTAINS", respVO.getDepartmentMatchType());
        assertEquals("生产制造部", respVO.getDepartmentName());
        assertEquals(1, respVO.getMatchedDepartmentCount());
        assertEquals(80L, respVO.getTotalRecords());
        assertEquals(8L, respVO.getEmployeeCount());
    }

    @Test
    void getDepartmentStatShouldNotFallbackToAllWhenQuestionHasDepartmentHint() {
        when(dataSourceMapper.selectListByTenantIdAndKnowledgeBaseIds(eq(1L), eq(List.of(13L))))
                .thenReturn(List.of(AiDataSourceDO.builder()
                        .id(7L)
                        .tenantId(1L)
                        .knowledgeBaseId(13L)
                        .configJson("{\"provider\":\"two-hao-hr\"}")
                        .build()));
        when(attendanceRecordMapper.selectStatMaps(any()))
                .thenReturn(List.of(),
                        List.of(Map.of(
                                "total_records", 12L,
                                "employee_count", 3L)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        TwoHaoHrAttendanceStatReqVO reqVO = new TwoHaoHrAttendanceStatReqVO();
        reqVO.setKnowledgeBaseId(13L);
        reqVO.setDepartmentKeyword("统计一下生产制造部上月的考勤情况");

        TwoHaoHrAttendanceStatRespVO respVO = service.getDepartmentStat(reqVO);

        assertEquals("DEPARTMENT_NAME_CONTAINS", respVO.getDepartmentMatchType());
        assertEquals("生产制造部", respVO.getDepartmentName());
        assertEquals(12L, respVO.getTotalRecords());
        assertEquals(3L, respVO.getEmployeeCount());
    }

    @Test
    void getDepartmentStatShouldFilterByEmployeeName() {
        when(dataSourceMapper.selectListByTenantIdAndKnowledgeBaseIds(eq(1L), eq(List.of(13L))))
                .thenReturn(List.of(AiDataSourceDO.builder()
                        .id(7L)
                        .tenantId(1L)
                        .knowledgeBaseId(13L)
                        .configJson("{\"provider\":\"two-hao-hr\"}")
                        .build()));
        when(attendanceRecordMapper.selectStatMaps(any()))
                .thenReturn(List.of(Map.of(
                                "total_records", 9L,
                                "employee_count", 1L,
                                "min_attendance_date", LocalDate.of(2026, 5, 1),
                                "max_attendance_date", LocalDate.of(2026, 5, 25))),
                        List.of(Map.of(
                                "record_type", "attendance_card_record",
                                "record_count", 9L,
                                "employee_count", 1L)),
                        List.of(),
                        List.of(),
                        List.of(Map.of(
                                "department_name", "行政部",
                                "record_count", 9L,
                                "employee_count", 1L)));

        TwoHaoHrAttendanceStatReqVO reqVO = new TwoHaoHrAttendanceStatReqVO();
        reqVO.setKnowledgeBaseId(13L);
        reqVO.setEmployeeName("袁方");

        TwoHaoHrAttendanceStatRespVO respVO = service.getDepartmentStat(reqVO);

        assertEquals("EMPLOYEE_NAME_CONTAINS", respVO.getEmployeeMatchType());
        assertEquals("袁方", respVO.getEmployeeName());
        assertEquals(9L, respVO.getTotalRecords());
        assertEquals(1L, respVO.getEmployeeCount());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<QueryWrapper<AiTwoHaoHrAttendanceRecordDO>> queryCaptor =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(attendanceRecordMapper, times(5)).selectStatMaps(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().get(0).getSqlSegment().contains("employee_name"));
    }

}
