package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;

import java.time.LocalDate;
import java.util.List;

public interface TwoHaoHrLeaveEmployeeListService {

    String LEAVE_EMPLOYEE_LIST = "leave_employee_list";
    String LEAVING_EMPLOYEE_LIST = "leaving_employee_list";

    LeaveEmployeeListResult listEmployees(Long tenantId, AiDataSourceDO dataSource, String objectType,
                                          LocalDate startDate, LocalDate endDate);

    record LeaveEmployeeListResult(String objectType, LocalDate startDate, LocalDate endDate,
                                   long rawRecordCount, List<LeaveEmployee> employees) {
    }

    record LeaveEmployee(String id, String employeeNo, String name, LocalDate leaveDate, String leaveTypeName,
                         String leaveReason, String departmentId) {
    }

}
