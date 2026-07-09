package cn.iocoder.yudao.module.ai.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadAgentDashboardRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCrawlJobPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCrawlJobRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadFilterRuleRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadFilterRuleSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadHistoryRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketPageReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadMarketSaveReqVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadRunCreateReqVO;
import cn.iocoder.yudao.module.ai.convert.LeadAgentConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlHistoryDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCustomerDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadExportRuleDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadFilterRuleDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketCategoryDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketCountryDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadMarketKeywordDO;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadCrawlHistoryMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadCrawlJobMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadCustomerMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadExportRuleMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadFilterRuleMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadMarketCategoryMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadMarketCountryMapper;
import cn.iocoder.yudao.module.ai.dal.mysql.LeadMarketKeywordMapper;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadExcelExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_CRAWL_JOB_CREATE_FAILED;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_CRAWL_JOB_NOT_EXISTS;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_EXPORT_FAILED;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_EXPORT_GRADE_INVALID;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_MARKET_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_MARKET_COUNTRY_EMPTY;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_MARKET_KEYWORD_EMPTY;
import static cn.iocoder.yudao.module.ai.enums.LeadAgentErrorCodeConstants.LEAD_MARKET_NOT_EXISTS;

/**
 * Lead agent Service implementation.
 */
@Service
@RequiredArgsConstructor
public class LeadAgentServiceImpl implements LeadAgentService {

