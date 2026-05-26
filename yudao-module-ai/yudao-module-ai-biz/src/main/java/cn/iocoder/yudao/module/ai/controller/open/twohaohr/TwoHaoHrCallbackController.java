package cn.iocoder.yudao.module.ai.controller.open.twohaohr;

import cn.iocoder.yudao.module.ai.controller.open.twohaohr.vo.TwoHaoHrCallbackReqVO;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.callback.TwoHaoHrCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public callback endpoint for 2hao HR.
 */
@RestController
@RequestMapping("/open-api/ai/twohaohr")
@Validated
@RequiredArgsConstructor
public class TwoHaoHrCallbackController {

    private static final String RESULT_CODE = "result_code";
    private static final String RESULT_MSG = "result_msg";
    private static final String SUCCESS = "SUCCESS";
    private static final String OK = "OK";

    private final TwoHaoHrCallbackService callbackService;

    @PostMapping("/callback")
    public Map<String, String> callback(@Valid @RequestBody TwoHaoHrCallbackReqVO reqVO,
                                        @RequestParam(value = "tenantId", required = false) Long tenantId,
                                        @RequestParam(value = "dataSourceId", required = false) Long dataSourceId,
                                        @RequestParam(value = "token", required = false) String queryToken,
                                        @RequestHeader(value = "X-2HaoHr-Token", required = false) String headerToken,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        callbackService.acceptCallback(reqVO, tenantId, dataSourceId, firstText(queryToken, headerToken), userAgent);
        Map<String, String> response = new LinkedHashMap<>();
        response.put(RESULT_CODE, SUCCESS);
        response.put(RESULT_MSG, OK);
        return response;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
