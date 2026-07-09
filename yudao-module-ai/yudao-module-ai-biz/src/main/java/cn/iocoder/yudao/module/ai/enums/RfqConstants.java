package cn.iocoder.yudao.module.ai.enums;

import java.util.Set;

/**
 * RFQ constants.
 */
public interface RfqConstants {

    String STATUS_NEW = "NEW";
    String STATUS_ANALYZING = "ANALYZING";
    String STATUS_COSTING = "COSTING";
    String STATUS_QUOTED = "QUOTED";
    String STATUS_SENT = "SENT";

    Set<String> RFQ_STATUSES = Set.of(STATUS_NEW, STATUS_ANALYZING, STATUS_COSTING, STATUS_QUOTED, STATUS_SENT);

    String TASK_TYPE_ENGINEERING = "ENGINEERING";
    String TASK_TYPE_COSTING = "COSTING";
    String TASK_TYPE_PROCUREMENT = "PROCUREMENT";
    String TASK_TYPE_SALES = "SALES";

    String TASK_STATUS_PENDING = "PENDING";

}
