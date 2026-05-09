package cn.iocoder.yudao.module.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同步记录动作类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SyncRecordActionTypeEnum {

    CREATE("CREATE", "新增"),
    UPDATE("UPDATE", "更新"),
    SKIP("SKIP", "跳过"),
    ERROR("ERROR", "异常");

    private final String code;
    private final String name;

}
