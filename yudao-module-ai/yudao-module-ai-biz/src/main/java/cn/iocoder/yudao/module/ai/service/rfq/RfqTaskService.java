package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.dal.dataobject.RfqDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.RfqTaskDO;
import cn.iocoder.yudao.module.ai.dal.mysql.RfqTaskMapper;
import cn.iocoder.yudao.module.ai.enums.RfqConstants;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * RFQ task generator.
 */
@Service
@RequiredArgsConstructor
public class RfqTaskService {

    private final RfqTaskMapper rfqTaskMapper;

    public List<RfqTaskDO> generateTasks(RfqDO rfq, HermesRfqDTO hermesRfq) {
        if (rfq == null || rfq.getId() == null) {
            return List.of();
        }
        List<RfqTaskDO> oldTasks = rfqTaskMapper.selectListByRfqIdAndTenantId(rfq.getId(), rfq.getTenantId());
        if (!oldTasks.isEmpty()) {
            return oldTasks;
        }
        List<RfqTaskDO> tasks = buildDefaultTasks(rfq, hermesRfq);
        for (RfqTaskDO task : tasks) {
            rfqTaskMapper.insert(task);
        }
        return tasks;
    }

    private List<RfqTaskDO> buildDefaultTasks(RfqDO rfq, HermesRfqDTO hermesRfq) {
        String product = firstText(rfq.getProduct(), "RFQ product");
        List<RfqTaskDO> tasks = new ArrayList<>(4);
        tasks.add(buildTask(rfq, RfqConstants.TASK_TYPE_ENGINEERING, "ENGINEERING",
                "Drawing analysis", "Review drawings, specifications, tolerances and missing technical inputs for " + product,
                1));
        tasks.add(buildTask(rfq, RfqConstants.TASK_TYPE_COSTING, "COSTING",
                "Cost estimation", "Estimate material, manufacturing, tooling and logistics cost for " + product,
                2));
        tasks.add(buildTask(rfq, RfqConstants.TASK_TYPE_PROCUREMENT, "PROCUREMENT",
                "Supplier inquiry", "Check supplier availability, MOQ, lead time and external quotation needs for " + product,
                2));
        tasks.add(buildTask(rfq, RfqConstants.TASK_TYPE_SALES, "SALES",
                "Customer communication", buildSalesDetail(hermesRfq), 1));
        return tasks;
    }

    private RfqTaskDO buildTask(RfqDO rfq, String taskType, String owner, String title, String detail, int dueDays) {
        RfqTaskDO task = new RfqTaskDO();
        task.setTenantId(rfq.getTenantId());
        task.setRfqId(rfq.getId());
        task.setTaskType(taskType);
        task.setStatus(RfqConstants.TASK_STATUS_PENDING);
        task.setOwner(owner);
        task.setTitle(title);
        task.setDetail(detail);
        task.setDueDate(LocalDate.now().plusDays(dueDays));
        task.setDeleted(false);
        return task;
    }

    private String buildSalesDetail(HermesRfqDTO hermesRfq) {
        if (hermesRfq == null || hermesRfq.getMissingInfo() == null || hermesRfq.getMissingInfo().isEmpty()) {
            return "Confirm RFQ scope, delivery target, commercial terms and quotation timeline with customer";
        }
        return "Confirm missing information with customer: " + String.join("; ", hermesRfq.getMissingInfo());
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

}
