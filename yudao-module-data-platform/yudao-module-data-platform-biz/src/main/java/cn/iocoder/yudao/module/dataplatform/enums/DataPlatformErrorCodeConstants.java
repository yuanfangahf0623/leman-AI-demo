package cn.iocoder.yudao.module.dataplatform.enums;

public final class DataPlatformErrorCodeConstants {

    public static final int DATA_SOURCE_NOT_EXISTS = 1_520_001;
    public static final int DATA_SOURCE_TYPE_UNSUPPORTED = 1_520_002;
    public static final int DATA_SOURCE_CONNECT_FAILED = 1_520_003;
    public static final int SECRET_KEY_INVALID = 1_520_004;
    public static final int SYNC_JOB_NOT_EXISTS = 1_520_101;
    public static final int SYNC_JOB_RUNNING = 1_520_102;
    public static final int SYNC_JOB_CONFIG_INVALID = 1_520_103;
    public static final int SYNC_JOB_EXECUTE_FAILED = 1_520_104;
    public static final int WAREHOUSE_CONNECT_FAILED = 1_520_201;

    private DataPlatformErrorCodeConstants() {
    }
}
