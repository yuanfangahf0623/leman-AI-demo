package cn.iocoder.yudao.module.ai.service.rag.sensitive;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiSystemDictDataDO;
import cn.iocoder.yudao.module.ai.dal.mysql.AiSystemDictDataMapper;
import cn.iocoder.yudao.module.ai.framework.vector.KnowledgeHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 个人敏感数据访问策略。
 *
 * <p>规则从系统字典维护，字典值格式为 {@code 类型编码:规则值}，例如 {@code salary:工资}。</p>
 */
@Service
@Slf4j
public class PersonalSensitiveDataPolicy {

    public static final String DICT_TYPE_QUESTION_KEYWORD = "ai_personal_sensitive_question_keyword";
    public static final String DICT_TYPE_HIT_MARKER = "ai_personal_sensitive_hit_marker";
    public static final String DICT_TYPE_VALUE_PATTERN = "ai_personal_sensitive_value_pattern";

    private static final String ACCESS_DENIED_MESSAGE = "个人敏感数据仅本人或管理员可以查询";
    private static final Collection<String> DICT_TYPES = List.of(DICT_TYPE_QUESTION_KEYWORD, DICT_TYPE_HIT_MARKER,
            DICT_TYPE_VALUE_PATTERN);

    private static final List<PersonalSensitiveDataRule> DEFAULT_RULES = List.of(
            new PersonalSensitiveDataRule("salary",
                    List.of("工资", "薪资", "薪酬", "收入", "绩效工资", "绩效奖金", "绩效", "奖金", "全勤奖",
                            "补贴", "岗位工资", "综合薪资", "综合工资", "应发", "实发", "扣款", "多少钱",
                            "拿到多少钱"),
                    List.of("绩效目标确认单", "员工姓名", "薪资结构", "工资结构", "综合薪资", "综合工资",
                            "岗位工资", "调机工资", "Sheet:"),
                    List.of()),
            new PersonalSensitiveDataRule("id_card",
                    List.of("身份证", "身份证号", "证件号", "居民身份证", "公民身份号码"),
                    List.of("身份证", "身份证号", "证件号", "居民身份证", "公民身份号码"),
                    List.of(Pattern.compile("(?<!\\d)(\\d{15}|\\d{17}[0-9Xx])(?!\\d)")))
    );

    private final AiSystemDictDataMapper systemDictDataMapper;

    public PersonalSensitiveDataPolicy(AiSystemDictDataMapper systemDictDataMapper) {
        this.systemDictDataMapper = systemDictDataMapper;
    }

    public boolean isSensitiveQuestion(String question, String normalizedQuestion) {
        String source = (question == null ? "" : question) + "\n"
                + (normalizedQuestion == null ? "" : normalizedQuestion);
        return loadRules().stream().anyMatch(rule -> rule.matchesQuestion(source));
    }

    public boolean isPersonalSensitiveHit(KnowledgeHit hit) {
        if (hit == null) {
            return false;
        }
        String source = buildHitSearchText(hit);
        return loadRules().stream().anyMatch(rule -> rule.matchesHit(source));
    }

    public boolean hitContainsCurrentUser(KnowledgeHit hit, String currentUserNickname) {
        if (hit == null || currentUserNickname == null || currentUserNickname.isBlank()) {
            return false;
        }
        return containsIgnoreCase(buildHitSearchText(hit), currentUserNickname);
    }

    public String getAccessDeniedMessage() {
        return ACCESS_DENIED_MESSAGE;
    }

    private List<PersonalSensitiveDataRule> loadRules() {
        if (systemDictDataMapper == null) {
            return DEFAULT_RULES;
        }
        List<AiSystemDictDataDO> dictDataList = systemDictDataMapper.selectEnabledByDictTypes(DICT_TYPES);
        if (dictDataList == null || dictDataList.isEmpty()) {
            return DEFAULT_RULES;
        }
        Map<String, PersonalSensitiveDataRuleBuilder> builders = new LinkedHashMap<>();
        for (AiSystemDictDataDO dictData : dictDataList) {
            applyDictData(builders, dictData);
        }
        List<PersonalSensitiveDataRule> rules = builders.values().stream()
                .map(PersonalSensitiveDataRuleBuilder::build)
                .filter(PersonalSensitiveDataRule::isValid)
                .toList();
        return rules.isEmpty() ? DEFAULT_RULES : rules;
    }

