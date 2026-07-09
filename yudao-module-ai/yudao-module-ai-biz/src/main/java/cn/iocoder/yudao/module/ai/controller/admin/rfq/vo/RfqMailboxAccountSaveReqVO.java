package cn.iocoder.yudao.module.ai.controller.admin.rfq.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

/**
 * RFQ mailbox account save request.
 */
@Data
@ToString(exclude = "password")
public class RfqMailboxAccountSaveReqVO {

    private Long id;

    @NotBlank(message = "account is required")
    private String account;

    @NotBlank(message = "emailAddress is required")
    private String emailAddress;

    @NotBlank(message = "host is required")
    private String host;

    @NotNull(message = "port is required")
    private Integer port;

    @NotBlank(message = "username is required")
    private String username;

    /**
     * Required for create. Optional for update; empty keeps the old encrypted password.
     */
    private String password;

    private String folder;

    private Boolean enabled;

}
