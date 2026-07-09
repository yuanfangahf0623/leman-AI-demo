package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadCustomerRespVO;
import cn.iocoder.yudao.module.ai.controller.admin.lead.vo.LeadExportRuleRespVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel exporter for lead-agent customers.
 */
@Component
public class LeadExcelExporter {

    private static final List<String> EXPORT_COLUMNS = List.of(
            "序号", "客户等级", "分数", "公司名", "国家", "网址", "域名", "最佳邮箱", "邮箱类型",
            "全部邮箱", "电话", "主营产品", "匹配分类", "客户类型", "联系页面", "关于页面", "社交媒体",
            "社交活跃度", "社交校验分", "社交校验原因", "外部出现次数", "外部出现链接", "评分原因",
            "开发信标题", "开发信正文", "来源页面", "采集时间", "合规备注", "重复状态");
    private static final List<String> DETAIL_COLUMNS = List.of(
            "序号", "客户等级", "分数", "公司名", "国家", "网址", "域名", "是否目标客户", "匹配分类",
            "客户类型", "最佳邮箱", "邮箱类型", "全部邮箱", "电话", "主营产品", "联系页面", "关于页面",
            "来源页面", "采集时间", "运行ID", "分析方式", "AI错误", "爬取错误", "社交媒体", "社交活跃度",
            "社交校验分", "社交校验原因", "外部出现次数", "外部出现链接", "评分原因", "开发信标题",
            "开发信正文", "合规备注", "重复状态");
    private static final List<String> SCORE_COLUMNS = List.of(
            "序号", "客户等级", "总分", "公司名", "域名", "网址", "是否目标客户", "判断来源",
            "分值", "评分项", "评分来源");
    private static final List<String> RISK_COLUMNS = List.of(
            "序号", "客户等级", "销售分", "风险等级", "风险分", "建议", "公司名", "域名", "网址",
            "是否目标客户", "风险维度", "风险分值", "风险项");
    private static final Map<String, Integer> GRADE_ORDER = Map.of("A", 1, "B", 2, "C", 3, "D", 4);