    private static final String FILTER_TYPE_BLOCKED_DOMAIN = "BLOCKED_DOMAIN_KEYWORD";
    private static final String FILTER_TYPE_BLOCKED_EXTENSION = "BLOCKED_FILE_EXTENSION";
    private static final Set<String> VALID_GRADES = Set.of("A", "B", "C", "D");
    private static final List<String> DEFAULT_BLOCKED_DOMAIN_KEYWORDS = List.of(
            "amazon", "ebay", "aliexpress", "alibaba", "temu", "ubuy", "apple", "sciencedirect",
            "snsinsider", "noon", "fruugo", "kiwicollection", "fitchratings", "facebook", "instagram",
            "linkedin", "youtube", "youtu", "tiktok", "pinterest", "wikipedia", "google", "bing",
            "reddit", "quora", "trustpilot", "tripadvisor", "yelp");
    private static final List<String> DEFAULT_BLOCKED_FILE_EXTENSIONS = List.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".mp4", ".mov", ".avi", ".zip", ".rar");

    private final LeadMarketCategoryMapper marketCategoryMapper;
    private final LeadMarketCountryMapper marketCountryMapper;
    private final LeadMarketKeywordMapper marketKeywordMapper;
    private final LeadFilterRuleMapper filterRuleMapper;
    private final LeadExportRuleMapper exportRuleMapper;
    private final LeadCustomerMapper customerMapper;
    private final LeadCrawlHistoryMapper crawlHistoryMapper;
    private final LeadCrawlJobMapper crawlJobMapper;
    private final LeadAgentRunner leadAgentRunner;
    private final LeadExcelExporter leadExcelExporter;

    @Override
    public PageResult<LeadMarketRespVO> getMarketPage(LeadMarketPageReqVO pageReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        PageResult<LeadMarketCategoryDO> pageResult = marketCategoryMapper.selectPage(pageReqVO, tenantId);
        return LeadAgentConvert.INSTANCE.convertMarketPage(pageResult, getCountriesByCategoryId(tenantId, pageResult.getList()),
                getKeywordsByCategoryId(tenantId, pageResult.getList()));
    }

    @Override
    public List<LeadMarketRespVO> getMarketList() {
        Long tenantId = AiTenantContextHolder.getTenantId();
        List<LeadMarketCategoryDO> categories = marketCategoryMapper.selectListByTenantId(tenantId);
        Map<Long, List<String>> countriesByCategoryId = getCountriesByCategoryId(tenantId, categories);
        Map<Long, List<String>> keywordsByCategoryId = getKeywordsByCategoryId(tenantId, categories);
        return categories.stream()
                .map(item -> LeadAgentConvert.INSTANCE.convertMarket(item, countriesByCategoryId.get(item.getId()),
                        keywordsByCategoryId.get(item.getId())))
                .toList();
    }

    @Override
    public LeadMarketRespVO getMarket(Long id) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        LeadMarketCategoryDO category = validateMarketExists(id, tenantId);
        return LeadAgentConvert.INSTANCE.convertMarket(category, getCountryValues(tenantId, id), getKeywordValues(tenantId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMarket(LeadMarketSaveReqVO createReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateCodeUnique(tenantId, null, createReqVO.getCategoryCode());
        List<String> countries = normalizeList(createReqVO.getCountries(), false);
        List<String> keywords = normalizeList(createReqVO.getKeywords(), false);
        validateMarketChildren(countries, keywords);

        LeadMarketCategoryDO category = new LeadMarketCategoryDO();
        fillMarket(category, tenantId, createReqVO);
        marketCategoryMapper.insert(category);
        replaceMarketChildren(tenantId, category.getId(), countries, keywords);
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMarket(LeadMarketSaveReqVO updateReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        LeadMarketCategoryDO oldCategory = validateMarketExists(updateReqVO.getId(), tenantId);
        validateCodeUnique(tenantId, updateReqVO.getId(), updateReqVO.getCategoryCode());
        List<String> countries = normalizeList(updateReqVO.getCountries(), false);
        List<String> keywords = normalizeList(updateReqVO.getKeywords(), false);
        validateMarketChildren(countries, keywords);

        LeadMarketCategoryDO updateObj = new LeadMarketCategoryDO();
        updateObj.setId(oldCategory.getId());
        fillMarket(updateObj, tenantId, updateReqVO);
        marketCategoryMapper.updateById(updateObj);
        replaceMarketChildren(tenantId, oldCategory.getId(), countries, keywords);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMarket(Long id) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        validateMarketExists(id, tenantId);
        marketCountryMapper.deleteByCategoryId(tenantId, id);
        marketKeywordMapper.deleteByCategoryId(tenantId, id);
        marketCategoryMapper.deleteById(id);
    }

    @Override
    public LeadFilterRuleRespVO getFilterRules() {
        Long tenantId = AiTenantContextHolder.getTenantId();
        LeadFilterRuleRespVO result = new LeadFilterRuleRespVO();
        List<String> blockedDomains = filterRuleMapper.selectListByType(tenantId, FILTER_TYPE_BLOCKED_DOMAIN)
                .stream().map(LeadFilterRuleDO::getRuleValue).toList();
        List<String> blockedExtensions = filterRuleMapper.selectListByType(tenantId, FILTER_TYPE_BLOCKED_EXTENSION)
                .stream().map(LeadFilterRuleDO::getRuleValue).toList();
        result.setBlockedDomainKeywords(blockedDomains.isEmpty() ? DEFAULT_BLOCKED_DOMAIN_KEYWORDS : blockedDomains);
        result.setBlockedFileExtensions(blockedExtensions.isEmpty() ? DEFAULT_BLOCKED_FILE_EXTENSIONS : blockedExtensions);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFilterRules(LeadFilterRuleSaveReqVO saveReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        List<String> blockedDomains = normalizeList(saveReqVO.getBlockedDomainKeywords(), true);
        List<String> blockedExtensions = normalizeExtensions(saveReqVO.getBlockedFileExtensions());
        filterRuleMapper.deleteByTenantId(tenantId);
        insertFilterRules(tenantId, FILTER_TYPE_BLOCKED_DOMAIN, blockedDomains);
        insertFilterRules(tenantId, FILTER_TYPE_BLOCKED_EXTENSION, blockedExtensions);
    }

    @Override
    public LeadExportRuleRespVO getExportRules() {
        return LeadAgentConvert.INSTANCE.convertExportRule(exportRuleMapper.selectByTenantId(AiTenantContextHolder.getTenantId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExportRules(LeadExportRuleSaveReqVO saveReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        List<String> allowedGrades = normalizeGrades(saveReqVO.getAllowedGrades());
        LeadExportRuleDO oldRule = exportRuleMapper.selectByTenantId(tenantId);
        LeadExportRuleDO saveObj = new LeadExportRuleDO();
        saveObj.setId(oldRule == null ? null : oldRule.getId());
        saveObj.setTenantId(tenantId);
        saveObj.setMinScore(saveReqVO.getMinScore());
        saveObj.setIncludeTargetOnly(Boolean.TRUE.equals(saveReqVO.getIncludeTargetOnly()));
        saveObj.setRequireEmail(Boolean.TRUE.equals(saveReqVO.getRequireEmail()));
        saveObj.setAllowedGrades(String.join(",", allowedGrades));
        saveObj.setIncludePossibleDuplicates(!Boolean.FALSE.equals(saveReqVO.getIncludePossibleDuplicates()));
        saveObj.setWriteRejectedFile(!Boolean.FALSE.equals(saveReqVO.getWriteRejectedFile()));
        if (saveObj.getId() == null) {
            exportRuleMapper.insert(saveObj);
        } else {
            exportRuleMapper.updateById(saveObj);
        }
    }

    @Override
    public PageResult<LeadCustomerRespVO> getCustomerPage(LeadCustomerPageReqVO pageReqVO) {
        return LeadAgentConvert.INSTANCE.convertCustomerPage(
                customerMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId()));
    }

    @Override
    public PageResult<LeadHistoryRespVO> getHistoryPage(LeadHistoryPageReqVO pageReqVO) {
        return LeadAgentConvert.INSTANCE.convertHistoryPage(
                crawlHistoryMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId()));
    }

    @Override
    public Long startRun(LeadRunCreateReqVO createReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        if (hasText(createReqVO.getCategoryCode())) {
            LeadMarketCategoryDO category = marketCategoryMapper.selectByTenantIdAndCode(tenantId,
                    createReqVO.getCategoryCode().trim());
            if (category == null) {
                throw new ServiceException(LEAD_MARKET_NOT_EXISTS, "Lead Agent market category does not exist");
            }
        }
        LeadCrawlJobDO job = new LeadCrawlJobDO();
        job.setTenantId(tenantId);
        job.setRunId(nextRunId());
        job.setCategoryCode(trimToNull(createReqVO.getCategoryCode()));
        job.setCountry(trimToNull(createReqVO.getCountry()));
        job.setMaxResults(createReqVO.getMaxResults() == null ? 50 : createReqVO.getMaxResults());
        job.setMaxPagesPerSite(createReqVO.getMaxPagesPerSite() == null ? 5 : createReqVO.getMaxPagesPerSite());
        job.setCrawlTimeoutSeconds(createReqVO.getCrawlTimeoutSeconds() == null ? 15 : createReqVO.getCrawlTimeoutSeconds());
        job.setSearchProvider(hasText(createReqVO.getSearchProvider()) ? createReqVO.getSearchProvider().trim() : "auto");
        job.setAnalysisProvider("rules");
        job.setSkipSocialVerification(Boolean.TRUE.equals(createReqVO.getSkipSocialVerification()));
        job.setEnableAiReview(Boolean.TRUE.equals(createReqVO.getEnableAiReview()));
        job.setStatus(LeadAgentExecutionModels.STATUS_PENDING);
        job.setTotalCandidates(0);
        job.setCrawledCount(0);
        job.setLeadCount(0);
        job.setExportedCount(0);
        job.setRejectedCount(0);
        try {
            crawlJobMapper.insert(job);
        } catch (RuntimeException ex) {
            throw new ServiceException(LEAD_CRAWL_JOB_CREATE_FAILED, "Lead Agent job create failed");
        }
        leadAgentRunner.submit(job);
        return job.getId();
    }

    @Override
    public PageResult<LeadCrawlJobRespVO> getJobPage(LeadCrawlJobPageReqVO pageReqVO) {
        return LeadAgentConvert.INSTANCE.convertJobPage(
                crawlJobMapper.selectPage(pageReqVO, AiTenantContextHolder.getTenantId()));
    }

    @Override
    public LeadCrawlJobRespVO getJob(Long id) {
        return LeadAgentConvert.INSTANCE.convertJob(validateJobExists(id, AiTenantContextHolder.getTenantId()));
    }

    @Override
    public byte[] exportCustomers(LeadCustomerPageReqVO pageReqVO) {
        Long tenantId = AiTenantContextHolder.getTenantId();
        List<LeadCustomerRespVO> customers = customerMapper.selectList(pageReqVO, tenantId).stream()
                .map(LeadAgentConvert.INSTANCE::convertCustomer)
                .toList();
        LeadExportRuleRespVO rules = getExportRules();
        try {
            return leadExcelExporter.exportCustomers(customers, rules);
        } catch (RuntimeException ex) {
            throw new ServiceException(LEAD_EXPORT_FAILED, "Lead Agent Excel export failed");
        }
    }

    @Override
    public LeadAgentDashboardRespVO getDashboard() {
        Long tenantId = AiTenantContextHolder.getTenantId();
        List<LeadCustomerDO> customers = customerMapper.selectListByTenantId(tenantId);
        List<LeadCrawlHistoryDO> historyItems = crawlHistoryMapper.selectListByTenantId(tenantId);
        List<LeadMarketCategoryDO> categories = marketCategoryMapper.selectListByTenantId(tenantId);
        List<Long> categoryIds = categories.stream().map(LeadMarketCategoryDO::getId).toList();
        List<LeadMarketCountryDO> countries = marketCountryMapper.selectListByCategoryIds(tenantId, categoryIds);
        List<LeadMarketKeywordDO> keywords = marketKeywordMapper.selectListByCategoryIds(tenantId, categoryIds);
        List<LeadFilterRuleDO> filterRules = filterRuleMapper.selectListByTenantId(tenantId);

        LeadAgentDashboardRespVO result = new LeadAgentDashboardRespVO();
        result.setGeneratedAt(LocalDateTime.now());
        result.setCustomerTotal((long) customers.size());
        result.setTargetCount(customers.stream().filter(item -> Boolean.TRUE.equals(item.getTarget())).count());
        result.setWithEmailCount(customers.stream().filter(item -> hasText(item.getBestEmail())).count());
        result.setHistoryTotal((long) historyItems.size());
        result.setMarketCategoryCount((long) categories.size());
        result.setCountryCount(countries.stream().map(LeadMarketCountryDO::getCountry).filter(this::hasText).distinct().count());
        result.setKeywordCount((long) keywords.size());
        result.setFilterBlockedDomainCount(filterRules.stream()
                .filter(item -> FILTER_TYPE_BLOCKED_DOMAIN.equals(item.getRuleType())).count());
        result.setFilterBlockedFileExtensionCount(filterRules.stream()
                .filter(item -> FILTER_TYPE_BLOCKED_EXTENSION.equals(item.getRuleType())).count());
        result.setLatestCollectedAt(customers.stream().map(LeadCustomerDO::getCollectedAt).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null));
        result.setGradeCounts(countBy(customers, item -> LeadAgentConvert.INSTANCE.gradeFromScore(item.getScore())));
        result.setCategoryCounts(countBy(customers, LeadCustomerDO::getMatchedCategory));
        result.setProviderCounts(countBy(historyItems, LeadCrawlHistoryDO::getSearchProvider));
        return result;
    }

    private LeadCrawlJobDO validateJobExists(Long id, Long tenantId) {
        if (id == null) {
            throw new ServiceException(LEAD_CRAWL_JOB_NOT_EXISTS, "Lead Agent job does not exist");
        }
        LeadCrawlJobDO job = crawlJobMapper.selectByIdAndTenantId(id, tenantId);
        if (job == null) {
            throw new ServiceException(LEAD_CRAWL_JOB_NOT_EXISTS, "Lead Agent job does not exist");
        }
        return job;
    }

    private String nextRunId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "lead-" + timestamp + "-" + suffix;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void fillMarket(LeadMarketCategoryDO category, Long tenantId, LeadMarketSaveReqVO saveReqVO) {
        category.setTenantId(tenantId);
        category.setCategoryCode(saveReqVO.getCategoryCode().trim());
        category.setCategoryName(hasText(saveReqVO.getCategoryName()) ? saveReqVO.getCategoryName().trim()
                : saveReqVO.getCategoryCode().trim());
        category.setWeight(saveReqVO.getWeight() == null ? 10 : saveReqVO.getWeight());
        category.setEnabled(!Boolean.FALSE.equals(saveReqVO.getEnabled()));
    }

    private void replaceMarketChildren(Long tenantId, Long categoryId, List<String> countries, List<String> keywords) {
        marketCountryMapper.deleteByCategoryId(tenantId, categoryId);
        marketKeywordMapper.deleteByCategoryId(tenantId, categoryId);
        for (int i = 0; i < countries.size(); i++) {
            marketCountryMapper.insert(LeadMarketCountryDO.builder()
                    .tenantId(tenantId)
                    .categoryId(categoryId)
                    .country(countries.get(i))
                    .sortOrder(i)
                    .build());
        }
        for (int i = 0; i < keywords.size(); i++) {
            marketKeywordMapper.insert(LeadMarketKeywordDO.builder()
                    .tenantId(tenantId)
                    .categoryId(categoryId)
                    .keyword(keywords.get(i))
                    .sortOrder(i)
                    .build());
        }
    }

    private Map<Long, List<String>> getCountriesByCategoryId(Long tenantId, List<LeadMarketCategoryDO> categories) {
        List<Long> categoryIds = categories.stream().map(LeadMarketCategoryDO::getId).toList();
        return marketCountryMapper.selectListByCategoryIds(tenantId, categoryIds).stream()
                .collect(Collectors.groupingBy(LeadMarketCountryDO::getCategoryId, LinkedHashMap::new,
                        Collectors.mapping(LeadMarketCountryDO::getCountry, Collectors.toList())));
    }

    private Map<Long, List<String>> getKeywordsByCategoryId(Long tenantId, List<LeadMarketCategoryDO> categories) {
        List<Long> categoryIds = categories.stream().map(LeadMarketCategoryDO::getId).toList();
        return marketKeywordMapper.selectListByCategoryIds(tenantId, categoryIds).stream()
                .collect(Collectors.groupingBy(LeadMarketKeywordDO::getCategoryId, LinkedHashMap::new,
                        Collectors.mapping(LeadMarketKeywordDO::getKeyword, Collectors.toList())));
    }

    private List<String> getCountryValues(Long tenantId, Long categoryId) {
        return marketCountryMapper.selectListByCategoryId(tenantId, categoryId).stream()
                .map(LeadMarketCountryDO::getCountry)
                .toList();
    }

    private List<String> getKeywordValues(Long tenantId, Long categoryId) {
        return marketKeywordMapper.selectListByCategoryId(tenantId, categoryId).stream()
                .map(LeadMarketKeywordDO::getKeyword)
                .toList();
    }

    private LeadMarketCategoryDO validateMarketExists(Long id, Long tenantId) {
        if (id == null) {
            throw new ServiceException(LEAD_MARKET_NOT_EXISTS, "线索采集市场分类不存在");
        }
        LeadMarketCategoryDO category = marketCategoryMapper.selectByIdAndTenantId(id, tenantId);
        if (category == null) {
            throw new ServiceException(LEAD_MARKET_NOT_EXISTS, "线索采集市场分类不存在");
        }
        return category;
    }

    private void validateCodeUnique(Long tenantId, Long id, String categoryCode) {
        LeadMarketCategoryDO category = marketCategoryMapper.selectByTenantIdAndCode(tenantId, categoryCode.trim());
        if (category == null || category.getId().equals(id)) {
            return;
        }
        throw new ServiceException(LEAD_MARKET_CODE_DUPLICATE, "市场分类 ID 在当前租户下已存在");
    }

    private void validateMarketChildren(List<String> countries, List<String> keywords) {
        if (countries.isEmpty()) {
            throw new ServiceException(LEAD_MARKET_COUNTRY_EMPTY, "至少需要一个国家");
        }
        if (keywords.isEmpty()) {
            throw new ServiceException(LEAD_MARKET_KEYWORD_EMPTY, "至少需要一个搜索关键词");
        }
    }

    private void insertFilterRules(Long tenantId, String ruleType, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            filterRuleMapper.insert(LeadFilterRuleDO.builder()
                    .tenantId(tenantId)
                    .ruleType(ruleType)
                    .ruleValue(values.get(i))
                    .sortOrder(i)
                    .enabled(true)
                    .build());
        }
    }

    private List<String> normalizeGrades(List<String> values) {
        List<String> result = normalizeList(values, true).stream()
                .map(item -> item.toUpperCase(Locale.ROOT))
                .filter(VALID_GRADES::contains)
                .toList();
        if (result.isEmpty()) {
            throw new ServiceException(LEAD_EXPORT_GRADE_INVALID, "允许导出的等级至少包含 A、B、C、D 中的一个");
        }
        return result;
    }

    private List<String> normalizeExtensions(List<String> values) {
        return normalizeList(values, true).stream()
                .map(item -> item.startsWith(".") ? item : "." + item)
                .toList();
    }

    private List<String> normalizeList(List<String> values, boolean lowerCase) {
        if (values == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            String item = value.trim();
            if (lowerCase) {
                item = item.toLowerCase(Locale.ROOT);
            }
            String dedupeKey = item.toLowerCase(Locale.ROOT);
            if (seen.add(dedupeKey)) {
                result.add(item);
            }
        }
        return result;
    }

    private <T> Map<String, Long> countBy(List<T> list, Function<T, String> classifier) {
        return list.stream()
                .map(classifier)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(String::trim, LinkedHashMap::new, Collectors.counting()));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
