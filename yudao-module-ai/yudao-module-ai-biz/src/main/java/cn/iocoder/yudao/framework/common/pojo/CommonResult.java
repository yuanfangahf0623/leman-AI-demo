package cn.iocoder.yudao.framework.common.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * Common API result.
 */
@Data
public class CommonResult<T> implements Serializable {

    public static final Integer SUCCESS_CODE = 0;

    private Integer code;
    private String msg;
    private T data;

    public static <T> CommonResult<T> success(T data) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(SUCCESS_CODE);
        result.setMsg("");
        result.setData(data);
        return result;
    }

}