    public byte[] exportCustomers(List<LeadCustomerRespVO> customers, LeadExportRuleRespVO rules) {
        List<LeadCustomerRespVO> included = splitIncluded(customers, rules);
        included = included.stream()
                .sorted(Comparator
                        .comparingInt((LeadCustomerRespVO item) -> GRADE_ORDER.getOrDefault(item.getGrade(), 9))
                        .thenComparing(Comparator.comparingInt((LeadCustomerRespVO item) ->
                                item.getScore() == null ? 0 : item.getScore()).reversed())
                        .thenComparing(item -> item.getDomain() == null ? "" : item.getDomain()))
                .toList();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeSheet(workbook, "leads", EXPORT_COLUMNS, leadRows(included));
            writeSheet(workbook, "customer_details", DETAIL_COLUMNS, detailRows(included));
            writeSheet(workbook, "scoring_table", SCORE_COLUMNS, scoreRows(included));
            writeSheet(workbook, "customer_risk", RISK_COLUMNS, riskRows(included));
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Lead Excel export failed", ex);
        }
    }

    public LeadAgentExecutionModels.ExportSplit splitLeadsByRules(List<LeadAgentExecutionModels.LeadRecord> leads,
                                                                   LeadExportRuleRespVO rules) {
        List<LeadAgentExecutionModels.LeadRecord> included = new ArrayList<>();
        List<LeadAgentExecutionModels.LeadRecord> rejected = new ArrayList<>();
        for (LeadAgentExecutionModels.LeadRecord lead : leads) {
            if (shouldExport(lead.getScore(), LeadAgentUtils.gradeFromScore(lead.getScore()), lead.getBestEmail(),
                    Boolean.TRUE.equals(lead.getTarget()), lead.getDuplicateStatus(), rules)) {
                included.add(lead);
            } else {
                rejected.add(lead);
            }
        }
        return LeadAgentExecutionModels.ExportSplit.builder().included(included).rejected(rejected).build();
    }

    private List<LeadCustomerRespVO> splitIncluded(List<LeadCustomerRespVO> customers, LeadExportRuleRespVO rules) {
        return customers.stream()
                .filter(item -> shouldExport(item.getScore() == null ? 0 : item.getScore(), item.getGrade(),
                        item.getBestEmail(), Boolean.TRUE.equals(item.getTarget()), item.getDuplicateStatus(), rules))
                .toList();
    }

    private boolean shouldExport(int score, String grade, String bestEmail, boolean target, String duplicateStatus,
                                 LeadExportRuleRespVO rules) {
        LeadExportRuleRespVO safeRules = rules == null ? new LeadExportRuleRespVO() : rules;
        List<String> allowedGrades = safeRules.getAllowedGrades() == null || safeRules.getAllowedGrades().isEmpty()
                ? List.of("A", "B", "C", "D") : safeRules.getAllowedGrades();
        int minScore = safeRules.getMinScore() == null ? 0 : safeRules.getMinScore();
        if (score < minScore) {
            return false;
        }
        if (!allowedGrades.contains(grade)) {
            return false;
        }
        if (Boolean.TRUE.equals(safeRules.getIncludeTargetOnly()) && !target) {
            return false;
        }
        if (Boolean.TRUE.equals(safeRules.getRequireEmail()) && !LeadAgentUtils.hasText(bestEmail)) {
            return false;
        }
        return !Boolean.FALSE.equals(safeRules.getIncludePossibleDuplicates())
                || !LeadAgentUtils.hasText(duplicateStatus)
                || "unique".equalsIgnoreCase(duplicateStatus);
    }

    private List<List<Object>> leadRows(List<LeadCustomerRespVO> customers) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            LeadCustomerRespVO item = customers.get(i);
            rows.add(List.of(
                    i + 1, value(item.getGrade()), number(item.getScore()), value(item.getCompanyName()),
                    value(item.getCountry()), value(item.getWebsite()), value(item.getDomain()), value(item.getBestEmail()),
                    value(item.getEmailType()), join(item.getEmails()), value(item.getPhone()), join(item.getMainProducts()),
                    value(item.getMatchedCategory()), value(item.getTargetCustomerType()), value(item.getContactPage()),
                    value(item.getAboutPage()), formatSocialProfiles(item), value(item.getSocialActivitySummary()),
                    number(item.getSocialVerificationScore()), value(item.getSocialVerificationReason()),
                    number(item.getExternalAppearanceCount()), join(item.getExternalAppearanceUrls()),
                    value(item.getScoreReason()), value(item.getDevelopmentEmailSubject()),
                    value(item.getDevelopmentEmailBody()), value(item.getSourceUrl()), value(item.getCollectedAt()),
                    value(item.getComplianceNote()), value(item.getDuplicateStatus())));
        }
        return rows;
    }

    private List<List<Object>> detailRows(List<LeadCustomerRespVO> customers) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            LeadCustomerRespVO item = customers.get(i);
            rows.add(List.of(
                    i + 1, value(item.getGrade()), number(item.getScore()), value(item.getCompanyName()),
                    value(item.getCountry()), value(item.getWebsite()), value(item.getDomain()),
                    bool(item.getTarget()), value(item.getMatchedCategory()), value(item.getTargetCustomerType()),
                    value(item.getBestEmail()), value(item.getEmailType()), join(item.getEmails()), value(item.getPhone()),
                    join(item.getMainProducts()), value(item.getContactPage()), value(item.getAboutPage()),
                    value(item.getSourceUrl()), value(item.getCollectedAt()), value(item.getRunId()),
                    value(item.getAnalysisProvider()), value(item.getAiReviewError()), join(item.getCrawlErrors()),
                    formatSocialProfiles(item), value(item.getSocialActivitySummary()),
                    number(item.getSocialVerificationScore()), value(item.getSocialVerificationReason()),
                    number(item.getExternalAppearanceCount()), join(item.getExternalAppearanceUrls()),
                    value(item.getScoreReason()), value(item.getDevelopmentEmailSubject()),
                    value(item.getDevelopmentEmailBody()), value(item.getComplianceNote()), value(item.getDuplicateStatus())));
        }
        return rows;
    }

    private List<List<Object>> scoreRows(List<LeadCustomerRespVO> customers) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            LeadCustomerRespVO item = customers.get(i);
            List<ScoreReasonRow> reasonRows = parseScoreReason(item.getScoreReason(), item.getAnalysisProvider());
            if (reasonRows.isEmpty()) {
                reasonRows = List.of(new ScoreReasonRow("说明", "暂无评分原因", "当前数据"));
            }
            for (ScoreReasonRow reason : reasonRows) {
                rows.add(List.of(i + 1, value(item.getGrade()), number(item.getScore()), value(item.getCompanyName()),
                        value(item.getDomain()), value(item.getWebsite()), bool(item.getTarget()),
                        scoreSourceLabel(item.getAnalysisProvider()), reason.points(), reason.detail(), reason.source()));
            }
        }
        return rows;
    }

    private List<List<Object>> riskRows(List<LeadCustomerRespVO> customers) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            LeadCustomerRespVO item = customers.get(i);
            RiskAssessment risk = assessRisk(item);
            for (RiskItem riskItem : risk.items()) {
                rows.add(List.of(i + 1, value(item.getGrade()), number(item.getScore()), risk.level(), risk.score(),
                        risk.action(), value(item.getCompanyName()), value(item.getDomain()), value(item.getWebsite()),
                        bool(item.getTarget()), riskItem.dimension(), riskItem.points(), riskItem.detail()));
            }
        }
        return rows;
    }

    private void writeSheet(Workbook workbook, String name, List<String> headers, List<List<Object>> rows) {
        Sheet sheet = workbook.createSheet(name);
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                Cell cell = row.createCell(columnIndex);
                Object value = values.get(columnIndex);
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else {
                    cell.setCellValue(value(value));
                }
            }
        }
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < headers.size(); i++) {
            sheet.setColumnWidth(i, Math.min(80, Math.max(12, headers.get(i).length() + 8)) * 256);
        }
    }

    private List<ScoreReasonRow> parseScoreReason(String reason, String fallbackSource) {
        String raw = reason == null ? "" : reason.trim();
        if (raw.isBlank()) {
            return List.of();
        }
        String source = scoreSourceLabel(fallbackSource);
        String body = raw;
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < 48 && raw.substring(0, colon).toLowerCase().matches(".*(openai|gpt|rules?|规则|评分).*")) {
            source = raw.substring(0, colon).trim();
            body = raw.substring(colon + 1).trim();
        }
        String normalized = body.replaceAll("[;；]\\s*(?=[+-]\\s*\\d+)", "；");
        List<String> parts = Arrays.stream(normalized.split("[;；]+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        String rowSource = source;
        return parts.stream().map(part -> scoreReasonRow(part, rowSource)).toList();
    }

    private ScoreReasonRow scoreReasonRow(String text, String source) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([+-]\\s*\\d+)").matcher(text);
        String points = "说明";
        String detail = text;
        if (matcher.find()) {
            points = matcher.group(1).replaceAll("\\s+", "");
            detail = text.substring(0, matcher.start()) + text.substring(matcher.end());
            detail = detail.replaceAll("^[:：,，.。\\s-]+", "").trim();
            if (detail.isBlank()) {
                detail = text;
            }
        }
        return new ScoreReasonRow(points, detail, source);
    }

    private RiskAssessment assessRisk(LeadCustomerRespVO customer) {
        List<RiskItem> items = new ArrayList<>();
        int score = 0;
        if (Boolean.FALSE.equals(customer.getTarget())) {
            score += 30;
            items.add(new RiskItem(30, "业务匹配", "公开页面判断该客户不是当前目标客户。"));
        } else if (number(customer.getScore()) < 40) {
            score += 24;
            items.add(new RiskItem(24, "业务匹配", "销售评分低于 40，建议复核业务匹配度。"));
        } else if (number(customer.getScore()) < 60) {
            score += 12;
            items.add(new RiskItem(12, "业务匹配", "销售评分低于 60，建议谨慎开发。"));
        }
        if (!LeadAgentUtils.hasText(customer.getCompanyName())) {
            score += 12;
            items.add(new RiskItem(12, "客户主体", "未识别到明确公司名称。"));
        }
        if (!LeadAgentUtils.hasText(customer.getBestEmail())) {
            score += 18;
            items.add(new RiskItem(18, "联系方式", "未提取到公开业务邮箱。"));
        } else if (customer.getBestEmail().contains("@gmail.") || customer.getBestEmail().contains("@hotmail.")
                || customer.getBestEmail().contains("@outlook.")) {
            score += 14;
            items.add(new RiskItem(14, "联系方式", "最佳邮箱疑似个人邮箱域名。"));
        }
        if (!LeadAgentUtils.hasText(customer.getContactPage())) {
            score += 8;
            items.add(new RiskItem(8, "联系方式", "未找到明确联系页面。"));
        }
        if (!LeadAgentUtils.hasText(customer.getWebsite()) || !customer.getWebsite().toLowerCase().startsWith("https://")) {
            score += 5;
            items.add(new RiskItem(5, "网站可信度", "网站不是 HTTPS 链接或缺少网站。"));
        }
        if (customer.getCrawlErrors() != null && !customer.getCrawlErrors().isEmpty()) {
            int points = Math.min(10, customer.getCrawlErrors().size() * 3);
            score += points;
            items.add(new RiskItem(points, "网站可信度", "抓取过程中出现错误，建议复查网站可达性。"));
        }
        if ((customer.getSocialProfiles() == null || customer.getSocialProfiles().isEmpty())
                && number(customer.getSocialVerificationScore()) == 0 && number(customer.getExternalAppearanceCount()) == 0) {
            score += 10;
            items.add(new RiskItem(10, "公开佐证", "未找到公开社交媒体或第三方出现佐证。"));
        }
        if (items.isEmpty()) {
            items.add(new RiskItem(0, "说明", "当前公开资料较完整，仍建议人工核验主体和联系方式。"));
        }
        int riskScore = Math.max(0, Math.min(100, score));
        String level = riskScore >= 56 ? "高风险" : riskScore >= 26 ? "中风险" : "低风险";
        String action = riskScore >= 56 ? "先复核" : riskScore >= 26 ? "谨慎开发" : "可开发";
        return new RiskAssessment(riskScore, level, action, items);
    }

    private String formatSocialProfiles(LeadCustomerRespVO customer) {
        if (customer.getSocialProfiles() == null || customer.getSocialProfiles().isEmpty()) {
            return "";
        }
        return customer.getSocialProfiles().stream()
                .map(profile -> value(profile.get("platform")) + ": " + value(profile.get("url"))
                        + " (" + value(profile.get("activityStatus")) + ")")
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private String scoreSourceLabel(String value) {
        if (!LeadAgentUtils.hasText(value)) {
            return "评分规则";
        }
        if (value.startsWith("openai:")) {
            return value.replace("openai:", "OpenAI ");
        }
        if ("rules".equals(value)) {
            return "规则引擎";
        }
        return value;
    }

    private String bool(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "是" : "否";
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join("; ", values);
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ScoreReasonRow(String points, String detail, String source) {
    }

    private record RiskAssessment(int score, String level, String action, List<RiskItem> items) {
    }

    private record RiskItem(int points, String dimension, String detail) {
    }

}
