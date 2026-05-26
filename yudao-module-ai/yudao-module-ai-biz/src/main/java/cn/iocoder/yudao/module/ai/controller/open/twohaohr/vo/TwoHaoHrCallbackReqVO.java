package cn.iocoder.yudao.module.ai.controller.open.twohaohr.vo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 2hao HR callback request.
 */
@Data
public class TwoHaoHrCallbackReqVO {

    @NotBlank(message = "event key must not be blank")
    private String key;

    private JsonNode data;

}