    private void applyDictData(Map<String, PersonalSensitiveDataRuleBuilder> builders, AiSystemDictDataDO dictData) {
        if (dictData == null || dictData.getDictType() == null || dictData.getValue() == null) {
            return;
        }
        RuleValue ruleValue = parseRuleValue(dictData.getValue());
        if (ruleValue == null) {
            log.warn("Skip invalid personal sensitive dictionary value, dictType={}, dictDataId={}",
                    dictData.getDictType(), dictData.getId());
            return;
        }
        PersonalSensitiveDataRuleBuilder builder = builders.computeIfAbsent(ruleValue.typeCode(),
                PersonalSensitiveDataRuleBuilder::new);
        if (DICT_TYPE_QUESTION_KEYWORD.equals(dictData.getDictType())) {
            builder.questionKeywords.add(ruleValue.value());
        } else if (DICT_TYPE_HIT_MARKER.equals(dictData.getDictType())) {
            builder.hitMarkers.add(ruleValue.value());
        } else if (DICT_TYPE_VALUE_PATTERN.equals(dictData.getDictType())) {
            addPattern(dictData, ruleValue, builder);
        }
    }

    private void addPattern(AiSystemDictDataDO dictData, RuleValue ruleValue,
                            PersonalSensitiveDataRuleBuilder builder) {
        try {
            builder.valuePatterns.add(Pattern.compile(ruleValue.value()));
        } catch (PatternSyntaxException ex) {
            log.warn("Skip invalid personal sensitive regex, dictDataId={}, typeCode={}, error={}",
                    dictData.getId(), ruleValue.typeCode(), ex.getDescription());
        }
    }

    private RuleValue parseRuleValue(String rawValue) {
        int separatorIndex = rawValue.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= rawValue.length() - 1) {
            return null;
        }
        String typeCode = rawValue.substring(0, separatorIndex).trim();
        String value = rawValue.substring(separatorIndex + 1).trim();
        if (typeCode.isBlank() || value.isBlank()) {
            return null;
        }
        return new RuleValue(typeCode, value);
    }

    private String buildHitSearchText(KnowledgeHit hit) {
        return (hit.getDocumentTitle() == null ? "" : hit.getDocumentTitle()) + "\n"
                + (hit.getContent() == null ? "" : hit.getContent()) + "\n"
                + (hit.getMetadata() == null ? "" : hit.getMetadata().toString());
    }

    private static boolean containsAnyIgnoreCase(String source, List<String> keywords) {
        if (source == null || source.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && containsIgnoreCase(source, keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private record RuleValue(String typeCode, String value) {
    }

    private record PersonalSensitiveDataRule(String typeCode, List<String> questionKeywords, List<String> hitMarkers,
                                             List<Pattern> valuePatterns) {

        private boolean isValid() {
            return !questionKeywords.isEmpty() || !hitMarkers.isEmpty() || !valuePatterns.isEmpty();
        }

        private boolean matchesQuestion(String source) {
            return containsAnyIgnoreCase(source, questionKeywords);
        }

        private boolean matchesHit(String source) {
            boolean keywordMatched = containsAnyIgnoreCase(source, questionKeywords);
            boolean markerMatched = containsAnyIgnoreCase(source, hitMarkers);
            boolean patternMatched = valuePatterns.stream().anyMatch(pattern -> pattern.matcher(source).find());
            if (!hitMarkers.isEmpty() && !valuePatterns.isEmpty()) {
                return markerMatched || patternMatched;
            }
            if (!hitMarkers.isEmpty()) {
                return markerMatched && (questionKeywords.isEmpty() || keywordMatched);
            }
            if (!valuePatterns.isEmpty()) {
                return patternMatched;
            }
            return keywordMatched;
        }

    }

    private static final class PersonalSensitiveDataRuleBuilder {

        private final String typeCode;
        private final List<String> questionKeywords = new ArrayList<>();
        private final List<String> hitMarkers = new ArrayList<>();
        private final List<Pattern> valuePatterns = new ArrayList<>();

        private PersonalSensitiveDataRuleBuilder(String typeCode) {
            this.typeCode = typeCode;
        }

        private PersonalSensitiveDataRule build() {
            return new PersonalSensitiveDataRule(typeCode, List.copyOf(questionKeywords), List.copyOf(hitMarkers),
                    List.copyOf(valuePatterns));
        }

    }

}
