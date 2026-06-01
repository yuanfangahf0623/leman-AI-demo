package cn.iocoder.yudao.module.ai.framework.chatgpt;

import cn.iocoder.yudao.module.ai.service.chatgpt.ChatGptActionLogService;
import cn.iocoder.yudao.module.ai.service.chatgpt.dto.ChatGptActionLogCreateReqDTO;
import cn.iocoder.yudao.module.ai.framework.tenant.AiUserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatGptActionAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CALLER_TYPE = "GPT_ACTION";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_YUDAO_TENANT_ID = "tenant-id";

    private final AiChatGptActionsProperties properties;
    private final ChatGptActionLogService chatGptActionLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ChatGptActionAuditContextHolder.init(resolveActionName(request));
        ChatGptCallerContext caller = buildCallerContext(request);
        ChatGptCallerContextHolder.set(caller);
        AiUserContextHolder.setUserContext(caller.getTenantId(), 0L, 0L, caller.getCallerIdentity(), false);

        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            writeFailureAudit(caller, "CHATGPT_ACTION_DISABLED");
            response.sendError(HttpStatus.NOT_FOUND.value());
            clearContext();
            return false;
        }
        if (!StringUtils.hasText(properties.getToken())) {
            writeFailureAudit(caller, "CHATGPT_ACTION_TOKEN_NOT_CONFIGURED");
            response.sendError(HttpStatus.FORBIDDEN.value());
            clearContext();
            return false;
        }
        if (!isTokenValid(request.getHeader(HEADER_AUTHORIZATION), properties.getToken())) {
            writeFailureAudit(caller, "CHATGPT_ACTION_UNAUTHORIZED");
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            clearContext();
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            ChatGptCallerContext caller = ChatGptCallerContextHolder.get();
            ChatGptActionAuditContext auditContext = ChatGptActionAuditContextHolder.get();
            if (caller == null || auditContext == null) {
                return;
            }
            String errorCode = auditContext.getErrorCode();
            boolean success = !StringUtils.hasText(errorCode)
                    && response.getStatus() < HttpStatus.BAD_REQUEST.value() && ex == null;
            if (!success && !StringUtils.hasText(errorCode)) {
                errorCode = ex != null ? ex.getClass().getSimpleName() : String.valueOf(response.getStatus());
            }
            chatGptActionLogService.createLog(ChatGptActionLogCreateReqDTO.builder()
                    .tenantId(caller.getTenantId())
                    .actionName(auditContext.getActionName())
                    .requestId(caller.getRequestId())
                    .callerType(caller.getCallerType())
                    .callerIdentity(caller.getCallerIdentity())
                    .meetingId(auditContext.getMeetingId())
                    .knowledgeBaseId(auditContext.getKnowledgeBaseId())
                    .queryText(auditContext.getQueryText())
                    .success(success)
                    .errorCode(errorCode)
                    .build());
        } finally {
            clearContext();
        }
    }

    private void writeFailureAudit(ChatGptCallerContext caller, String errorCode) {
        ChatGptActionAuditContext auditContext = ChatGptActionAuditContextHolder.get();
        chatGptActionLogService.createLog(ChatGptActionLogCreateReqDTO.builder()
                .tenantId(caller.getTenantId())
                .actionName(auditContext == null ? null : auditContext.getActionName())
                .requestId(caller.getRequestId())
                .callerType(caller.getCallerType())
                .callerIdentity(caller.getCallerIdentity())
                .success(false)
                .errorCode(errorCode)
                .build());
    }

    private static boolean isTokenValid(String authorization, String expectedToken) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String actualToken = authorization.substring(BEARER_PREFIX.length());
        return MessageDigest.isEqual(sha256(actualToken), sha256(expectedToken));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ChatGptCallerContext buildCallerContext(HttpServletRequest request) {
        return ChatGptCallerContext.builder()
                .tenantId(resolveTenantId(request))
                .requestId(resolveRequestId(request))
                .callerType(CALLER_TYPE)
                .callerIdentity(CALLER_TYPE)
                .build();
    }

    private static Long resolveTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getHeader(HEADER_YUDAO_TENANT_ID);
        }
        if (!StringUtils.hasText(tenantId)) {
            return 0L;
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }

    private static String resolveActionName(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.endsWith("/meetings/search")) {
            return "searchMeetings";
        }
        if (uri.endsWith("/meetings/rag-search")) {
            return "searchMeetingKnowledge";
        }
        if (uri.endsWith("/minutes")) {
            return "getMeetingMinutes";
        }
        if (uri.endsWith("/transcript")) {
            return "getMeetingTranscript";
        }
        if (uri.matches(".*/openapi/chatgpt/meetings/\\d+$")) {
            return "getMeeting";
        }
        return request.getMethod() + " " + uri;
    }

    private static void clearContext() {
        AiUserContextHolder.clear();
        ChatGptActionAuditContextHolder.clear();
        ChatGptCallerContextHolder.clear();
    }

}
