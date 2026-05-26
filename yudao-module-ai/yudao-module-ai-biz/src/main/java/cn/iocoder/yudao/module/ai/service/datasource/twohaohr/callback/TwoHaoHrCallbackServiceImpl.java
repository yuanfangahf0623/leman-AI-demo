package cn.iocoder.yudao.module.ai.service.datasource.twohaohr.callback;

import cn.iocoder.yudao.module.ai.controller.open.twohaohr.vo.TwoHaoHrCallbackReqVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSyncJobMapper;
import cn.iocoder.yudao.module.ai.enums.DataSourceTypeEnum;
import cn.iocoder.yudao.module.ai.enums.SyncJobStatusEnum;
import cn.iocoder.yudao.module.ai.enums.SyncJobTypeEnum;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.datasource.twohaohr.TwoHaoHrDataSourceConfig;
import cn.iocoder.yudao.module.ai.service.sync.KnowledgeSyncService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Receives 2hao HR event callbacks and turns them into scoped incremental sync jobs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwoHaoHrCallbackServiceImpl implements TwoHaoHrCallbackService {

    private static final String TRIGGER_PREFIX = "CALLBACK:";
    private static final String CALLBACK_CREATOR = "twohaohr-callback";
    private static final Integer DEFAULT_COUNT = 0;

    private final AiDataSourceMapper dataSourceMapper;
    private final AiSyncJobMapper syncJobMapper;
    private final KnowledgeSyncService knowledgeSyncService;
    private final ObjectMapper objectMapper;

    @Value("${TWO_HAO_HR_CALLBACK_TOKEN:}")
    private String callbackToken;

    @Override
    public void acceptCallback(TwoHaoHrCallbackReqVO reqVO, Long tenantId, Long dataSourceId, String token,
                               String userAgent) {
        validateToken(token);
        String eventKey = normalize(reqVO.getKey());
        Set<String> syncObjects = resolveSyncObjects(eventKey);
        int dataCount = countData(reqVO.getData());
        log.info("2hao HR callback accepted, eventKey={}, tenantId={}, dataSourceId={}, dataCount={}, userAgent={}",
                eventKey, tenantId, dataSourceId, dataCount, abbreviate(userAgent, 120));
        if (syncObjects.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> dispatchSync(eventKey, syncObjects, tenantId, dataSourceId));
    }

    private void dispatchSync(String eventKey, Set<String> syncObjects, Long tenantId, Long dataSourceId) {
        List<AiDataSourceDO> dataSources = selectTwoHaoHrDataSources(tenantId, dataSourceId);
        if (dataSources.isEmpty()) {
            log.warn("2hao HR callback has no matching data source, eventKey={}, tenantId={}, dataSourceId={}",
                    eventKey, tenantId, dataSourceId);
            return;
        }
        for (AiDataSourceDO dataSource : dataSources) {
            try {
                AiTenantContextHolder.setTenantId(dataSource.getTenantId());
                Long jobId = createCallbackSyncJob(dataSource, syncObjects);
                knowledgeSyncService.executeSyncJob(jobId);
                log.info("2hao HR callback sync finished, eventKey={}, syncJobId={}, tenantId={}, dataSourceId={}, syncObjects={}",
                        eventKey, jobId, dataSource.getTenantId(), dataSource.getId(), syncObjects);
            } catch (Exception ex) {
                log.warn("2hao HR callback sync failed, eventKey={}, tenantId={}, dataSourceId={}, syncObjects={}, errorType={}",
                        eventKey, dataSource.getTenantId(), dataSource.getId(), syncObjects, ex.getClass().getSimpleName());
            } finally {
                AiTenantContextHolder.clear();
            }
        }
    }

    private Long createCallbackSyncJob(AiDataSourceDO dataSource, Set<String> syncObjects) {
        AiSyncJobDO syncJob = new AiSyncJobDO();
        syncJob.setTenantId(dataSource.getTenantId());
        syncJob.setKnowledgeBaseId(dataSource.getKnowledgeBaseId());
        syncJob.setDataSourceId(dataSource.getId());
        syncJob.setJobType(SyncJobTypeEnum.INCREMENTAL.getCode());
        syncJob.setTriggerType(TRIGGER_PREFIX + String.join(",", syncObjects));
        syncJob.setStatus(SyncJobStatusEnum.PENDING.getCode());
        syncJob.setTotalCount(DEFAULT_COUNT);
        syncJob.setSuccessCount(DEFAULT_COUNT);
        syncJob.setFailCount(DEFAULT_COUNT);
        syncJob.setCreator(CALLBACK_CREATOR);
        syncJob.setUpdater(CALLBACK_CREATOR);
        syncJobMapper.insert(syncJob);
        return syncJob.getId();
    }

    private List<AiDataSourceDO> selectTwoHaoHrDataSources(Long tenantId, Long dataSourceId) {
        return dataSourceMapper.selectList(Wrappers.lambdaQuery(AiDataSourceDO.class)
                        .eq(AiDataSourceDO::getType, DataSourceTypeEnum.API.getCode())
                        .eq(tenantId != null, AiDataSourceDO::getTenantId, tenantId)
                        .eq(dataSourceId != null, AiDataSourceDO::getId, dataSourceId)
                        .orderByAsc(AiDataSourceDO::getId))
                .stream()
                .filter(this::isTwoHaoHrDataSource)
                .toList();
    }

    private boolean isTwoHaoHrDataSource(AiDataSourceDO dataSource) {
        if (!hasText(dataSource.getConfigJson())) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(dataSource.getConfigJson());
            return TwoHaoHrDataSourceConfig.isSupportedProvider(root.path("provider").asText(null));
        } catch (Exception ex) {
            log.warn("Ignore invalid 2hao HR data source config, tenantId={}, dataSourceId={}",
                    dataSource.getTenantId(), dataSource.getId());
            return false;
        }
    }

    private Set<String> resolveSyncObjects(String eventKey) {
        Set<String> result = new LinkedHashSet<>();
        if ("event_test".equals(eventKey)) {
            return result;
        }
        if (eventKey.startsWith("employee_") || eventKey.startsWith("intent_employee_")
                || "sign_electronic_contract".equals(eventKey)) {
            result.add("employees");
            return result;
        }
        if (eventKey.startsWith("dept_") || "company_leader_update".equals(eventKey)) {
            result.add("departments");
            return result;
        }
        if ("approve_result".equals(eventKey)) {
            result.add("approvals");
            return result;
        }
        log.info("2hao HR callback event is not mapped to current sync objects, eventKey={}", eventKey);
        return result;
    }

    private void validateToken(String token) {
        if (!hasText(callbackToken)) {
            return;
        }
        if (!callbackToken.trim().equals(token == null ? null : token.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid callback token");
        }
    }

    private int countData(JsonNode data) {
        if (data == null || data.isNull()) {
            return 0;
        }
        if (data.isArray() || data.isObject()) {
            return data.size();
        }
        return 1;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
