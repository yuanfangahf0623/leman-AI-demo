package cn.iocoder.yudao.module.ai.service.lead;

import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleRespVO;
import cn.iocoder.yudao.module.ai.convert.LeadAgentConvert;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlHistoryDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCrawlJobDO;
import cn.iocoder.yudao.module.ai.dal.dataobject.LeadCustomerDO;
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
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CandidateRecord;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ClassificationResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawlResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawledPage;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ExportSplit;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ProcessedLead;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.RunOptions;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SearchJob;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SocialProfile;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SocialVerificationResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentUtils;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadClassifier;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadDeduplicator;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadEmailGenerator;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadEmailGenerator.EmailDraft;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadExcelExporter;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadExtractor;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadLlmReviewer;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadLlmReviewer.ReviewOutcome;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadScorer;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadScorer.ScoreResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadSearchProvider;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadSearchProviderFactory;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadSocialVerifier;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadWebCrawler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.STATUS_FAILED;
import static cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.STATUS_RUNNING;
import static cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.STATUS_SUCCESS;

/**
 * Background runner for the Lead Agent collection pipeline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadAgentRunner {

    private static final String FILTER_TYPE_BLOCKED_DOMAIN = "BLOCKED_DOMAIN_KEYWORD";
    private static final String FILTER_TYPE_BLOCKED_EXTENSION = "BLOCKED_FILE_EXTENSION";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final LeadCrawlJobMapper crawlJobMapper;
    private final LeadCrawlHistoryMapper crawlHistoryMapper;
    private final LeadCustomerMapper customerMapper;
    private final LeadMarketCategoryMapper marketCategoryMapper;
    private final LeadMarketCountryMapper marketCountryMapper;
    private final LeadMarketKeywordMapper marketKeywordMapper;
    private final LeadFilterRuleMapper filterRuleMapper;
    private final LeadExportRuleMapper exportRuleMapper;
    private final LeadSearchProviderFactory searchProviderFactory;
    private final LeadLlmReviewer llmReviewer;
    private final LeadExcelExporter excelExporter;
    private final ObjectMapper objectMapper;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(new LeadAgentThreadFactory());

    public void submit(LeadCrawlJobDO job) {
        executorService.submit(() -> run(job.getId(), job.getTenantId()));
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    private void run(Long jobId, Long tenantId) {
        LeadCrawlJobDO job = crawlJobMapper.selectByIdAndTenantId(jobId, tenantId);
        if (job == null) {
            log.warn("Lead Agent job not found, jobId={}, tenantId={}", jobId, tenantId);
            return;
        }
        try {
            runInternal(job);
        } catch (Exception ex) {
            log.error("Lead Agent job failed, jobId={}, tenantId={}, runId={}", job.getId(), job.getTenantId(),
                    job.getRunId(), ex);
            LeadCrawlJobDO updateObj = new LeadCrawlJobDO();
            updateObj.setId(job.getId());
            updateObj.setStatus(STATUS_FAILED);
            updateObj.setErrorMessage(LeadAgentUtils.truncate(ex.getMessage(), 1000));
            updateObj.setFinishedAt(LocalDateTime.now());
            crawlJobMapper.updateByIdAndTenantId(updateObj, job.getTenantId());
        }
    }

    private void runInternal(LeadCrawlJobDO job) {
        markRunning(job);
        RunOptions options = buildRunOptions(job);
        LeadSearchProvider provider = searchProviderFactory.create(options.getSearchProvider());
        updateProvider(job, provider.providerName());
        LeadWebCrawler crawler = new LeadWebCrawler(options.getMaxPagesPerSite(), options.getCrawlTimeoutSeconds(),
                provider.getMockPages());
        LeadExtractor extractor = new LeadExtractor();
        LeadClassifier classifier = new LeadClassifier();
        LeadScorer scorer = new LeadScorer();
        LeadEmailGenerator emailGenerator = new LeadEmailGenerator();
        LeadDeduplicator deduplicator = new LeadDeduplicator();
        LeadSocialVerifier socialVerifier = new LeadSocialVerifier(provider);

        List<SearchJob> searchJobs = buildSearchJobs(options);
        List<CandidateRecord> candidates = collectCandidates(options, provider, searchJobs, loadBlockedRules(job.getTenantId()));
        updateProgress(job, update -> {
            update.setStatus(STATUS_RUNNING);
            update.setTotalCandidates(candidates.size());
        });

        List<ProcessedLead> processed = new ArrayList<>();
        AtomicInteger crawledCount = new AtomicInteger();
        for (CandidateRecord candidate : candidates) {
            ProcessedLead item = processCandidate(options, provider, crawler, extractor, classifier, scorer,
                    emailGenerator, socialVerifier, candidate);
            processed.add(item);
            updateProgress(job, update -> {
                update.setCrawledCount(crawledCount.incrementAndGet());
                update.setLeadCount((int) processed.stream().filter(value -> value.getLead() != null).count());
            });
        }

        List<LeadRecord> dedupedLeads = deduplicator.deduplicateLeads(processed.stream()
                .map(ProcessedLead::getLead)
                .filter(Objects::nonNull)
                .toList());
        LeadExportRuleRespVO exportRules = LeadAgentConvert.INSTANCE.convertExportRule(
                exportRuleMapper.selectByTenantId(job.getTenantId()));
        ExportSplit exportSplit = excelExporter.splitLeadsByRules(dedupedLeads, exportRules);
        Map<LeadRecord, Long> customerIds = insertCustomers(job, dedupedLeads);
        insertHistory(job, provider.providerName(), processed, customerIds);
        updateProgress(job, update -> {
            update.setStatus(STATUS_SUCCESS);
            update.setLeadCount(dedupedLeads.size());
            update.setExportedCount(exportSplit.getIncluded() == null ? 0 : exportSplit.getIncluded().size());
            update.setRejectedCount(exportSplit.getRejected() == null ? 0 : exportSplit.getRejected().size());
            update.setFinishedAt(LocalDateTime.now());
        });
        log.info("Lead Agent job completed, jobId={}, tenantId={}, runId={}, candidates={}, leads={}",
                job.getId(), job.getTenantId(), job.getRunId(), candidates.size(), dedupedLeads.size());
    }

    private void markRunning(LeadCrawlJobDO job) {
        updateProgress(job, update -> {
            update.setStatus(STATUS_RUNNING);
            update.setStartedAt(LocalDateTime.now());
            update.setTotalCandidates(0);
            update.setCrawledCount(0);
            update.setLeadCount(0);
            update.setExportedCount(0);
            update.setRejectedCount(0);
        });
    }

    private RunOptions buildRunOptions(LeadCrawlJobDO job) {
        return RunOptions.builder()
                .tenantId(job.getTenantId())
                .jobId(job.getId())
                .runId(job.getRunId())
                .categoryCode(blankToNull(job.getCategoryCode()))
                .country(blankToNull(job.getCountry()))
                .maxResults(defaultInt(job.getMaxResults(), 50))
                .maxPagesPerSite(defaultInt(job.getMaxPagesPerSite(), 5))
                .crawlTimeoutSeconds(defaultInt(job.getCrawlTimeoutSeconds(), 15))
                .searchProvider(defaultString(job.getSearchProvider(), "auto"))
                .enableAiReview(Boolean.TRUE.equals(job.getEnableAiReview()))
                .skipSocialVerification(Boolean.TRUE.equals(job.getSkipSocialVerification()))
                .build();
    }

    private List<SearchJob> buildSearchJobs(RunOptions options) {
        List<LeadMarketCategoryDO> categories = marketCategoryMapper.selectListByTenantId(options.getTenantId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> !LeadAgentUtils.hasText(options.getCategoryCode())
                        || options.getCategoryCode().equals(item.getCategoryCode()))
                .sorted(Comparator.comparingInt((LeadMarketCategoryDO item) -> defaultInt(item.getWeight(), 0)).reversed()
                        .thenComparing(LeadMarketCategoryDO::getCategoryCode))
                .toList();
        List<SearchJob> result = new ArrayList<>();
        for (LeadMarketCategoryDO category : categories) {
            List<String> countries = marketCountryMapper.selectListByCategoryId(options.getTenantId(), category.getId()).stream()
                    .map(LeadMarketCountryDO::getCountry)
                    .filter(LeadAgentUtils::hasText)
                    .filter(item -> !LeadAgentUtils.hasText(options.getCountry()) || options.getCountry().equals(item))
                    .toList();
            List<String> keywords = marketKeywordMapper.selectListByCategoryId(options.getTenantId(), category.getId()).stream()
                    .map(LeadMarketKeywordDO::getKeyword)
                    .filter(LeadAgentUtils::hasText)
                    .toList();
            for (String country : countries) {
                for (String keyword : keywords) {
                    result.add(SearchJob.builder()
                            .category(category.getCategoryCode())
                            .country(country)
                            .keyword(keyword)
                            .weight(defaultInt(category.getWeight(), 0))
                            .build());
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No enabled Lead Agent market keywords match the run options.");
        }
        return result.stream()
                .sorted(Comparator.comparingInt(SearchJob::getWeight).reversed()
                        .thenComparing(SearchJob::getCategory)
                        .thenComparing(SearchJob::getCountry)
                        .thenComparing(SearchJob::getKeyword))
                .limit(Math.max(10, options.getMaxResults()))
                .toList();
    }

    private List<CandidateRecord> collectCandidates(RunOptions options, LeadSearchProvider provider,
                                                    List<SearchJob> searchJobs, BlockedRules blockedRules) {
        Map<String, CandidateRecord> candidatesByDomain = new LinkedHashMap<>();
        int perJobLimit = Math.max(1, Math.min(10, options.getMaxResults() / Math.max(1, Math.min(searchJobs.size(), 10))));
        for (SearchJob searchJob : searchJobs) {
            if (candidatesByDomain.size() >= options.getMaxResults()) {
                break;
            }
            List<String> urls;
            try {
                urls = provider.search(searchJob.getKeyword(), searchJob.getCountry(), perJobLimit);
            } catch (RuntimeException ex) {
                log.warn("Lead search failed, provider={}, category={}, country={}, keyword={}, reason={}",
                        provider.providerName(), searchJob.getCategory(), searchJob.getCountry(), searchJob.getKeyword(),
                        ex.getClass().getSimpleName());
                continue;
            }
            for (String url : urls) {
                String normalizedUrl = LeadAgentUtils.normalizeUrl(url);
                String domain = LeadAgentUtils.extractDomain(normalizedUrl);
                if (!LeadAgentUtils.hasText(normalizedUrl) || !LeadAgentUtils.hasText(domain)
                        || blockedRules.shouldSkip(normalizedUrl, domain) || candidatesByDomain.containsKey(domain)) {
                    continue;
                }
                candidatesByDomain.put(domain, CandidateRecord.builder()
                        .url(normalizedUrl)
                        .category(searchJob.getCategory())
                        .country(searchJob.getCountry())
                        .keyword(searchJob.getKeyword())
                        .build());
                if (candidatesByDomain.size() >= options.getMaxResults()) {
                    break;
                }
            }
        }
        return new ArrayList<>(candidatesByDomain.values());
    }

    private ProcessedLead processCandidate(RunOptions options, LeadSearchProvider provider, LeadWebCrawler crawler,
                                           LeadExtractor extractor, LeadClassifier classifier, LeadScorer scorer,
                                           LeadEmailGenerator emailGenerator, LeadSocialVerifier socialVerifier,
                                           CandidateRecord candidate) {
        try {
            CrawlResult crawlResult = crawler.crawlSite(candidate.getUrl());
            var extraction = extractor.extractSite(crawlResult.getPages(), crawlResult.getDomain());
            ClassificationResult classification = classifier.classifySite(crawlResult, extraction, candidate.getCategory());
            LeadRecord lead = LeadRecord.builder()
                    .companyName(extraction.getCompanyName())
                    .country(candidate.getCountry())
                    .website(LeadAgentUtils.hasText(crawlResult.getFinalUrl()) ? crawlResult.getFinalUrl() : candidate.getUrl())
                    .domain(crawlResult.getDomain())
                    .emails(LeadAgentUtils.uniquePreserveOrder(extraction.getEmails()))
                    .bestEmail(extraction.getBestEmail())
                    .emailType(extraction.getEmailType())
                    .phone(extraction.getPhone())
                    .contactPage(extraction.getContactPage())
                    .aboutPage(extraction.getAboutPage())
                    .mainProducts(LeadAgentUtils.uniquePreserveOrder(extraction.getMainProducts()))
                    .target(classification.isTarget())
                    .matchedCategory(classification.getMatchedCategory())
                    .targetCustomerType(classification.getTargetCustomerType())
                    .sourceUrl(extraction.getSourceUrl())
                    .collectedAt(LeadAgentUtils.currentTimestamp())
                    .complianceNote(LeadAgentUtils.DEFAULT_COMPLIANCE_NOTE)
                    .duplicateStatus("unique")
                    .crawlErrors(crawlResult.getErrors())
                    .analysisProvider("rules")
                    .build();
            if (!options.isSkipSocialVerification()) {
                SocialVerificationResult socialResult = socialVerifier.verify(lead, crawlResult);
                socialVerifier.applySocialVerification(lead, socialResult);
            }
            ScoreResult scoreResult = scorer.scoreLead(lead, classification);
            lead.setScore(scoreResult.score());
            lead.setScoreReason(scoreResult.reason());
            EmailDraft emailDraft = emailGenerator.generateDevelopmentEmail(lead);
            lead.setDevelopmentEmailSubject(emailDraft.subject());
            lead.setDevelopmentEmailBody(emailDraft.body());
            if (options.isEnableAiReview()) {
                ReviewOutcome outcome = llmReviewer.review(lead, crawlResult, classification, candidate);
                if (!outcome.applied()) {
                    lead.setAiReviewError(outcome.error());
                }
            }
            lead.setTarget(classification.isTarget());
            return ProcessedLead.builder()
                    .candidate(candidate)
                    .crawlResult(crawlResult)
                    .classification(classification)
                    .lead(lead)
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Lead candidate processing failed, url={}, reason={}", candidate.getUrl(), ex.getClass().getSimpleName());
            CrawlResult failed = CrawlResult.builder()
                    .startUrl(candidate.getUrl())
                    .finalUrl(candidate.getUrl())
                    .domain(LeadAgentUtils.extractDomain(candidate.getUrl()))
                    .errors(List.of(LeadAgentUtils.truncate(ex.getMessage(), 500)))
                    .build();
            return ProcessedLead.builder()
                    .candidate(candidate)
                    .crawlResult(failed)
                    .classification(null)
                    .lead(null)
                    .build();
        }
    }

    private Map<LeadRecord, Long> insertCustomers(LeadCrawlJobDO job, List<LeadRecord> leads) {
        Map<LeadRecord, Long> result = new IdentityHashMap<>();
        for (LeadRecord lead : leads) {
            LeadCustomerDO customer = toCustomer(job, lead);
            customerMapper.insert(customer);
            result.put(lead, customer.getId());
        }
        return result;
    }

    private void insertHistory(LeadCrawlJobDO job, String providerName, List<ProcessedLead> processed,
                               Map<LeadRecord, Long> customerIds) {
        for (ProcessedLead item : processed) {
            LeadCrawlHistoryDO history = toHistory(job, providerName, item, customerIds.get(item.getLead()));
            crawlHistoryMapper.insert(history);
        }
    }

    private LeadCustomerDO toCustomer(LeadCrawlJobDO job, LeadRecord lead) {
        return LeadCustomerDO.builder()
                .tenantId(job.getTenantId())
                .runId(job.getRunId())
                .companyName(LeadAgentUtils.truncate(lead.getCompanyName(), 255))
                .country(LeadAgentUtils.truncate(lead.getCountry(), 128))
                .website(LeadAgentUtils.truncate(defaultString(lead.getWebsite(), ""), 1024))
                .domain(LeadAgentUtils.truncate(defaultString(lead.getDomain(), LeadAgentUtils.extractDomain(lead.getWebsite())), 255))
                .emailsJson(LeadAgentConvert.INSTANCE.writeStringList(lead.getEmails()))
                .bestEmail(LeadAgentUtils.truncate(lead.getBestEmail(), 255))
                .emailType(LeadAgentUtils.truncate(lead.getEmailType(), 64))
                .phone(LeadAgentUtils.truncate(lead.getPhone(), 128))
                .contactPage(LeadAgentUtils.truncate(lead.getContactPage(), 1024))
                .aboutPage(LeadAgentUtils.truncate(lead.getAboutPage(), 1024))
                .mainProductsJson(LeadAgentConvert.INSTANCE.writeStringList(lead.getMainProducts()))
                .target(lead.getTarget())
                .matchedCategory(LeadAgentUtils.truncate(lead.getMatchedCategory(), 64))
                .targetCustomerType(LeadAgentUtils.truncate(lead.getTargetCustomerType(), 128))
                .score(lead.getScore())
                .scoreReason(lead.getScoreReason())
                .sourceUrl(LeadAgentUtils.truncate(lead.getSourceUrl(), 1024))
                .collectedAt(parseDateTime(lead.getCollectedAt()))
                .complianceNote(LeadAgentUtils.truncate(lead.getComplianceNote(), 1024))
                .developmentEmailSubject(LeadAgentUtils.truncate(lead.getDevelopmentEmailSubject(), 512))
                .developmentEmailBody(lead.getDevelopmentEmailBody())
                .duplicateStatus(LeadAgentUtils.truncate(defaultString(lead.getDuplicateStatus(), "unique"), 64))
                .crawlErrorsJson(LeadAgentConvert.INSTANCE.writeStringList(lead.getCrawlErrors()))
                .analysisProvider(LeadAgentUtils.truncate(defaultString(lead.getAnalysisProvider(), "rules"), 128))
                .aiReviewError(LeadAgentUtils.truncate(lead.getAiReviewError(), 1024))
                .socialProfilesJson(LeadAgentConvert.INSTANCE.writeMapList(socialProfilesToMaps(lead.getSocialProfiles())))
                .socialActivitySummary(LeadAgentUtils.truncate(lead.getSocialActivitySummary(), 1024))
                .socialVerificationScore(lead.getSocialVerificationScore())
                .socialVerificationReason(LeadAgentUtils.truncate(lead.getSocialVerificationReason(), 1024))
                .externalAppearanceCount(lead.getExternalAppearanceCount())
                .externalAppearanceUrlsJson(LeadAgentConvert.INSTANCE.writeStringList(lead.getExternalAppearanceUrls()))
                .demoStatus("collected")
                .build();
    }

    private LeadCrawlHistoryDO toHistory(LeadCrawlJobDO job, String providerName, ProcessedLead item, Long customerId) {
        CandidateRecord candidate = item.getCandidate();
        CrawlResult crawlResult = item.getCrawlResult();
        ClassificationResult classification = item.getClassification();
        LeadRecord lead = item.getLead();
        return LeadCrawlHistoryDO.builder()
                .tenantId(job.getTenantId())
                .runId(job.getRunId())
                .searchProvider(providerName)
                .searchCategory(candidate.getCategory())
                .searchCountry(candidate.getCountry())
                .searchKeyword(candidate.getKeyword())
                .website(candidate.getUrl())
                .domain(crawlResult == null ? LeadAgentUtils.extractDomain(candidate.getUrl()) : crawlResult.getDomain())
                .crawlStatus(lead == null ? STATUS_FAILED : STATUS_SUCCESS)
                .pagesCrawled(crawlResult == null || crawlResult.getPages() == null ? 0 : crawlResult.getPages().size())
                .crawledPagesJson(LeadAgentConvert.INSTANCE.writeStringList(crawledPageUrls(crawlResult)))
                .crawlErrorsJson(LeadAgentConvert.INSTANCE.writeStringList(crawlResult == null ? List.of() : crawlResult.getErrors()))
                .classificationReason(classification == null ? null : classification.getReason())
                .target(lead == null ? null : lead.getTarget())
                .score(lead == null ? 0 : lead.getScore())
                .customerId(customerId)
                .rawJson(LeadAgentConvert.INSTANCE.writeMap(historyRaw(item)))
                .collectedAt(LocalDateTime.now())
                .build();
    }

    private List<String> crawledPageUrls(CrawlResult crawlResult) {
        if (crawlResult == null || crawlResult.getPages() == null) {
            return List.of();
        }
        return crawlResult.getPages().stream().map(CrawledPage::getUrl).filter(LeadAgentUtils::hasText).toList();
    }

    private Map<String, Object> historyRaw(ProcessedLead item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("candidate", item.getCandidate());
        raw.put("crawl_errors", item.getCrawlResult() == null ? List.of() : item.getCrawlResult().getErrors());
        raw.put("crawled_pages", crawledPageUrls(item.getCrawlResult()));
        raw.put("classification", item.getClassification());
        if (item.getLead() != null) {
            raw.put("company_name", item.getLead().getCompanyName());
            raw.put("best_email", item.getLead().getBestEmail());
            raw.put("score", item.getLead().getScore());
            raw.put("duplicate_status", item.getLead().getDuplicateStatus());
        }
        return raw;
    }

    private List<Map<String, Object>> socialProfilesToMaps(List<SocialProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        return objectMapper.convertValue(profiles, MAP_LIST_TYPE);
    }

    private BlockedRules loadBlockedRules(Long tenantId) {
        List<String> domains = filterRuleMapper.selectListByType(tenantId, FILTER_TYPE_BLOCKED_DOMAIN).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(LeadFilterRuleDO::getRuleValue)
                .filter(LeadAgentUtils::hasText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        List<String> extensions = filterRuleMapper.selectListByType(tenantId, FILTER_TYPE_BLOCKED_EXTENSION).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(LeadFilterRuleDO::getRuleValue)
                .filter(LeadAgentUtils::hasText)
                .map(item -> item.startsWith(".") ? item.toLowerCase(Locale.ROOT) : "." + item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        return new BlockedRules(new LinkedHashSet<>(domains), new LinkedHashSet<>(extensions));
    }

    private void updateProvider(LeadCrawlJobDO job, String providerName) {
        updateProgress(job, update -> update.setSearchProvider(providerName));
    }

    private void updateProgress(LeadCrawlJobDO job, ProgressUpdater updater) {
        LeadCrawlJobDO updateObj = new LeadCrawlJobDO();
        updateObj.setId(job.getId());
        updater.update(updateObj);
        crawlJobMapper.updateByIdAndTenantId(updateObj, job.getTenantId());
    }

    private LocalDateTime parseDateTime(String value) {
        if (!LeadAgentUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return LocalDateTime.now();
        }
    }

    private String blankToNull(String value) {
        return LeadAgentUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultString(String value, String defaultValue) {
        return LeadAgentUtils.hasText(value) ? value : defaultValue;
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private interface ProgressUpdater {
        void update(LeadCrawlJobDO updateObj);
    }

    private record BlockedRules(Set<String> domainKeywords, Set<String> fileExtensions) {

        private boolean shouldSkip(String url, String domain) {
            if (LeadAgentUtils.shouldSkipUrl(url)) {
                return true;
            }
            String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
            String lowerDomain = domain == null ? "" : domain.toLowerCase(Locale.ROOT);
            return domainKeywords.stream().anyMatch(lowerDomain::contains)
                    || fileExtensions.stream().anyMatch(lowerUrl::endsWith);
        }
    }

    private static final class LeadAgentThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lead-agent-runner-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

}
