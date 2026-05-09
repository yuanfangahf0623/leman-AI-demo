package cn.iocoder.yudao.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员用户 DO。
 */
@TableName("system_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemUserDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("username")
    private String username;
    @TableField("password")
    private String password;
    @TableField("nickname")
    private String nickname;
    @TableField("remark")
    private String remark;
    @TableField("dept_id")
    private Long deptId;
    @TableField("email")
    private String email;
    @TableField("mobile")
    private String mobile;
    @TableField("sex")
    private Integer sex;
    @TableField("avatar")
    private String avatar;
    @TableField("status")
    private Integer status;
    @TableField("login_ip")
    private String loginIp;
    @TableField("login_date")
    private LocalDateTime loginDate;
    @TableField("creator")
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("updater")
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

}
