package cn.iocoder.yudao.module.system.dal.mysql;

import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemHrIdentityMapper {
    @Select("SELECT user_id FROM system_hr_identity WHERE tenant_id=#{tenant} AND login_digest=#{digest}")
    Long findUserId(@Param("tenant") Long tenant, @Param("digest") String digest);

    @Select("SELECT COUNT(*) FROM system_hr_identity WHERE tenant_id=#{tenant} AND user_id=#{user}")
    int isImported(@Param("tenant") Long tenant, @Param("user") Long user);

    @Select("SELECT COUNT(*) FROM system_hr_identity WHERE tenant_id=#{tenant} AND user_id=#{user} AND requires_password_change=1")
    int requiresChange(@Param("tenant") Long tenant, @Param("user") Long user);

    @Select("SELECT COUNT(*) FROM system_users WHERE tenant_id=#{tenant} AND id=#{user} AND deleted=0 AND status=0")
    int isEnabled(@Param("tenant") Long tenant, @Param("user") Long user);

    @Update("UPDATE system_hr_identity SET requires_password_change=0 WHERE tenant_id=#{tenant} AND user_id=#{user}")
    int clearChange(@Param("tenant") Long tenant, @Param("user") Long user);
}
