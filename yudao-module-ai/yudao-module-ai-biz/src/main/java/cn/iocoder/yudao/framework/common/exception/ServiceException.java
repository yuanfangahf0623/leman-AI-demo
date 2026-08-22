package cn.iocoder.yudao.framework.common.exception;

import lombok.Getter;

/**
 * Business exception.
 */
@Getter
public class ServiceException extends RuntimeException {

    private final Integer code;

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

}
