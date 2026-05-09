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
 * 菜单权限 DO。
 */
@TableName("system_menu")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMenuDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("name")
    private String name;
    @TableField("permission")
    private String permission;
    @TableField("type")
    private Integer type;
    @TableField("sort")
    private Integer sort;
    @TableField("parent_id")
    private Long parentId;
    @TableField("path")
    private String path;
    @TableField("icon")
    private String icon;
    @TableField("component")
    private String component;
    @TableField("component_name")
    private String componentName;
    @TableField("status")
    private Integer status;
    @TableField("visible")
    private Boolean visible;
    @TableField("keep_alive")
    private Boolean keepAlive;
    @TableField("always_show")
    private Boolean alwaysShow;
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
