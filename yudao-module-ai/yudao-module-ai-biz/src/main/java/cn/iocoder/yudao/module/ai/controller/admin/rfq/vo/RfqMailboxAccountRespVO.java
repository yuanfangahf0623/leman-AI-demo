package cn.iocoder.yudao.module.ai.controller.admin.rfq.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RFQ mailbox account response with masked password only.
 */
@Data
public class RfqMailboxAccountRespVO {

    private Long id;

    private Long tenantId;

    private String account;

    private String emailAddress;

    private String host;

    private Integer port;

    private String username;

    private String passwordMask;

    private String folder;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
