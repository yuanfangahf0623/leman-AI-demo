package cn.iocoder.yudao.module.ai.service.chatgpt;

import cn.iocoder.yudao.module.ai.framework.chatgpt.AiChatGptActionsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatGptSensitiveMaskServiceImpl implements ChatGptSensitiveMaskService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<![0-9A-Za-z])\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)(?:\\d[ -]?){16,19}(?!\\d)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)\\b(?:sk|ak|api[_-]?key|access[_-]?token|secret[_-]?key)[_A-Za-z0-9-]{8,}\\b");
    private static final Pattern PASSWORD_FIELD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|token)\\s*[:=]\\s*[^,;\\s\\]}]+");

    private final AiChatGptActionsProperties properties;

    @Override
    public String mask(String text) {
        if (text == null || !Boolean.TRUE.equals(properties.getEnableSensitiveMask())) {
            return text;
        }
        String masked = MOBILE_PATTERN.matcher(text).replaceAll(match -> maskKeepEnds(match.group(), 3, 2));
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll(match -> maskKeepEnds(match.group(), 6, 2));
        masked = EMAIL_PATTERN.matcher(masked).replaceAll(match -> maskEmail(match.group()));
        masked = BANK_CARD_PATTERN.matcher(masked).replaceAll(match -> maskKeepEnds(match.group(), 4, 4));
        masked = API_KEY_PATTERN.matcher(masked).replaceAll("[MASKED_API_KEY]");
        masked = PASSWORD_FIELD_PATTERN.matcher(masked).replaceAll(match -> match.group(1) + "=[MASKED]");
        return masked;
    }

    private static String maskKeepEnds(String value, int prefixLength, int suffixLength) {
        String digits = value.replaceAll("[^0-9Xx]", "");
        if (digits.length() <= prefixLength + suffixLength) {
            return "[MASKED]";
        }
        return digits.substring(0, prefixLength) + "****" + digits.substring(digits.length() - suffixLength);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

}
