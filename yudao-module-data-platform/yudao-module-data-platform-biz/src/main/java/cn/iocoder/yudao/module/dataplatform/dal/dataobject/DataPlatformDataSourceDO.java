package cn.iocoder.yudao.module.dataplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dp_data_source")
public class DataPlatformDataSourceDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String type;
    private String host;
    private Integer port;
    @TableField("database_name")
    private String databaseName;
    private String username;
    @TableField("password_cipher")
    private String passwordCipher;
    @TableField("jdbc_params")
    private String jdbcParams;
    private Integer status;
    private String remark;
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
