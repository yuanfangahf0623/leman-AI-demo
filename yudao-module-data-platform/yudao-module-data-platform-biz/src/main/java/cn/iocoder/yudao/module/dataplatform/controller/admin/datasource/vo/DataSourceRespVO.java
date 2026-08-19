package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataSourceRespVO {
    private Long id;
    private String name;
    private String code;
    private String category;
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private Boolean passwordConfigured;
    private String jdbcParams;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
