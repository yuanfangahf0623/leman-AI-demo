package cn.iocoder.yudao.module.system.dal.mysql;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface SharedHrMapper {
    @Select("SELECT organization_id,organization_name,parent_organization_id,source_organization_id,batch_id " +
            "FROM system_hr_organization WHERE tenant_id=#{tenant} ORDER BY organization_id")
    List<Map<String,Object>> organizations(@Param("tenant") Long tenant);

    @Select("SELECT person_id,employee_no,employee_name,organization_id,employment_status,batch_id " +
            "FROM system_hr_person WHERE tenant_id=#{tenant} ORDER BY person_id LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> people(@Param("tenant") Long tenant, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT batch_id,organization_count,person_count,updated_at FROM system_hr_release WHERE tenant_id=#{tenant}")
    Map<String,Object> status(@Param("tenant") Long tenant);
}
