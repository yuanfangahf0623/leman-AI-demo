package cn.iocoder.yudao.module.ai.framework.chatgpt;

public final class ChatGptActionAuditContextHolder {

    private static final ThreadLocal<ChatGptActionAuditContext> HOLDER = new ThreadLocal<>();

    private ChatGptActionAuditContextHolder() {
    }

    public static void init(String actionName) {
        ChatGptActionAuditContext context = new ChatGptActionAuditContext();
        context.setActionName(actionName);
        HOLDER.set(context);
    }

    public static ChatGptActionAuditContext get() {
        return HOLDER.get();
    }

    public static void setActionName(String actionName) {
        ChatGptActionAuditContext context = context();
        context.setActionName(actionName);
    }

    public static void setMeetingId(Long meetingId) {
        ChatGptActionAuditContext context = context();
        context.setMeetingId(meetingId);
    }

    public static void setKnowledgeBaseId(Long knowledgeBaseId) {
        ChatGptActionAuditContext context = context();
        context.setKnowledgeBaseId(knowledgeBaseId);
    }

    public static void setQueryText(String queryText) {
        ChatGptActionAuditContext context = context();
        context.setQueryText(queryText);
    }

    public static void setErrorCode(String errorCode) {
        ChatGptActionAuditContext context = context();
        context.setErrorCode(errorCode);
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static ChatGptActionAuditContext context() {
        ChatGptActionAuditContext context = HOLDER.get();
        if (context == null) {
            context = new ChatGptActionAuditContext();
            HOLDER.set(context);
        }
        return context;
    }

}
