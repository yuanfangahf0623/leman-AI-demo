package cn.iocoder.yudao.module.ai.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.datasource.vo.AiDataSourceRawRecordRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiDataSourceRawRecordDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.AiSyncJobDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiDataSourceRawRecordMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI data source raw record service implementation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDataSourceRawRecordServiceImpl implements AiDataSourceRawRecordService {

    private static final int DEFAULT_STATUS = 0;
    private static final int EXTERNAL_ID_MAX_LENGTH = 255;
    private static final Set<String> NAME_FIELDS = Set.of("name", "employee_name", "emp_name", "staff_name",
            "user_name", "real_name", "target_name", "leader_name", "approver", "applicant_name");
    private static final Set<String> MOBILE_FIELDS = Set.of("mobile", "phone", "telephone", "cellphone",
            "contact_mobile", "contact_phone");
    private static final Set<String> ID_CARD_FIELDS = Set.of("credentials_no", "credential_no", "id_card",
            "id_card_no", "identity_card", "identity_no", "cert_no", "certificate_no");
    private static final Set<String> BANK_FIELDS = Set.of("bank_card", "bank_card_no", "bank_no", "account",
            "account_no", "card_no", "card_number");
    private static final Set<String> ADDRESS_FIELDS = Set.of("address", "home_address", "registered_address",
            "residence_address");
    private static final Set<String> BIRTH_FIELDS = Set.of("birthday", "birth_date");
    private static final Set<String> SALARY_FIELDS = Set.of("salary", "amount", "pay", "wage", "income",
            "bonus", "tax", "pre_tax", "after_tax");

    private final AiDataSourceRawRecordMapper rawRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecords(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String provider, String moduleName,
                            String objectType, String sourceUri, List<JsonNode> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (JsonNode record : records) {
            saveOne(syncJob, dataSource, provider, moduleName, objectType, sourceUri, record);
        }
        log.info("AI data source raw records saved, tenantId={}, knowledgeBaseId={}, dataSourceId={}, provider={}, objectType={}, count={}",
                syncJob.getTenantId(), syncJob.getKnowledgeBaseId(), dataSource.getId(), provider, objectType,
                records.size());
    }

    @Override
    public PageResult<AiDataSourceRawRecordRespVO> getRawRecordPage(AiDataSourceRawRecordPageReqVO pageReqVO) {
        PageResult<AiDataSourceRawRecordDO> pageResult = rawRecordMapper.selectPage(pageReqVO,
                AiTenantContextHolder.getTenantId());
        List<AiDataSourceRawRecordRespVO> list = pageResult.getList().stream()
                .map(this::convertMasked)
                .toList();
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public String toMaskedJson(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mask(payload, null));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void saveOne(AiSyncJobDO syncJob, AiDataSourceDO dataSource, String provider, String moduleName,
                         String objectType, String sourceUri, JsonNode payload) {
        String payloadJson = toJson(payload);
        String payloadHash = sha256Hex(payloadJson);
        String externalId = resolveExternalId(payload, payloadHash);
        AiDataSourceRawRecordDO oldRecord = rawRecordMapper.selectByUniqueKey(syncJob.getTenantId(), dataSource.getId(),
                provider, objectType, externalId);
        AiDataSourceRawRecordDO record = AiDataSourceRawRecordDO.builder()
                .id(oldRecord == null ? null : oldRecord.getId())
                .tenantId(syncJob.getTenantId())
                .knowledgeBaseId(syncJob.getKnowledgeBaseId())
                .dataSourceId(dataSource.getId())
                .syncJobId(syncJob.getId())
                .provider(provider)
                .moduleName(moduleName)
                .objectType(objectType)
                .externalId(externalId)
                .sourceUri(sourceUri)
                .payloadJson(payloadJson)
                .payloadHash(payloadHash)
                .recordTime(LocalDateTime.now())
                .status(DEFAULT_STATUS)
                .build();
        if (oldRecord == null) {
            rawRecordMapper.insert(record);
        } else {
            rawRecordMapper.updateById(record);
        }
    }

    private AiDataSourceRawRecordRespVO convertMasked(AiDataSourceRawRecordDO record) {
        AiDataSourceRawRecordRespVO respVO = new AiDataSourceRawRecordRespVO();
        respVO.setId(record.getId());
        respVO.setKnowledgeBaseId(record.getKnowledgeBaseId());
        respVO.setDataSourceId(record.getDataSourceId());
        respVO.setSyncJobId(record.getSyncJobId());
        respVO.setProvider(record.getProvider());
        respVO.setModuleName(record.getModuleName());
        respVO.setObjectType(record.getObjectType());
        respVO.setExternalId(record.getExternalId());
        respVO.setSourceUri(record.getSourceUri());
        respVO.setPayloadJson(maskPayloadJson(record.getPayloadJson()));
        respVO.setPayloadHash(record.getPayloadHash());
        respVO.setRecordTime(record.getRecordTime());
        respVO.setStatus(record.getStatus());
        respVO.setCreateTime(record.getCreateTime());
        respVO.setUpdateTime(record.getUpdateTime());
        return respVO;
    }

    private String maskPayloadJson(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return "";
        }
        try {
            return toMaskedJson(objectMapper.readTree(payloadJson));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private JsonNode mask(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode masked = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                masked.set(entry.getKey(), mask(entry.getValue(), entry.getKey()));
            }
            return masked;
        }
        if (node.isArray()) {
            ArrayNode masked = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                masked.add(mask(item, fieldName));
            }
            return masked;
        }
        if (!node.isTextual() && !node.isNumber()) {
            return node;
        }
        String value = node.asText("");
        String normalizedField = normalizeField(fieldName);
        if (NAME_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf(maskName(value));
        }
        if (MOBILE_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf(maskMiddle(value, 3, 4));
        }
        if (ID_CARD_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf(maskMiddle(value, 6, 4));
        }
        if (BANK_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf(maskMiddle(value, 4, 4));
        }
        if ("email".equals(normalizedField)) {
            return TextNode.valueOf(maskEmail(value));
        }
        if (ADDRESS_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf(maskMiddle(value, 6, 0));
        }
        if (BIRTH_FIELDS.contains(normalizedField)) {
            return TextNode.valueOf("****-**-**");
        }
        if (SALARY_FIELDS.contains(normalizedField) || containsAny(normalizedField, SALARY_FIELDS)) {
            return TextNode.valueOf("***");
        }
        return node;
    }

    private boolean containsAny(String fieldName, Set<String> fragments) {
        for (String fragment : fragments) {
            if (fieldName.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeField(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
    }

    private String maskMiddle(String value, int prefix, int suffix) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= prefix + suffix) {
            return "***";
        }
        String left = trimmed.substring(0, Math.min(prefix, trimmed.length()));
        String right = suffix > 0 ? trimmed.substring(trimmed.length() - suffix) : "";
        return left + "****" + right;
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 1) {
            return "*";
        }
        return trimmed.charAt(0) + "*";
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            return maskMiddle(value, 1, 0);
        }
        int atIndex = value.indexOf('@');
        return value.substring(0, Math.min(1, atIndex)) + "***" + value.substring(atIndex);
    }

    private String resolveExternalId(JsonNode payload, String payloadHash) {
        List<String> candidateFields = List.of("id", "uuid", "entry_id", "employee_id", "emp_id", "department_id",
                "job_id", "plan_id", "task_id", "no", "code");
        if (payload != null && payload.isObject()) {
            for (String field : candidateFields) {
                String value = payload.path(field).asText(null);
                if (value != null && !value.isBlank()) {
                    return abbreviate(value.trim(), EXTERNAL_ID_MAX_LENGTH);
                }
            }
        }
        return payloadHash;
    }

    private String toJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

}
