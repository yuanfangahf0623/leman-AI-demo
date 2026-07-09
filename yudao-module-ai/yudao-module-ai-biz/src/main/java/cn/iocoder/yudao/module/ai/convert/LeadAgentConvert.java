package cn.iocoder.yudao.module.ai.convert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketRespVO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlHistoryDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCustomerDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadExportRuleDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketCategoryDO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lead agent convert.
 */
public class LeadAgentConvert {

    public static final LeadAgentConvert INSTANCE = new LeadAgentConvert();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public LeadMarketRespVO convertMarket(LeadMarketCategoryDO category, List<String> countries, List<String> keywords) {
        if (category == null) {
            return null;
        }
        LeadMarketRespVO result = new LeadMarketRespVO();
        result.setId(category.getId());
        result.setCategoryCode(category.getCategoryCode());
        result.setCategoryName(category.getCategoryName());
        result.setWeight(category.getWeight());
        result.setEnabled(category.getEnabled());
        result.setCountries(countries == null ? List.of() : countries);
        result.setKeywords(keywords == null ? List.of() : keywords);
        result.setCreateTime(category.getCreateTime());
        result.setUpdateTime(category.getUpdateTime());
        return result;
    }

    public PageResult<LeadMarketRespVO> convertMarketPage(PageResult<LeadMarketCategoryDO> page,
                                                         Map<Long, List<String>> countriesByCategoryId,
                                                         Map<Long, List<String>> keywordsByCategoryId) {
        if (page == null) {
            return null;
        }
        List<LeadMarketRespVO> list = page.getList().stream()
                .map(item -> convertMarket(item, countriesByCategoryId.get(item.getId()),
                        keywordsByCategoryId.get(item.getId())))
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    public LeadExportRuleRespVO convertExportRule(LeadExportRuleDO bean) {
        LeadExportRuleRespVO result = new LeadExportRuleRespVO();
        if (bean == null) {
            result.setMinScore(0);
            result.setIncludeTargetOnly(false);
            result.setRequireEmail(false);
            result.setAllowedGrades(List.of("A", "B", "C", "D"));
            result.setIncludePossibleDuplicates(true);
            result.setWriteRejectedFile(true);
            return result;
        }
        result.setMinScore(bean.getMinScore());
        result.setIncludeTargetOnly(bean.getIncludeTargetOnly());
        result.setRequireEmail(bean.getRequireEmail());
        result.setAllowedGrades(parseGrades(bean.getAllowedGrades()));
        result.setIncludePossibleDuplicates(bean.getIncludePossibleDuplicates());
        result.setWriteRejectedFile(bean.getWriteRejectedFile());
        return result;
    }

    public LeadCustomerRespVO convertCustomer(LeadCustomerDO bean) {
        if (bean == null) {
            return null;
        }
        LeadCustomerRespVO result = new LeadCustomerRespVO();
        result.setId(bean.getId());
        result.setRunId(bean.getRunId());
        result.setCompanyName(bean.getCompanyName());
        result.setCountry(bean.getCountry());
        result.setWebsite(bean.getWebsite());
        result.setDomain(bean.getDomain());
        result.setEmails(readStringList(bean.getEmailsJson()));
        result.setBestEmail(bean.getBestEmail());
        result.setEmailType(bean.getEmailType());
        result.setPhone(bean.getPhone());
        result.setContactPage(bean.getContactPage());
        result.setAboutPage(bean.getAboutPage());
        result.setMainProducts(readStringList(bean.getMainProductsJson()));
        result.setTarget(bean.getTarget());
        result.setMatchedCategory(bean.getMatchedCategory());
        result.setTargetCustomerType(bean.getTargetCustomerType());
        result.setScore(bean.getScore());
        result.setGrade(gradeFromScore(bean.getScore()));
        result.setScoreReason(bean.getScoreReason());
        result.setSourceUrl(bean.getSourceUrl());
        result.setCollectedAt(bean.getCollectedAt());
        result.setComplianceNote(bean.getComplianceNote());
        result.setDevelopmentEmailSubject(bean.getDevelopmentEmailSubject());
        result.setDevelopmentEmailBody(bean.getDevelopmentEmailBody());
        result.setDuplicateStatus(bean.getDuplicateStatus());
        result.setCrawlErrors(readStringList(bean.getCrawlErrorsJson()));
        result.setAnalysisProvider(bean.getAnalysisProvider());
        result.setAiReviewError(bean.getAiReviewError());
        result.setSocialProfiles(readMapList(bean.getSocialProfilesJson()));
        result.setSocialActivitySummary(bean.getSocialActivitySummary());
        result.setSocialVerificationScore(bean.getSocialVerificationScore());
        result.setSocialVerificationReason(bean.getSocialVerificationReason());
        result.setExternalAppearanceCount(bean.getExternalAppearanceCount());
        result.setExternalAppearanceUrls(readStringList(bean.getExternalAppearanceUrlsJson()));
        result.setDemoStatus(bean.getDemoStatus());
        return result;
    }

    public PageResult<LeadCustomerRespVO> convertCustomerPage(PageResult<LeadCustomerDO> page) {
        if (page == null) {
            return null;
        }
        return new PageResult<>(page.getList().stream().map(this::convertCustomer).toList(), page.getTotal());
    }

    public LeadHistoryRespVO convertHistory(LeadCrawlHistoryDO bean) {
        if (bean == null) {
            return null;
        }
        LeadHistoryRespVO result = new LeadHistoryRespVO();
        result.setId(bean.getId());
        result.setRunId(bean.getRunId());
        result.setSearchProvider(bean.getSearchProvider());
        result.setSearchCategory(bean.getSearchCategory());
        result.setSearchCountry(bean.getSearchCountry());
        result.setSearchKeyword(bean.getSearchKeyword());
        result.setWebsite(bean.getWebsite());
        result.setDomain(bean.getDomain());
        result.setCrawlStatus(bean.getCrawlStatus());
        result.setPagesCrawled(bean.getPagesCrawled());
        result.setCrawledPages(readStringList(bean.getCrawledPagesJson()));
        result.setCrawlErrors(readStringList(bean.getCrawlErrorsJson()));
        result.setClassificationReason(bean.getClassificationReason());
        result.setTarget(bean.getTarget());
        result.setScore(bean.getScore());
        result.setCustomerId(bean.getCustomerId());
        result.setRaw(readMap(bean.getRawJson()));
        result.setCollectedAt(bean.getCollectedAt());
        return result;
    }

    public PageResult<LeadHistoryRespVO> convertHistoryPage(PageResult<LeadCrawlHistoryDO> page) {
        if (page == null) {
            return null;
        }
        return new PageResult<>(page.getList().stream().map(this::convertHistory).toList(), page.getTotal());
    }

    public String writeStringList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    public String gradeFromScore(Integer score) {
        int normalized = score == null ? 0 : score;
        if (normalized >= 80) {
            return "A";
        }
        if (normalized >= 60) {
            return "B";
        }
        if (normalized >= 40) {
            return "C";
        }
        return "D";
    }

    private List<String> parseGrades(String allowedGrades) {
        if (allowedGrades == null || allowedGrades.isBlank()) {
            return List.of("A", "B", "C", "D");
        }
        return Arrays.stream(allowedGrades.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

}
